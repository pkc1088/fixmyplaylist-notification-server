package kafka.kafkaService.email.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.kafkaService.email.adapter.out.kafka.KafkaDlqAdapter;
import kafka.kafkaService.email.adapter.out.kafka.KafkaMessagePullAdapter;
import kafka.kafkaService.email.application.port.out.DlqPort;
import kafka.kafkaService.email.application.port.out.MessagePullPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@Configuration
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ConfluentKafkaConfig {

    @Bean
    public MessagePullPort recoveryCompletedTopic(
            @Value("${app.kafka.topic.recovery-completed}") String topicName,
            ConsumerFactory<String, String> consumerFactory,
            ObjectMapper objectMapper
    ) {
        return new KafkaMessagePullAdapter(consumerFactory, objectMapper, topicName);
    }

    @Bean
    public DlqPort recoveryDlqTopic(
            @Value("${app.kafka.topic.recovery-dlq}") String topicName,
            KafkaTemplate<String, String> kafkaTemplate)
    {
        return new KafkaDlqAdapter(kafkaTemplate, topicName);
    }
}
