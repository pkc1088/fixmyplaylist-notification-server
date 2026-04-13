package kafka.kafkaService.email.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.kafkaService.email.application.port.in.KafkaConsumerUseCase;
import kafka.kafkaService.email.application.port.in.NotificationUseCase;
import kafka.kafkaService.global.dto.RecoveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService implements KafkaConsumerUseCase {

    private final ConsumerFactory<String, String> consumerFactory;
    private final NotificationUseCase notificationUseCase;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    private static final String TOPIC_NAME = "recovery.completed.event";


    public int pollAndProcessMessages() {
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
                        notificationUseCase.processRecoveryNotification(event);
                        processedCount++;

                    } catch (Exception e) {
                        log.error("Event Fail. Offset: {}", record.offset(), e);
                        // 데이터를 DB나 DLQ 토픽에 저장하는 로직
                        kafkaProducerService.sendToDlq(record.value(), e);
                    }
                }

                consumer.commitSync(); // Offset Commit (Sync)
            }

        } catch (Exception e) {
            log.error("Fatal Error During Manual Consumption", e);
        }

        return processedCount;
    }
}
