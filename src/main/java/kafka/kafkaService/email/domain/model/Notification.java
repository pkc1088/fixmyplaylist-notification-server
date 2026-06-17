package kafka.kafkaService.email.domain.model;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Notification {

    private final String eventId;

    private final String userId;

    private final String userEmail;

    private Status status;

    private final String payload;

    private int retryCount;

    private final LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum Status {
        PENDING, SUCCESS, FAILED, DEAD
    }

    private static final int MAX_EVENT_ID_LENGTH = 100;
    private static final int MAX_USER_ID_LENGTH = 255;
    private static final int MAX_EMAIL_LENGTH = 255;


    @Builder(access = AccessLevel.PRIVATE)
    private Notification(
            String eventId,
            String userId,
            String userEmail,
            String payload,
            Status status,
            Integer retryCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.eventId = eventId;
        this.userId = userId;
        this.userEmail = userEmail;
        this.payload = payload;
        this.status = (status != null) ? status : Status.PENDING;
        this.retryCount = (retryCount != null) ? retryCount : 0;
        this.createdAt = (createdAt != null) ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt;
        validate();
    }

    private void validate() {
        if (eventId == null || eventId.isBlank() || eventId.length() > MAX_EVENT_ID_LENGTH) {
            throw new IllegalArgumentException("Notification eventId cannot be null or empty.");
        }
        if (userId == null || userId.isBlank() || userId.length() > MAX_USER_ID_LENGTH) {
            throw new IllegalArgumentException("Notification eventId cannot be null or empty.");
        }
        if (userEmail == null || userEmail.isBlank() || userEmail.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("Notification userEmail cannot be null or empty.");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Notification payload cannot be null or empty.");
        }
    }

    public static Notification create(
            String eventId,
            String userId,
            String userEmail,
            String payload
    ) {
        return Notification.builder()
                .eventId(eventId)
                .userId(userId)
                .userEmail(userEmail)
                .payload(payload)
                .build();
    }

    public static Notification reconstitute(
            String eventId,
            String userId,
            String userEmail,
            String payload,
            Status status,
            int retryCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return Notification.builder()
                .eventId(eventId)
                .userId(userId)
                .userEmail(userEmail)
                .payload(payload)
                .status(status)
                .retryCount(retryCount)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public void markAsSuccess() {
        this.status = Status.SUCCESS;
        this.updatedAt = LocalDateTime.now();
        validate();
    }

    public void handleFailure(int maxRetryCount) {
        if (this.retryCount >= maxRetryCount - 1) {
            this.status = Status.DEAD;
        } else {
            this.status = Status.FAILED;
            this.retryCount++;
        }
        this.updatedAt = LocalDateTime.now();
        validate();
    }

    public void markAsDead() {
        this.status = Status.DEAD;
        this.updatedAt = LocalDateTime.now();
        validate();
    }
}
