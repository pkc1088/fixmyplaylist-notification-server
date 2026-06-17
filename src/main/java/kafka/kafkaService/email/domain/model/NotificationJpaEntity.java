package kafka.kafkaService.email.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "notification_inbox", indexes = {
        @Index(name = "idx_status_created_at", columnList = "status, created_at"),  // PENDING 조회용 복합 인덱스
        @Index(name = "idx_status_retry_count", columnList = "status, retry_count") // FAILED 조회용 복합 인덱스
})
public class NotificationJpaEntity implements Persistable<String> {

    @Id
    @Column(length = 100)
    private String eventId;

    @Column(nullable = false, length = 255)
    private String userId;

    @Column(nullable = false, length = 255)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Notification.Status status;

    @Column(columnDefinition = "JSON")
    private String payload;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = true)
    private LocalDateTime updatedAt;

    @Transient
    @Builder.Default
    private boolean isNew = false;

    @Override
    public String getId() {
        return this.eventId;
    }

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
