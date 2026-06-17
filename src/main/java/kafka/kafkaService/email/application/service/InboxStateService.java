package kafka.kafkaService.email.application.service;

import kafka.kafkaService.email.application.port.out.NotificationInboxPort;
import kafka.kafkaService.email.application.port.out.dto.RecoveryCompletedEvent;
import kafka.kafkaService.email.domain.model.Notification;
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

        return notificationInboxPort.saveIdempotent(Notification.create(
                event.eventId(),
                event.userId(),
                event.userEmail(),
                payloadJson
        ));
    }


    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateInboxStatusToSuccess(String eventId) {
        notificationInboxPort.updateStatusDirectly(eventId, Notification.Status.SUCCESS);
    }


    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateInboxStatusToFailed(String eventId) {
        notificationInboxPort.updateStatusDirectly(eventId, Notification.Status.FAILED);
    }
}
