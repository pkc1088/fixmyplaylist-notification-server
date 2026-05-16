package kafka.kafkaService.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import kafka.kafkaService.email.application.port.out.DlqPort;
import kafka.kafkaService.email.application.port.out.dto.RecoveryCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        // 테스트 돌릴 때는 Confluent Cloud 용 보안 설정을 전부 무효화하고 평문으로 통신
        "spring.kafka.properties.security.protocol=PLAINTEXT",
        "spring.kafka.properties.sasl.mechanism=",
        "spring.kafka.properties.sasl.jaas.config="
})
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.yml")
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = { "listeners=PLAINTEXT://localhost:9092", "port=9092" },
        topics = {"recovery.completed.event", "recovery.dlq.event"}
) // 메모리에 가짜 카프카 브로커 띄우고 토픽 2개를 만듦
class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, String> testKafkaProducer; // 메인 서버(생산자) 역할 대역

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoSpyBean
    private DlqPort dlqPort;

    private static String eventId;
    private static String userId;
    private static String userName;
    private static String userEmail;

    @Value("${notification.secret}") private String SECRET_KEY;
    private static final String TOPIC = "recovery.completed.event";


    @BeforeEach
    void setUp() {
        eventId = "eventId-123";
        userId = "112735690496635663877";
        userName = "pkc1088";
        userEmail = "pkcmax@naver.com";
        // 깡통 MimeMessage 를 반환하도록 세팅 (NullPointerException 방지)
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    @DisplayName("Success Scenario: Send one email as one Kafka event exists then return 200.")
    void testSuccessfulEmailNotification() throws Exception {

        RecoveryCompletedEvent event = new RecoveryCompletedEvent(
                eventId,
                userId,
                userName,
                userEmail,
                makeSomeRecoveryDetail(),
                makeSomeCleanupDetail(),
                LocalDateTime.now()
        );

        String eventJson = objectMapper.writeValueAsString(event);
        testKafkaProducer.send(TOPIC, eventJson).get();

        // [스케줄러 역할]
        mockMvc.perform(post("/api/internal/notifications/recovery-completed")
                        .header("X-Notification-Secret", SECRET_KEY))
                .andExpect(status().isOk())
                .andExpect(content().string("Successfully processed and sent 1 emails."));

        // then
        verify(mailSender, times(1)).send(any(MimeMessage.class));

        verify(dlqPort, never()).sendToDlq(anyString());
    }

    @Test
    @DisplayName("Fail Scenario: exception after 3 times of sending emails then send DLQ.")
    void testPoisonPillGoesToDlq() throws Exception {
        // given
        doThrow(new RuntimeException("SMTP Server Down!")).when(mailSender).send(any(MimeMessage.class));

        RecoveryCompletedEvent event = new RecoveryCompletedEvent(
                eventId,
                userId,
                userName,
                userEmail,
                makeSomeRecoveryDetail(),
                makeSomeCleanupDetail(),
                LocalDateTime.now()
        );

        // when
        String eventJson = objectMapper.writeValueAsString(event);
        testKafkaProducer.send(TOPIC, eventJson).get();

        // [스케줄러 역할] 에러가 나도 catch 해서 처리하므로 200 OK가 떨어져야 함
        mockMvc.perform(post("/api/internal/notifications/recovery-completed")
                        .header("X-Notification-Secret", SECRET_KEY))
                .andExpect(status().isOk())
                .andExpect(content().string("Successfully processed and sent 0 emails."));

        // then
        verify(mailSender, times(3)).send(any(MimeMessage.class));

        verify(dlqPort, times(1)).sendToDlq(eq(eventJson));
    }

    @Test
    @DisplayName("Security Check: Return 403 Forbidden")
    void testInvalidSecretKey() throws Exception {
        mockMvc.perform(post("/api/internal/notifications/recovery-completed")
                        .header("X-Notification-Secret", "이상한_비밀번호"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Invalid Secret Key"));
    }


    private List<RecoveryCompletedEvent.RecoveryDetail> makeSomeRecoveryDetail() {
        List<RecoveryCompletedEvent.RecoveryDetail> list = new ArrayList<>();

        String playlistId = "playlistId-A";
        String targetVideoId = "targetVideoId-";
        String targetVideoTitle = "targetVideoTitle-";
        String sourceVideoId = "sourceVideoId-";
        String sourceVideoTitle = "sourceVideoTitle-";

        for (int i = 0; i < 3; i++) {
            list.add(new RecoveryCompletedEvent.RecoveryDetail(
                    playlistId,
                    targetVideoId + i,
                    targetVideoTitle + i,
                    sourceVideoId + i,
                    sourceVideoTitle + i
            ));
        }

        return list;
    }

    private List<RecoveryCompletedEvent.CleanupDetail> makeSomeCleanupDetail() {
        List<RecoveryCompletedEvent.CleanupDetail> list = new ArrayList<>();

        String playlistId = "playlistId-B";
        String targetVideoId = "targetVideoId-";
        String targetVideoTitle = "targetVideoTitle-";

        for (int i = 0; i < 3; i++) {
            list.add(new RecoveryCompletedEvent.CleanupDetail(
                    playlistId,
                    targetVideoId + i,
                    targetVideoTitle + i
            ));
        }

        return list;
    }
}