package kafka.kafkaService.email.application.port.in;

import kafka.kafkaService.global.dto.RecoveryCompletedEvent;

public interface NotificationUseCase {

    void processRecoveryNotification(RecoveryCompletedEvent event);
}
