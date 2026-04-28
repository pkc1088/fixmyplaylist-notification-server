package kafka.kafkaService.email.adapter.out.kafka;

import kafka.kafkaService.email.application.port.out.DlqPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class KafkaDlqAdapter implements DlqPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topicName;


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