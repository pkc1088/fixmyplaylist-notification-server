package kafka.kafkaService.email.adapter.out;

import jakarta.mail.internet.MimeMessage;
import kafka.kafkaService.email.application.port.out.EmailPort;
import kafka.kafkaService.global.dto.RecoveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailAdapter implements EmailPort {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;


    @Override
    public void sendRecoveryEmail(RecoveryCompletedEvent event) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(event.userEmail());
            helper.setSubject("[FixMyPlaylist] 복구 리포트 도착");

            Context context = new Context();
            context.setVariable("event", event);

            String htmlContent = templateEngine.process("recovery_report", context);

            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("[이메일 발송 완료] 대상: {}", event.userEmail());

        } catch (Exception e) {
            log.error("[이메일 발송 실패]: {}", event.userEmail(), e);
        }
    }
}