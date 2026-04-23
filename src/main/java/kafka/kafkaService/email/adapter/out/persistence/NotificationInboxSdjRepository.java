package kafka.kafkaService.email.adapter.out.persistence;

import kafka.kafkaService.email.domain.model.NotificationInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationInboxSdjRepository extends JpaRepository<NotificationInbox, String> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificationInbox n " +
            "SET n.status = :status, n.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE n.eventId = :eventId")
    void updateStatusDirectly(@Param("eventId") String eventId, @Param("status") NotificationInbox.Status status);


    @Query("SELECT n FROM NotificationInbox n WHERE n.status = 'PENDING' AND n.createdAt < :gracePeriod")
    List<NotificationInbox> findPendingCandidates(@Param("gracePeriod") LocalDateTime gracePeriod);


    @Query("SELECT n FROM NotificationInbox n WHERE n.status = 'FAILED' AND n.retryCount < :maxRetryCount")
    List<NotificationInbox> findFailedCandidates(@Param("retryCount") int maxRetryCount);


    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificationInbox n " +
            "SET n.retryCount = n.retryCount + 1, n.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE n.eventId = :eventId")
    void incrementRetryCountDirectly(@Param("eventId") String eventId);
}
