package kafka.kafkaService.email.adapter.out.kafka;

import kafka.kafkaService.email.application.port.out.DlqPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaDlqAdapter implements DlqPort {

    @Value("${app.kafka.topic.recovery-dlq}")
    private String topicName;

    private final KafkaTemplate<String, String> kafkaTemplate;


    @Override
    public void sendToDlq(String rawMessage) {
        try {
            kafkaTemplate.send(topicName, rawMessage)
                    .orTimeout(5, TimeUnit.SECONDS)
                    .join();

            log.info("Success to send DLQ message");

        } catch (Exception ex) {
            log.error("CRITICAL: Fail to send DLQ message. RawMessage: {}", rawMessage, ex);
            throw new RuntimeException("Fail to send DLQ message", ex);
        }
    }
}