package kafka.kafkaService.email.application.port.out;

import kafka.kafkaService.email.application.service.dto.RecoveryCompletedEvent;

public interface EmailPort {

    void sendRecoveryEmail(RecoveryCompletedEvent event) throws Exception;
}
