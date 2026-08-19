package kafka.kafkaService.email.adapter.out.persistence;

import kafka.kafkaService.email.domain.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationInboxSdjRepository extends JpaRepository<NotificationJpaEntity, String> {

    List<NotificationJpaEntity> findByStatusIn(List<Notification.Status> statuses);


    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificationJpaEntity n " +
            "SET n.status = :status, " +
            "n.retryCount = :retryCount, " +
            "n.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE n.eventId = :eventId")
    void updateStatusDirectly(
            @Param("eventId") String eventId,
            @Param("status") Notification.Status status,
            @Param("retryCount") int retryCount
    );
}
