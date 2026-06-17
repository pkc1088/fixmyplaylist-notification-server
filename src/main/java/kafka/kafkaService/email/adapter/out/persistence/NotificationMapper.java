package kafka.kafkaService.email.adapter.out.persistence;

import kafka.kafkaService.email.domain.model.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    // Domain -> JPA Entity
    public NotificationJpaEntity toEntity(Notification domain, boolean isNew) {
        if (domain == null) return null;

        return NotificationJpaEntity.builder()
                .eventId(domain.getEventId())
                .userId(domain.getUserId())
                .userEmail(domain.getUserEmail())
                .status(domain.getStatus())
                .payload(domain.getPayload())
                .retryCount(domain.getRetryCount())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .isNew(isNew)
                .build();
    }

    // JPA Entity -> Domain
    public Notification toDomain(NotificationJpaEntity entity) {
        if (entity == null) return null;

        return Notification.reconstitute(
                entity.getEventId(),
                entity.getUserId(),
                entity.getUserEmail(),
                entity.getPayload(),
                entity.getStatus(),
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}