package kafka.kafkaService.email.adapter.out.persistence;

import kafka.kafkaService.email.application.port.out.NotificationInboxPort;
import kafka.kafkaService.email.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class NotificationInboxJpaAdapter implements NotificationInboxPort {

    private final NotificationInboxSdjRepository repository;
    private final NotificationMapper mapper;


    @Override
    public boolean saveIdempotent(Notification notification) {
        try {
            NotificationJpaEntity entity = mapper.toEntity(notification, true);
            repository.saveAndFlush(entity);
            return true;

        } catch (DataIntegrityViolationException e) {
            log.error("[Caught DataIntegrityViolationException] {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<Notification> findPendingOrFailedCandidates() {
        return repository.findByStatusIn(List.of(Notification.Status.PENDING, Notification.Status.FAILED))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void updateNotification(Notification notification) {
        repository.updateStatusDirectly(notification.getEventId(), notification.getStatus(), notification.getRetryCount());
    }
}