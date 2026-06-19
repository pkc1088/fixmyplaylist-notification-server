package kafka.kafkaService.email.adapter.out.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import kafka.kafkaService.email.application.port.out.NotificationMetricsPort;
import org.springframework.stereotype.Component;

@Component
public class MicrometerMetricsAdapter implements NotificationMetricsPort {

    private final DistributionSummary batchSizeSummary;
    private final Counter initialSuccessCounter;
//    private final Counter retrySuccessCounter;
//    private final Counter deadLetterCounter;
//    private final Counter failCounter;


    public MicrometerMetricsAdapter(MeterRegistry registry) {
//        this.retrySuccessCounter = Counter.builder("notification.retry.result")
//                .tag("outcome", "success")
//                .register(registry);
//
//        this.failCounter = Counter.builder("notification.retry.result")
//                .tag("outcome", "fail")
//                .register(registry);
//
//        this.deadLetterCounter = Counter.builder("notification.retry.result")
//                .tag("outcome", "dead_letter")
//                .register(registry);

        this.initialSuccessCounter = Counter.builder("notification.init.result")
                .tag("outcome", "success")
                .register(registry);

        this.batchSizeSummary = DistributionSummary.builder("notification.init.batch.size")
                .description("처리한 후보군 크기")
                .register(registry);
    }

    @Override
    public void recordSuccess() {
        initialSuccessCounter.increment();
    }

    @Override
    public void recordRetryBatchSize(int size) {
        batchSizeSummary.record(size);
    }

//    @Override
//    public void recordRetrySuccess() {
//        successCounter.increment();
//    }
//
//    @Override
//    public void recordRetryFail() {
//        failCounter.increment();
//    }
//
//    @Override
//    public void recordDeadLetter() {
//        deadLetterCounter.increment();
//    }
}