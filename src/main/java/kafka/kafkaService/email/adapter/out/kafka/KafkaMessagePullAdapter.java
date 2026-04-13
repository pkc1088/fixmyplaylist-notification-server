package kafka.kafkaService.email.adapter.out.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.kafkaService.email.application.port.out.MessagePullPort;
import kafka.kafkaService.global.dto.RecoveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import kafka.kafkaService.email.application.port.out.EventProcessor;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMessagePullAdapter implements MessagePullPort {

    private final ConsumerFactory<String, String> consumerFactory;
    private final ObjectMapper objectMapper;

    private static final String TOPIC_NAME = "recovery.completed.event";

    // 콜백 실행
    @Override
    public int pullAndProcess(EventProcessor processor) {
        int processedCount = 0;

        try (Consumer<String, String> consumer = consumerFactory.createConsumer()) {

            consumer.subscribe(Collections.singletonList(TOPIC_NAME));
            log.info("[Manual Polling Start...]");

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(3));

                if (records.isEmpty()) {
                    log.info("[No More Events. Polling Stop]");
                    break;
                }

                for (ConsumerRecord<String, String> record : records) {
                    try {
                        RecoveryCompletedEvent event = objectMapper.readValue(record.value(), RecoveryCompletedEvent.class);

                        processor.process(event);
                        processedCount++;

                    } catch (Exception e) {
                        log.error("Event Fail. Offset: {} - Move To DLQ.", record.offset(), e);

                        processor.onFail(record.value(), e);
                    }
                }
                consumer.commitSync();
            }

        } catch (Exception e) {
            log.error("Fatal Error During Manual Consumption", e);
        }

        return processedCount;
    }
}