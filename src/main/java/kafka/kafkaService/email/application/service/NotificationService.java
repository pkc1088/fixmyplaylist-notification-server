package kafka.kafkaService.email.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.kafkaService.email.application.port.in.NotificationUseCase;
import kafka.kafkaService.email.application.port.out.*;
import kafka.kafkaService.email.application.port.out.dto.RecoveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationUseCase {

    private final NotificationMetricsPort notificationMetricsPort;
    private final InboxStateService inboxStateService;
    private final MessagePullPort messagePullPort;
    private final EmailPort resendEmailAdapter;
    private final ObjectMapper objectMapper;
    private final DlqPort dlqPort;


    @Override
    public int processPendingNotifications() {
        // 콜백 전달
        return messagePullPort.pullAndProcess(new EventProcessor() {

            @Override
            public void process(RecoveryCompletedEvent event) throws Exception {

                String payloadJson = objectMapper.writeValueAsString(event);

                boolean isNewEvent = inboxStateService.saveToInboxIdempotent(event, payloadJson);
                if (!isNewEvent) {
                    log.warn("Event {} already processed. Skipping.", event.eventId());
                    return;
                }

                resendEmailAdapter.sendRecoveryEmail(event);

                inboxStateService.updateInboxStatusToSuccess(event.eventId());

                notificationMetricsPort.recordSuccess();
            }

            @Override
            public void onFail(String rawMessage) {
                // 실패 처리 로직(DLQ)
                dlqPort.sendToDlq(rawMessage);

                try {
                    RecoveryCompletedEvent event = objectMapper.readValue(rawMessage, RecoveryCompletedEvent.class);

                    inboxStateService.updateInboxStatusToFailed(event.eventId());
                    log.info("Updated Event {} Status To FAILED.", event.eventId());

                } catch (Exception parseException) {

                    log.warn("ParseException during DLQ processing: {}", parseException.getMessage());
                }
            }
        });
    }
}