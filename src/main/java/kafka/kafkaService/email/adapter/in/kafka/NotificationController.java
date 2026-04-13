package kafka.kafkaService.email.adapter.in.kafka;

import kafka.kafkaService.email.application.port.in.KafkaConsumerUseCase;
import kafka.kafkaService.email.application.port.in.NotificationUseCase;
import kafka.kafkaService.global.dto.RecoveryCompletedEvent;
import lombok.RequiredArgsConstructor;
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

    private final KafkaConsumerUseCase kafkaConsumerUseCase;

    @Autowired
    public NotificationController(KafkaConsumerUseCase kafkaConsumerUseCase) {
        this.kafkaConsumerUseCase = kafkaConsumerUseCase;
    }

    @PostMapping("/recovery-completed")
    public ResponseEntity<String> handleRecoveryEvent(
            @RequestHeader(value = "X-Notification-Secret", required = false) String requestSecret) {

        log.info("recovery-completed endpoint triggered");

        if (requestSecret == null || !requestSecret.equals(expectedSecret)) {
            log.warn("Invalid Secret Key");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid Secret Key");
        }

        int count = kafkaConsumerUseCase.pollAndProcessMessages();

        log.info("recovery-completed endpoint done");

        return ResponseEntity.ok("Successfully processed and sent " + count + " emails.");
    }
}