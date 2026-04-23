package kafka.kafkaService.email.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.kafkaService.email.application.port.in.RetryNotificationUseCase;
import kafka.kafkaService.email.application.port.out.EmailPort;
import kafka.kafkaService.email.application.port.out.NotificationInboxPort;
import kafka.kafkaService.email.domain.model.NotificationInbox;
import kafka.kafkaService.global.dto.RecoveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryNotificationService implements RetryNotificationUseCase {

    private final NotificationInboxPort notificationInboxPort;
    private final InboxStateManager inboxStateManager;
    private final EmailPort resendEmailAdapter;
    private final ObjectMapper objectMapper;

    private static final int maxRetryCount = 5;


    @Override
    public int retryFailedNotifications() {

        LocalDateTime gracePeriod = LocalDateTime.now().minusMinutes(5);
        List<NotificationInbox> candidates = notificationInboxPort.findRetryCandidates(gracePeriod, maxRetryCount);

        if (candidates.isEmpty()) {
            log.info("[Nothing to retry]");
            return 0;
        }

        int successCount = 0;

        for (NotificationInbox inbox : candidates) {
            try {
                RecoveryCompletedEvent event = objectMapper.readValue(inbox.getPayload(), RecoveryCompletedEvent.class);

                resendEmailAdapter.sendRecoveryEmail(event); // Resend 멱등성 작동

                inboxStateManager.updateInboxStatusToSuccess(inbox.getEventId());
                successCount++;
                log.info("Retry Success: Event ID = {}", inbox.getEventId());

            } catch (JsonProcessingException e) {
                log.error("JsonProcessingException: {}", inbox.getEventId(), e);
                inboxStateManager.markAsDeadImmediately(inbox.getEventId());

            } catch (Exception e) {
                log.error("Retry Fail: {}", inbox.getEventId(), e);
                inboxStateManager.handleRetryFailure(inbox.getEventId(), inbox.getRetryCount(), maxRetryCount);
            }
        }

        return successCount;
    }
}
