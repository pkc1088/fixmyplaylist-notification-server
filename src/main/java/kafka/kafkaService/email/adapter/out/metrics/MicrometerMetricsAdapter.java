package kafka.kafkaService.email.adapter.out.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import kafka.kafkaService.email.application.port.out.NotificationMetricsPort;
import org.springframework.stereotype.Component;

@Component
public class MicrometerMetricsAdapter implements NotificationMetricsPort {

    private final DistributionSummary initialBatchSizeSummary;
    private final Counter initialSuccessCounter;
    private final Counter initialFailCounter;

    private final DistributionSummary retryBatchSizeSummary;
    private final Counter retrySuccessCounter;
    private final Counter retryFailCounter;


    public MicrometerMetricsAdapter(MeterRegistry registry) {

        this.initialBatchSizeSummary = DistributionSummary.builder("notification.init.batch.size")
                .description("이메일 최초 처리한 작업 크기")
                .register(registry);

        this.initialSuccessCounter = Counter.builder("notification.init.result")
                .tag("outcome", "success")
                .description("이메일 최초 발송 성공 횟수")
                .register(registry);

        this.initialFailCounter = Counter.builder("notification.init.result")
                .tag("outcome", "fail")
                .description("이메일 최초 발송 실패 횟수")
                .register(registry);


        this.retryBatchSizeSummary = DistributionSummary.builder("notification.retry.batch.size")
                .description("이메일 재시도 처리한 작업 크기")
                .register(registry);

        this.retrySuccessCounter = Counter.builder("notification.retry.result")
                .tag("outcome", "success")
                .description("이메일 재시도 발송 성공 횟수")
                .register(registry);

        this.retryFailCounter = Counter.builder("notification.retry.result")
                .tag("outcome", "fail")
                .description("이메일 재시도 발송 실패 횟수")
                .register(registry);
    }

    @Override
    public void recordBatchSize(int size) {
        initialBatchSizeSummary.record(size);
    }

    @Override
    public void recordSuccess() {
        initialSuccessCounter.increment();
    }

    @Override
    public void recordFail() {
        initialFailCounter.increment();
    }

    @Override
    public void recordRetryBatchSize(int size) {
        retryBatchSizeSummary.record(size);
    }

    @Override
    public void recordRetrySuccess() {
        retrySuccessCounter.increment();
    }

    @Override
    public void recordRetryFail() {
        retryFailCounter.increment();
    }
}