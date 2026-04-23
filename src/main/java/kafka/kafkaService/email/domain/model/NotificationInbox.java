package kafka.kafkaService.email.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification_inbox", indexes = {
        @Index(name = "idx_status_created_at", columnList = "status, created_at"),  // PENDING 조회용 복합 인덱스
        @Index(name = "idx_status_retry_count", columnList = "status, retry_count") // FAILED 조회용 복합 인덱스
})
public class NotificationInbox implements Persistable<String> {

    @Id
    @Column(length = 100)
    private String eventId;

    @Column(nullable = false, length = 255)
    private String userId;

    @Column(nullable = false, length = 255)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(columnDefinition = "JSON")
    private String payload;

    @Column(nullable = false)
    private int retryCount = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = true)
    private LocalDateTime updatedAt;

    @Transient
    private boolean isNew = false;

    public enum Status {
        PENDING, SUCCESS, FAILED, DEAD
    }

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

    @Builder
    public NotificationInbox(String eventId, String userId, String userEmail, String payload) {
        this.eventId = eventId;
        this.userId = userId;
        this.userEmail = userEmail;
        this.payload = payload;
        this.status = Status.PENDING;
        this.createdAt = LocalDateTime.now();
        this.isNew = true;
    }
}
