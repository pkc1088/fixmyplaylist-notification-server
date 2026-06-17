package kafka.kafkaService.email.adapter.out.mail;

import kafka.kafkaService.email.application.port.out.EmailPort;
import kafka.kafkaService.email.application.port.out.dto.RecoveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestClient;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ResendEmailAdapter implements EmailPort {

    private final SpringTemplateEngine templateEngine;
    private final RestClient restClient;
    private final String resendApiKey;
    private final String fromEmail;

    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000)
    )
    public void sendRecoveryEmail(RecoveryCompletedEvent event) {
        Context context = new Context();
        context.setVariable("event", event);
        String htmlContent = templateEngine.process("recovery_report", context);

        Map<String, Object> payload = new HashMap<>();
        payload.put("from", fromEmail);
        payload.put("to", List.of(event.userEmail()));
        payload.put("subject", "A New Playlist Report");
        payload.put("html", htmlContent);

        log.info("[이메일 발송 시도] Event ID: {}", event.eventId());

        String responseBody = restClient.post()
                .uri("https://api.resend.com/emails")
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Idempotency-Key", event.eventId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

        log.info("[이메일 발송 성공] Resend 응답: {}", responseBody);
    }
}
