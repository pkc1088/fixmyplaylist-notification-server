package kafka.kafkaService.email.adapter.in.web;

import kafka.kafkaService.email.application.port.in.NotificationUseCase;
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

    private final NotificationUseCase notificationUseCase;


    @PostMapping("/recovery-completed")
    public ResponseEntity<String> handleRecoveryEvent() {

        log.info("recovery-completed endpoint triggered");

        int count = notificationUseCase.processNotifications();

        log.info("recovery-completed endpoint done");

        return ResponseEntity.ok("Successfully processed and sent " + count + " emails.");
    }
}