package kafka.kafkaService.email.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final String DLQ_TOPIC = "recovery.dlq.event";

    public void sendToDlq(String rawMessage, Exception e) {
        // 원본 데이터에 에러 원인을 살짝 덧붙여서 보낼 수도 있고, 원본 그대로 보낼 수도 있습니다.
        // 여기서는 분석을 위해 에러 메시지를 포함한 새로운 JSON 을 만들거나 로그를 남깁니다.
        kafkaTemplate.send(DLQ_TOPIC, rawMessage);
    }
}