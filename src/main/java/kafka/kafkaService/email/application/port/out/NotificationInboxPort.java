package kafka.kafkaService.email.application.port.out;

import kafka.kafkaService.email.domain.model.Notification;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationInboxPort {

    boolean saveIdempotent(Notification inbox);

    void updateRetriedNotification(Notification notification);

    void updateStatusDirectly(String eventId, Notification.Status status);

    List<Notification> findRetryCandidates(LocalDateTime gracePeriod, int maxRetryCount);
}
