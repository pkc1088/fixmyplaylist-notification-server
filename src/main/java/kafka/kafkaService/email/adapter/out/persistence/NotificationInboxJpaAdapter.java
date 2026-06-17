package kafka.kafkaService.email.adapter.out.persistence;

import kafka.kafkaService.email.application.port.out.NotificationInboxPort;
import kafka.kafkaService.email.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
            log.error("[Caught DataIntegrityViolationException]", e);
            return false;
        }
    }

    @Override
    public void updateRetriedNotification(Notification notification) {
        NotificationJpaEntity entity = mapper.toEntity(notification, false);
        repository.save(entity);
    }

    @Override
    public void updateStatusDirectly(String eventId, Notification.Status status) {
        repository.updateStatusDirectly(eventId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findRetryCandidates(LocalDateTime gracePeriod, int maxRetryCount) {
        List<NotificationJpaEntity> result = new ArrayList<>();
        result.addAll(repository.findPendingCandidates(gracePeriod));
        result.addAll(repository.findFailedCandidates(maxRetryCount));

        return result.stream()
                .map(mapper::toDomain)
                .toList();
    }
}