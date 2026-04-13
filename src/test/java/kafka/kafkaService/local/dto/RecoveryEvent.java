package kafka.kafkaService.local.dto;

public record RecoveryEvent(
        Long userId,
        String userEmail,
        int recoveredCount
) {}