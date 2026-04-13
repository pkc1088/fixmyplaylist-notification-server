package kafka.kafkaService.global.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RecoveryCompletedEvent(
        String userId,
        String userName,
        String userEmail,
        List<RecoveryDetail> recoveries,
        List<CleanupDetail> cleanups,
        LocalDateTime localDateTime
) {
    public record RecoveryDetail(
            String playlistId,
            String playlistTitle,
            // 기존 비정상 영상
            String targetVideoId,
            String targetVideoTitle,
            // 대체된 정상 영상
            String sourceVideoId,
            String sourceVideoTitle
    ) {}

    public record CleanupDetail(
            String playlistId,
            String playlistTitle,
            // 정리된 비정상 영상
            String targetVideoId,
            String targetVideoTitle
    ) {}
}