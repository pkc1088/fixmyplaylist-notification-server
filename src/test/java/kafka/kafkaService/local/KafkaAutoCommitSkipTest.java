package kafka.kafkaService.local;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
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
import java.util.HashSet;
import java.util.Map;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = { "dev.recovery.completed.event" })
public class KafkaAutoCommitSkipTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private static final String TOPIC = "dev.recovery.completed.event";
    private static final String GROUP_ID = "dev-notification-group";

    @Test
    @DisplayName("Verify if try-with-resources close() commits leftover offset after Error")
    void testAutoCommitSkipOnFatalError() {
        // 프로듀서로 이벤트 3개 발행 (Offset: 0, 1, 2)
        produceTestMessages(3);

        // enable.auto.commit = true
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(GROUP_ID, "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // try-with-resources + Error
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(TOPIC));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(3));
            System.out.println(">> [Initial] Records from Broker(Fetch): " + records.count());

            for (ConsumerRecord<String, String> record : records) {
                System.out.println(">> Processing ... Offset: " + record.offset());

                // Offset 0 -> Error(!= Exception)
                if (record.offset() == 0) {
                    System.out.println(">> Offset 0 Error");
                    throw new NoClassDefFoundError("Intended OkHttp Error");
                }
            }
        } catch (Throwable t) {
            System.out.println(">> consumer.close() called: " + t.getMessage());
        }

        System.out.println("==================================================");
        System.out.println(">> Consumer Retry");
        System.out.println("==================================================");

        try (KafkaConsumer<String, String> newConsumer = new KafkaConsumer<>(consumerProps)) {
            TopicPartition partition = new TopicPartition(TOPIC, 0);

            // dummy poll for partition allocation
            newConsumer.assign(Collections.singletonList(partition));
            newConsumer.poll(Duration.ofSeconds(1));

            long committedOffset = newConsumer.committed(new HashSet<>(Collections.singletonList(partition)))
                    .get(partition).offset();

            System.out.println(">> Check Committed Offset: " + committedOffset);

            // Offset 0에서 터졌어도, 가져왔던 0, 1, 2가 모두 스킵되고 다음 차례인 3으로 커밋되었는지 확인
            Assertions.assertEquals(3L, committedOffset, "ConsumerLag gotta jump to 3");
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