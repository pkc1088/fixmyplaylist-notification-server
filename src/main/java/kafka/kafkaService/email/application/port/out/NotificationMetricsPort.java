package kafka.kafkaService.email.application.port.out;

public interface NotificationMetricsPort {

    void recordBatchSize(int size);

    void recordSuccess();

    void recordFail();

    void recordRetryBatchSize(int size);

    void recordRetrySuccess();

    void recordRetryFail();

    void recordFinalizeDead();
}
