package kafka.kafkaService.email.application.port.out;

import kafka.kafkaService.email.application.port.out.dto.RecoveryCompletedEvent;

public interface EmailPort {

    void sendRecoveryEmail(RecoveryCompletedEvent event) throws Exception;
}
