package kafka.kafkaService.email.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.kafkaService.email.application.port.in.NotificationUseCase;
import kafka.kafkaService.email.application.port.out.*;
import kafka.kafkaService.email.application.port.out.dto.RecoveryCompletedEvent;
import kafka.kafkaService.email.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
    public int processNotifications() {
        // 콜백 전달
        int consumedCount = messagePullPort.pullAndProcess(new EventProcessor() {

            @Override
            public void process(RecoveryCompletedEvent event) throws Exception {
                String payloadJson = objectMapper.writeValueAsString(event);

                boolean isNewEvent = inboxStateService.saveToInboxIdempotent(event, payloadJson);
                if (!isNewEvent) {
                    log.warn("Event {} already processed. Skipping.", event.eventId());
                    return;
                }
            }

            @Override
            public void onFail(String rawMessage) {
                dlqPort.sendToDlq(rawMessage);
            }
        });

        log.info("Kafka 신규 수신 및 Inbox 적재: {}건", consumedCount);


        List<Notification> targetNotifications = inboxStateService.findPendingOrFailedCandidates();
        if (targetNotifications.isEmpty()) return 0;

        int initialCount = 0;
        int retryCount = 0;

        for (Notification notification : targetNotifications) {
            boolean isInitial = (notification.getRetryCount() == 0);

            try {
                RecoveryCompletedEvent event = objectMapper.readValue(notification.getPayload(), RecoveryCompletedEvent.class);

                resendEmailAdapter.sendRecoveryEmail(event);

                notification.markAsSuccess();
                recordSuccessMetrics(isInitial);

            } catch (Exception e) {

                notification.handleFailure();
                recordFailMetrics(isInitial, notification.getStatus());

                log.warn("Email 발송 실패: eventId={}, currentStatus={}", notification.getEventId(), notification.getStatus(), e);
            }

            inboxStateService.updateNotification(notification);

            if (isInitial) initialCount++;
            else retryCount++;
        }


        notificationMetricsPort.recordBatchSize(initialCount);
        if (retryCount > 0) notificationMetricsPort.recordRetryBatchSize(retryCount);

        return targetNotifications.size();
    }


    private void recordSuccessMetrics(boolean isInitial) {
        if (isInitial) notificationMetricsPort.recordSuccess();
        else notificationMetricsPort.recordRetrySuccess();
    }

    private void recordFailMetrics(boolean isInitial, Notification.Status currentStatus) {
        if (currentStatus == Notification.Status.DEAD) {
            notificationMetricsPort.recordFinalizeDead();
        } else {
            if (isInitial) notificationMetricsPort.recordFail();
            else notificationMetricsPort.recordRetryFail();
        }
    }
}