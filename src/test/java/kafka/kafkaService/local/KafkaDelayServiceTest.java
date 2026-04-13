package kafka.kafkaService.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.kafkaService.local.dto.RecoveryEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

@Slf4j
@EnableKafka
@SpringBootTest
@TestPropertySource("classpath:application.yml")
public class KafkaDelayServiceTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private KafkaListenerEndpointRegistry registry;
    @Autowired
    private ObjectMapper objectMapper;

    private static final String TOPIC_NAME = "recovery.completed.event";
    private static final String LISTENER_ID = "my-noti-listener";


    @Test
    public void runWakeUpScenario() throws InterruptedException {

        registry.getListenerContainer(LISTENER_ID).stop();

        log.warn("=============================================");
        log.warn("[시나리오 시작] 알림 서버 뻗음");
        log.warn("=============================================");

        for (int i = 1; i <= 3; i++) {
            RecoveryEvent mockEvent = new RecoveryEvent((long) i, "user" + i + "@gmail.com", i * 1000);
            sendMessage(mockEvent);
            Thread.sleep(1000);
        }

        log.warn("=============================================");
        log.warn("메인 서버는 카프카에 메시지 3개 생산 완료");
        log.warn("20초 뒤, 알림 서버 깨어남...");
        log.warn("=============================================");

        Thread.sleep(20000);
        log.warn("컨슈머 강제 기상 명령 하달");

        registry.getListenerContainer(LISTENER_ID).start();
        Thread.sleep(30000);
        log.warn("테스트 종료");
    }

    public void sendMessage(RecoveryEvent event) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC_NAME, jsonMessage);
            log.info("[프로듀서] {}번 유저 복구 완료 이벤트 카프카 발송", event.userId());

        } catch (Exception e) {
            log.error("전송 실패", e);
        }
    }

    @TestConfiguration
    static class TestKafkaListenerConfig {

        @Autowired
        private ObjectMapper objectMapper;


        @KafkaListener(id = LISTENER_ID, topics = TOPIC_NAME, groupId = "notification-group", autoStartup = "false")
        public void consumeMessage(String jsonMessage) {
            try {
                RecoveryEvent event = objectMapper.readValue(jsonMessage, RecoveryEvent.class);
                log.info("[알림 서버 깨어남] 밀린 메시지 처리 중... 대상: {}, 복구 건수: {}", event.userEmail(), event.recoveredCount());
                Thread.sleep(500);

            } catch (Exception e) {
                log.error("수신 실패", e);
            }
        }
    }
}