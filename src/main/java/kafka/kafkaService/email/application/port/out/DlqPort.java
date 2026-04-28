package kafka.kafkaService.email.application.port.out;

public interface DlqPort {

    void sendToDlq(String rawMessage);
}
