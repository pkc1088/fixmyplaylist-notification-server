package kafka.kafkaService.email.adapter.out.persistence;

import kafka.kafkaService.email.application.port.out.NotificationInboxPort;
import kafka.kafkaService.email.domain.model.NotificationInbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationInboxJpaAdapter implements NotificationInboxPort {

    private final NotificationInboxSdjRepository repository;


    @Override
    public NotificationInbox save(NotificationInbox inbox) {
        return repository.save(inbox);
    }

    @Override
    public void updateStatusDirectly(String eventId, NotificationInbox.Status status) {
        repository.updateStatusDirectly(eventId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationInbox> findRetryCandidates(LocalDateTime gracePeriod, int maxRetryCount) {
        List<NotificationInbox> result = new ArrayList<>();
        result.addAll(repository.findPendingCandidates(gracePeriod));
        result.addAll(repository.findFailedCandidates(maxRetryCount));

        return result;
    }

    @Override
    public void incrementRetryCountDirectly(String eventId) {
        repository.incrementRetryCountDirectly(eventId);
    }
}