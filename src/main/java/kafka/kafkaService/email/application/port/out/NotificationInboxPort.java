package kafka.kafkaService.email.application.port.out;

import kafka.kafkaService.email.domain.model.NotificationInbox;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationInboxPort {

    NotificationInbox save(NotificationInbox inbox);

    void updateStatusDirectly(String eventId, NotificationInbox.Status status);

    List<NotificationInbox> findRetryCandidates(LocalDateTime gracePeriod, int maxRetryCount);

    void incrementRetryCountDirectly(String eventId);
}
