package kafka.kafkaService.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import kafka.kafkaService.email.application.port.out.DlqPort;
import kafka.kafkaService.global.dto.RecoveryCompletedEvent;
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

    private static String userId;
    private static String userName;
    private static String userEmail;

    @Value("${notification.secret}") private String SECRET_KEY;
    private static final String TOPIC = "recovery.completed.event";


    @BeforeEach
    void setUp() {
        userId = "112735690496635663877";
        userName = "pkc1088";
        userEmail = "pkcmax@naver.com";
        // 깡통 MimeMessage 를 반환하도록 세팅 (NullPointerException 방지)
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    @DisplayName("성공 시나리오: 카프카에 이벤트 있으면 메일 1번 발송하고 200 OK 반환")
    void testSuccessfulEmailNotification() throws Exception {

        RecoveryCompletedEvent event = new RecoveryCompletedEvent(
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

        // [검증] 메일 발송 로직 1번 호출되었는지 확인
        verify(mailSender, times(1)).send(any(MimeMessage.class));

        // [검증] 에러가 없었으니 DLQ 는 한 번도 호출되지 않아야 함
        verify(dlqPort, never()).sendToDlq(anyString(), any(Exception.class));
    }

    @Test
    @DisplayName("실패 시나리오: 메일 발송 예외 발생 시 3번 재시도 후 DLQ 전송")
    void testPoisonPillGoesToDlq() throws Exception {
        // [메일 서버 장애 시뮬레이션] 메일 전송 시도 시 무조건 에러가 나도록 조작
        doThrow(new RuntimeException("SMTP Server Down!")).when(mailSender).send(any(MimeMessage.class));

        RecoveryCompletedEvent event = new RecoveryCompletedEvent(
                userId,
                userName,
                userEmail,
                makeSomeRecoveryDetail(),
                makeSomeCleanupDetail(),
                LocalDateTime.now()
        );
        String eventJson = objectMapper.writeValueAsString(event);
        testKafkaProducer.send(TOPIC, eventJson).get();

        // [스케줄러 역할] 에러가 나도 catch 해서 처리하므로 200 OK가 떨어져야 함
        mockMvc.perform(post("/api/internal/notifications/recovery-completed")
                        .header("X-Notification-Secret", SECRET_KEY))
                .andExpect(status().isOk())
                .andExpect(content().string("Successfully processed and sent 0 emails."));

        // [검증] @Retryable(maxAttempts = 3)이 정상 작동해서 정확히 3번 메일 발송 시도했는가?
        verify(mailSender, times(3)).send(any(MimeMessage.class));

        // [검증] 3번 다 실패했으니, DLQ 포트가 정확히 1번 호출되어 에러 데이터를 격리했는가?
        verify(dlqPort, times(1)).sendToDlq(eq(eventJson), any(RuntimeException.class));
    }

    @Test
    @DisplayName("보안 검증: 잘못된 시크릿 키로 요청하면 403 Forbidden을 반환한다")
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