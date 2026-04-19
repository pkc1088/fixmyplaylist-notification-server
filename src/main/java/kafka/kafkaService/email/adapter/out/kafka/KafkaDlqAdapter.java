package kafka.kafkaService.email.adapter.out.kafka;

import kafka.kafkaService.email.application.port.out.DlqPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaDlqAdapter implements DlqPort {

    @Value("${app.kafka.topic.recovery-dlq}")
    private String topicName;

    private final KafkaTemplate<String, String> kafkaTemplate;


    @Override
    public void sendToDlq(String rawMessage, Exception e) {

        kafkaTemplate.send(topicName, rawMessage);
    }
}