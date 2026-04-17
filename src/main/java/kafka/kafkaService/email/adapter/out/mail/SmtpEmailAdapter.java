package kafka.kafkaService.email.adapter.out.mail;

import jakarta.mail.internet.MimeMessage;
import kafka.kafkaService.email.application.port.out.EmailPort;
import kafka.kafkaService.global.dto.RecoveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpEmailAdapter implements EmailPort {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;


    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000)
    )
    @Override
    public void sendRecoveryEmail(RecoveryCompletedEvent event) throws Exception {

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        helper.setTo(event.userEmail());
        helper.setSubject("[FixMyPlaylist] A New Playlist Report");

        Context context = new Context();
        context.setVariable("event", event);

        String htmlContent = templateEngine.process("recovery_report", context);

        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
        log.info("[이메일 발송 완료] 대상: {}", event.userEmail());
    }
}