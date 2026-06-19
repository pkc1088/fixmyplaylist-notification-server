package kafka.kafkaService.email.adapter.in.web;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/internal/test")
public class MetricTestController {

    private final Counter manualTriggerCounter;


    public MetricTestController(MeterRegistry meterRegistry) {
        this.manualTriggerCounter = Counter.builder("test.manual.trigger")
                .description("수동으로 트리거된 테스트 지표")
                .tag("server", "notification")
                .register(meterRegistry);
    }

    @GetMapping("/metric")
    public String triggerMetric() {
        manualTriggerCounter.increment();
        return "Metric incremented! Current count pushed to GCP in background.";
    }
}
