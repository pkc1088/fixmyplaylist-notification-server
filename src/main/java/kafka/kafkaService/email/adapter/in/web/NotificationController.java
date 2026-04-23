package kafka.kafkaService.email.adapter.in.web;

import kafka.kafkaService.email.application.port.in.NotificationUseCase;
import kafka.kafkaService.email.application.port.in.RetryNotificationUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/internal/notifications")
public class NotificationController {

    @Value("${notification.secret}")
    private String expectedSecret;
    private final NotificationUseCase notificationUseCase;
    private final RetryNotificationUseCase retryFailedNotifications;

    @Autowired
    public NotificationController(NotificationUseCase notificationUseCase, RetryNotificationUseCase retryFailedNotifications) {
        this.notificationUseCase = notificationUseCase;
        this.retryFailedNotifications = retryFailedNotifications;
    }


    @PostMapping("/recovery-completed")
    public ResponseEntity<String> handleRecoveryEvent(
            @RequestHeader(value = "X-Notification-Secret", required = false) String requestSecret) {

        log.info("recovery-completed endpoint triggered");

        if (requestSecret == null || !requestSecret.equals(expectedSecret)) {
            log.warn("Invalid Secret Key");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid Secret Key");
        }

        int count = notificationUseCase.processPendingNotifications();

        log.info("recovery-completed endpoint done");

        return ResponseEntity.ok("Successfully processed and sent " + count + " emails.");
    }


    @PostMapping("/retry-failed")
    public ResponseEntity<String> handleRetryTrigger(
            @RequestHeader(value = "X-Notification-Secret", required = false) String requestSecret) {

        log.info("retry-failed endpoint triggered by Cloud Scheduler");

        if (requestSecret == null || !requestSecret.equals(expectedSecret)) {
            log.warn("Invalid Secret Key for Retry Endpoint");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid Secret Key");
        }

        int successCount = retryFailedNotifications.retryFailedNotifications();

        log.info("retry-failed endpoint done. Processed {} emails.", successCount);

        return ResponseEntity.ok("Successfully retried and sent " + successCount + " emails.");
    }
}