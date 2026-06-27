package kafka.kafkaService.email.adapter.in.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.push.PushMeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WarmupController {

    private final MeterRegistry meterRegistry;


    @GetMapping("/api/internal/warmup")
    public ResponseEntity<String> warmup() {
        if (meterRegistry instanceof PushMeterRegistry pushRegistry) {
            try {
                log.info("[Metric Warmup] Starting initial publish...");
                long start = System.currentTimeMillis();

                Method publishMethod = PushMeterRegistry.class.getDeclaredMethod("publish");
                publishMethod.setAccessible(true);
                publishMethod.invoke(pushRegistry);

                log.info(
                        "[Metric Warmup] Initial Stackdriver push completed. elapsed={}ms",
                        System.currentTimeMillis() - start
                );

            } catch (Exception e) {
                log.warn("[Metric Warmup] Something went wrong.", e);
                return ResponseEntity.internalServerError().build();
            }
        }
        return ResponseEntity.ok("Warmup completed");
    }
}