package kafka.kafkaService.email.application.port.out;

import kafka.kafkaService.email.domain.model.Notification;

import java.util.List;

public interface NotificationInboxPort {

    boolean saveIdempotent(Notification inbox);

    List<Notification> findPendingOrFailedCandidates();

    void updateNotification(Notification notification);
}
