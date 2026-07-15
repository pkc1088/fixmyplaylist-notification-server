package kafka.kafkaService.local;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = { "dev.recovery.completed.event" })
public class KafkaManualCommitSafeTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private static final String TOPIC = "dev.recovery.completed.event";
    private static final String GROUP_ID = "dev-notification-group-safe";

    @Test
    @DisplayName("Verify if failed messages are re-consumed after restart when offsets are manually managed.")
    void testManualCommitPreventsSkipOnFatalError() {

        produceTestMessages(3);

        // enable.auto.commit = false
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(GROUP_ID, "false", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(TOPIC));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(3));
            System.out.println(">> [Initial] Records from Broker(Fetch): " + records.count());

            for (ConsumerRecord<String, String> record : records) {
                if (record.offset() == 0) {
                    System.out.println(">> Offset 0 Error");
                    throw new NoClassDefFoundError("Intended OkHttp Error");
                }
            }
            // 이 라인 절대 실행 안됨
            consumer.commitSync();

        } catch (Throwable t) {
            System.out.println(">> consumer.close() called: " + t.getMessage());
        }

        System.out.println("==================================================");
        System.out.println(">> Consumer Retry");
        System.out.println("==================================================");

        try (KafkaConsumer<String, String> newConsumer = new KafkaConsumer<>(consumerProps)) {
            newConsumer.subscribe(Collections.singletonList(TOPIC));

            ConsumerRecords<String, String> newRecords = newConsumer.poll(Duration.ofSeconds(3));

            if (!newRecords.isEmpty()) {
                long firstOffset = newRecords.iterator().next().offset();
                System.out.println(">> [Retry] Offset of the first record: " + firstOffset);
                // 이전에 0, 1, 2를 가져갔었지만 커밋을 안 했으니, 다시 Offset 0부터 읽어와야함
                Assertions.assertEquals(0L, firstOffset, "gotta read offset 0 without losing anything");
                Assertions.assertEquals(3, newRecords.count(), "gotta fetch 3 messages");
            } else {
                Assertions.fail("Fail");
            }
        }
    }

    private void produceTestMessages(int count) {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            for (int i = 0; i < count; i++) {
                producer.send(new ProducerRecord<>(TOPIC, "key-" + i, "value-" + i));
            }
            producer.flush();
        }
    }
}