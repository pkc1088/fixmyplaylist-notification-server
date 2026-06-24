package kafka.kafkaService.email.adapter.in.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.push.PushMeterRegistry;
import kafka.kafkaService.email.application.port.in.NotificationUseCase;
import kafka.kafkaService.email.application.port.in.RetryNotificationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/notifications")
public class NotificationController {

    private final RetryNotificationUseCase retryNotificationUseCase;
    private final NotificationUseCase notificationUseCase;
    private final MeterRegistry meterRegistry;


    @PostMapping("/recovery-completed")
    public ResponseEntity<String> handleRecoveryEvent() {

        log.info("recovery-completed endpoint triggered");

        int count;

        try {
            count = notificationUseCase.processPendingNotifications();
        } finally {
            forcePushMetrics();
        }

        log.info("recovery-completed endpoint done");

        return ResponseEntity.ok("Successfully processed and sent " + count + " emails.");
    }


    @PostMapping("/retry-failed")
    public ResponseEntity<String> handleRetryTrigger() {

        log.info("retry-failed endpoint triggered by Cloud Scheduler");

        int successCount;

        try {
            successCount = retryNotificationUseCase.retryFailedNotifications();
        } finally {
            forcePushMetrics();
        }

        log.info("retry-failed endpoint done. Processed {} emails.", successCount);

        return ResponseEntity.ok("Successfully retried and sent " + successCount + " emails.");
    }

    private void forcePushMetrics() {
        if (meterRegistry instanceof PushMeterRegistry pushRegistry) {
            try {
                Method publishMethod = PushMeterRegistry.class.getDeclaredMethod("publish");
                publishMethod.setAccessible(true);
                publishMethod.invoke(pushRegistry);
                log.info("[Task finished: Force push to StackDriver completed]");

            } catch (Exception e) {
                log.warn("[StackDriver metric forced push failed]", e);
            }
        }
    }
}