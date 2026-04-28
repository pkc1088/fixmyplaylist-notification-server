package kafka.kafkaService.email.application.service.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RecoveryCompletedEvent(
        String eventId,
        String userId,
        String userName,
        String userEmail,
        List<RecoveryDetail> recoveries,
        List<CleanupDetail> cleanups,
        LocalDateTime localDateTime
) {
    public record RecoveryDetail(
            String playlistId,
            // 기존 비정상 영상
            String targetVideoId,
            String targetVideoTitle,
            // 대체된 정상 영상
            String sourceVideoId,
            String sourceVideoTitle
    ) {}

    public record CleanupDetail(
            String playlistId,
            // 정리된 비정상 영상
            String targetVideoId,
            String targetVideoTitle
    ) {}
}