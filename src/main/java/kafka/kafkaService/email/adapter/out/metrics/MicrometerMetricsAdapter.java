package kafka.kafkaService.email.adapter.out.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import kafka.kafkaService.email.application.port.out.NotificationMetricsPort;
import org.springframework.stereotype.Component;

@Component
public class MicrometerMetricsAdapter implements NotificationMetricsPort {

    private final DistributionSummary initialEmailBatchSizeSummary;
    private final Counter initialEmailSuccessCounter;
    private final Counter initialEmailFailCounter;

    private final DistributionSummary retryEmailBatchSizeSummary;
    private final Counter retryEmailSuccessCounter;
    private final Counter retryEmailFailCounter;
    private final Counter emailDeadCounter;


    public MicrometerMetricsAdapter(MeterRegistry registry) {

        this.initialEmailBatchSizeSummary = DistributionSummary.builder("notification.init.batch.size")
                .description("이메일 최초 처리한 작업 크기")
                .register(registry);

        this.initialEmailSuccessCounter = Counter.builder("notification.init.result")
                .tag("outcome", "success")
                .description("이메일 최초 발송 성공 횟수")
                .register(registry);

        this.initialEmailFailCounter = Counter.builder("notification.init.result")
                .tag("outcome", "fail")
                .description("이메일 최초 발송 실패 횟수")
                .register(registry);


        this.retryEmailBatchSizeSummary = DistributionSummary.builder("notification.retry.batch.size")
                .description("이메일 재시도 처리한 작업 크기")
                .register(registry);

        this.retryEmailSuccessCounter = Counter.builder("notification.retry.result")
                .tag("outcome", "success")
                .description("이메일 재시도 발송 성공 횟수")
                .register(registry);

        this.retryEmailFailCounter = Counter.builder("notification.retry.result")
                .tag("outcome", "fail")
                .description("이메일 재시도 발송 실패 횟수")
                .register(registry);

        this.emailDeadCounter = Counter.builder("notification.retry.result")
                .tag("outcome", "dead")
                .description("이메일 최종 DEAD 횟수")
                .register(registry);
    }

    @Override
    public void recordBatchSize(int size) {
        initialEmailBatchSizeSummary.record(size);
    }

    @Override
    public void recordSuccess() {
        initialEmailSuccessCounter.increment();
    }

    @Override
    public void recordFail() {
        initialEmailFailCounter.increment();
    }

    @Override
    public void recordRetryBatchSize(int size) {
        retryEmailBatchSizeSummary.record(size);
    }

    @Override
    public void recordRetrySuccess() {
        retryEmailSuccessCounter.increment();
    }

    @Override
    public void recordRetryFail() {
        retryEmailFailCounter.increment();
    }

    @Override
    public void recordFinalizeDead() {
        emailDeadCounter.increment();
    }
}