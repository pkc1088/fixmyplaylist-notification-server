package kafka.kafkaService.global.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

//@Configuration
//public class KafkaConsumerConfig {
//
//    @Bean
//    public CommonErrorHandler errorHandler(KafkaOperations<Object, Object> template) {
//        // 실패 시 1초 간격으로 최대 3번 재시도 후, 그래도 실패하면 DLT(Dead Letter Topic)로 전송
//        return new DefaultErrorHandler(
//                new DeadLetterPublishingRecoverer(template),
//                new FixedBackOff(1000L, 3L)
//        );
//    }
//}