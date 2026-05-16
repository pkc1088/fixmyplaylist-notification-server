package kafka.kafkaService.email.application.service;

import kafka.kafkaService.email.application.port.out.NotificationInboxPort;
import kafka.kafkaService.email.application.port.out.dto.RecoveryCompletedEvent;
import kafka.kafkaService.email.domain.model.NotificationInbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InboxStateService {

    private final NotificationInboxPort notificationInboxPort;


    // Don't Start TX Here
    public boolean saveToInboxIdempotent(RecoveryCompletedEvent event, String payloadJson) {
        if (event.eventId() == null || event.eventId().isBlank()) {
            return false;
        }

        NotificationInbox inbox = NotificationInbox.create(
                event.eventId(),
                event.userId(),
                event.userEmail(),
                payloadJson
        );

        return notificationInboxPort.save(inbox);
    }


    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000)
    )
    @Transactional
    public void updateInboxStatusToSuccess(String eventId) {
        notificationInboxPort.updateStatusDirectly(eventId, NotificationInbox.Status.SUCCESS);
    }


    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateInboxStatusToFailed(String eventId) {
        notificationInboxPort.updateStatusDirectly(eventId, NotificationInbox.Status.FAILED);
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleRetryFailure(String eventId, int currentRetryCount, int maxRetryCount) {

        if (currentRetryCount >= maxRetryCount - 1) {
            // 더 이상 가망 없는 애들은 DEAD
            notificationInboxPort.updateStatusDirectly(eventId, NotificationInbox.Status.DEAD);
            log.error("Mark Event {} after {} times of retry", eventId, maxRetryCount);

        } else {
            notificationInboxPort.updateStatusDirectly(eventId, NotificationInbox.Status.FAILED);
            notificationInboxPort.incrementRetryCountDirectly(eventId);
            log.warn("Retry Failed on Event {}. Increase retry count.", eventId);
        }
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsDeadImmediately(String eventId) {
        notificationInboxPort.updateStatusDirectly(eventId, NotificationInbox.Status.DEAD);
    }
}
