package kafka.kafkaService.email.application.port.out;

import kafka.kafkaService.global.dto.RecoveryCompletedEvent;

public interface EmailPort {

    void sendRecoveryEmail(RecoveryCompletedEvent event);
}
