package kafka.kafkaService.email.adapter.in.web;

import kafka.kafkaService.email.application.port.in.NotificationUseCase;
import kafka.kafkaService.email.application.port.in.RetryNotificationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/notifications")
public class NotificationController {

    private final RetryNotificationUseCase retryFailedNotifications;
    private final NotificationUseCase notificationUseCase;


    @PostMapping("/recovery-completed")
    public ResponseEntity<String> handleRecoveryEvent() {

        log.info("recovery-completed endpoint triggered");

        int count = notificationUseCase.processPendingNotifications();

        log.info("recovery-completed endpoint done");

        return ResponseEntity.ok("Successfully processed and sent " + count + " emails.");
    }


    @PostMapping("/retry-failed")
    public ResponseEntity<String> handleRetryTrigger() {

        log.info("retry-failed endpoint triggered by Cloud Scheduler");

        int successCount = retryFailedNotifications.retryFailedNotifications();

        log.info("retry-failed endpoint done. Processed {} emails.", successCount);

        return ResponseEntity.ok("Successfully retried and sent " + successCount + " emails.");
    }
}