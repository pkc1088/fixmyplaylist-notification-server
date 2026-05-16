package kafka.kafkaService.email.infrastructure.config;

import kafka.kafkaService.email.adapter.out.mail.ResendEmailAdapter;
import kafka.kafkaService.email.application.port.out.EmailPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Slf4j
@Configuration
public class ResendConfig {

    @Bean
    public EmailPort resendEmailAdapter(
            SpringTemplateEngine templateEngine,
            @Value("${resend.api-key}") String resendApiKey,
            @Value("${resend.from-email}") String fromEmail
    ) {
        return new ResendEmailAdapter(templateEngine, resendApiKey, fromEmail);
    }
}
