package kafka.kafkaService.email.application.port.in;

public interface KafkaConsumerUseCase {

    int pollAndProcessMessages();
}
