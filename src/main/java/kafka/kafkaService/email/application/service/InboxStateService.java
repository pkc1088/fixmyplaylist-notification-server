package kafka.kafkaService.email.application.service;

import kafka.kafkaService.email.application.port.out.NotificationInboxPort;
import kafka.kafkaService.email.application.port.out.dto.RecoveryCompletedEvent;
import kafka.kafkaService.email.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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


    @Transactional(readOnly = true)
    public List<Notification> findPendingOrFailedCandidates() {
        return notificationInboxPort.findPendingOrFailedCandidates();
    }


    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000)
    )
    @Transactional
    public void updateNotification(Notification notification) {
        notificationInboxPort.updateNotification(notification);
    }
}
