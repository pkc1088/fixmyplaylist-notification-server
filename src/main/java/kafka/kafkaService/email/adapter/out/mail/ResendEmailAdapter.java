package kafka.kafkaService.email.adapter.out.mail;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import kafka.kafkaService.email.application.port.out.EmailPort;
import kafka.kafkaService.email.application.port.out.dto.RecoveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ResendEmailAdapter implements EmailPort {

    private final SpringTemplateEngine templateEngine;
    private final String resendApiKey;
    private final String fromEmail;


    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000)
    )
    public void sendRecoveryEmail(RecoveryCompletedEvent event) throws Exception {

        Context context = new Context();
        context.setVariable("event", event);

        String htmlContent = templateEngine.process("recovery_report", context);

        Resend resend = new Resend(resendApiKey);

        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("Idempotency-Key", event.eventId());

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(event.userEmail())
                .subject("A New Playlist Report")
                .html(htmlContent)
                .headers(customHeaders)
                .build();

        CreateEmailResponse response = resend.emails().send(params);
        log.info("[이메일 발송 성공] Resend ID: {}", response.getId());
    }
}
