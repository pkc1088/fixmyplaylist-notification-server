package kafka.kafkaService.email.adapter.out.persistence;

import kafka.kafkaService.email.domain.model.Notification;
import kafka.kafkaService.email.domain.model.NotificationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationInboxSdjRepository extends JpaRepository<NotificationJpaEntity, String> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificationJpaEntity n " +
            "SET n.status = :status, n.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE n.eventId = :eventId")
    void updateStatusDirectly(@Param("eventId") String eventId, @Param("status") Notification.Status status);


    @Query("SELECT n FROM NotificationJpaEntity n WHERE n.status = 'PENDING' AND n.createdAt < :gracePeriod")
    List<NotificationJpaEntity> findPendingCandidates(@Param("gracePeriod") LocalDateTime gracePeriod);


    @Query("SELECT n FROM NotificationJpaEntity n WHERE n.status = 'FAILED' AND n.retryCount < :maxRetryCount")
    List<NotificationJpaEntity> findFailedCandidates(@Param("maxRetryCount") int maxRetryCount);
}
