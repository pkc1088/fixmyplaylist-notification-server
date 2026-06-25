package kafka.kafkaService.email.adapter.in.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.push.PushMeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

@RestController
@RequiredArgsConstructor
public class WarmupController {

    private final MeterRegistry meterRegistry;


    @GetMapping("/api/internal/warmup")
    public ResponseEntity<String> warmup() {
        if (meterRegistry instanceof PushMeterRegistry pushRegistry) {
            try {
                Method publishMethod = PushMeterRegistry.class.getDeclaredMethod("publish");
                publishMethod.setAccessible(true);
                publishMethod.invoke(pushRegistry);
                System.out.println("[Metric Warmup] Initial Stackdriver push completed.");

            } catch (Exception e) {
                return ResponseEntity.internalServerError().build();
            }
        }
        return ResponseEntity.ok("Warmup completed");
    }
}