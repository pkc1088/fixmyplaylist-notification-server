package kafka.kafkaService.email.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.kafkaService.email.application.port.in.RetryNotificationUseCase;
import kafka.kafkaService.email.application.port.out.EmailPort;
import kafka.kafkaService.email.application.port.out.NotificationInboxPort;
import kafka.kafkaService.email.application.port.out.NotificationMetricsPort;
import kafka.kafkaService.email.domain.model.Notification;
import kafka.kafkaService.email.application.port.out.dto.RecoveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryNotificationService implements RetryNotificationUseCase {

    private final NotificationMetricsPort notificationMetricsPort;
    private final NotificationInboxPort notificationInboxPort;
    private final EmailPort resendEmailAdapter;
    private final ObjectMapper objectMapper;

    private static final int maxRetryCount = 3;
    private static final int minusMinutes = 3;


    @Override
    public int retryFailedNotifications() {

        List<Notification> candidates = notificationInboxPort
                .findRetryCandidates(LocalDateTime.now().minusMinutes(minusMinutes), maxRetryCount);

        if (candidates.isEmpty()) {
            log.info("[Nothing to retry]");
            return 0;
        }

        int successCount = 0;

        for (Notification notification : candidates) {
            try {
                RecoveryCompletedEvent event = objectMapper.readValue(notification.getPayload(), RecoveryCompletedEvent.class);
                resendEmailAdapter.sendRecoveryEmail(event); // Resend 멱등성 작동

                log.info("Retry Success: {}", notification.getEventId());
                successCount++;
                notification.markAsSuccess();
                notificationMetricsPort.recordRetrySuccess();

            } catch (JsonProcessingException e) {
                log.error("JsonProcessingException: {}", notification.getEventId(), e);
                notification.markAsDead();
                notificationMetricsPort.recordFinalizeDead();

            } catch (Exception e) {
                log.warn("Resend API Error: {}", notification.getEventId(), e);
                handleException(notification);
            }

            notificationInboxPort.updateRetriedNotification(notification);
        }

        notificationMetricsPort.recordRetryBatchSize(candidates.size());

        return successCount;
    }

    private void handleException(Notification notification) {
        notification.handleFailure(maxRetryCount);

        if (notification.getStatus() == Notification.Status.DEAD) {
            notificationMetricsPort.recordFinalizeDead();
            log.error("Retry Dead: {}", notification.getEventId());

        } else {
            notificationMetricsPort.recordRetryFail();
            log.warn("Retry Fail: {}", notification.getEventId());
        }
    }
}
