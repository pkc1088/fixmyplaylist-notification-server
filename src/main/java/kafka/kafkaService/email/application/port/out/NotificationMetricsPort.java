package kafka.kafkaService.email.application.port.out;

public interface NotificationMetricsPort {

    void recordSuccess();

    void recordRetryBatchSize(int size);
}
