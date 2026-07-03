package kafka.kafkaService.global.probe;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.push.PushMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

@Slf4j
@RestController
public class WarmupController {

    private final MeterRegistry meterRegistry;
    private final String warmupSecret;


    public WarmupController(
            MeterRegistry meterRegistry,
            @Value("${management.endpoints.warmup.secret}") String warmupSecret
    ) {
        this.meterRegistry = meterRegistry;
        this.warmupSecret = warmupSecret;
    }

    @GetMapping("/api/internal/warmup/{token}")
    public ResponseEntity<String> warmup(@PathVariable(value = "token", required = false) String token) {

        log.info("[Noti Server Startup Probe] expected: [{}], received: [{}]", this.warmupSecret, token);

        if (token == null || !token.equals(this.warmupSecret)) {
            log.warn("[Noti Server Startup Probe] Unauthorized warmup attempt.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (meterRegistry instanceof PushMeterRegistry pushRegistry) {
            try {
                log.info("[Noti Server Metric Warmup] Starting initial publish...");
                long start = System.currentTimeMillis();

                Method publishMethod = PushMeterRegistry.class.getDeclaredMethod("publish");
                publishMethod.setAccessible(true);
                publishMethod.invoke(pushRegistry);

                log.info(
                        "[Noti Server Metric Warmup] Initial Stackdriver push completed. elapsed={}ms",
                        System.currentTimeMillis() - start
                );

            } catch (Exception e) {
                log.warn("[Noti Server Metric Warmup] Something went wrong.", e);
                return ResponseEntity.internalServerError().build();
            }
        }
        return ResponseEntity.ok("Noti Server Warmup completed");
    }
}