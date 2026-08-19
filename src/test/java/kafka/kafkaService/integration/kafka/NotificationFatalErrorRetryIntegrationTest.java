package kafka.kafkaService.integration.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.kafkaService.email.adapter.out.persistence.NotificationInboxSdjRepository;
import kafka.kafkaService.email.adapter.out.persistence.NotificationJpaEntity;
import kafka.kafkaService.email.application.port.in.NotificationUseCase;
import kafka.kafkaService.email.application.port.out.DlqPort;
import kafka.kafkaService.email.application.port.out.EmailPort;
import kafka.kafkaService.email.application.port.out.dto.RecoveryCompletedEvent;
import kafka.kafkaService.email.domain.model.Notification;
import kafka.kafkaService.integration.IntegrationTestSupport;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("integration")
@TestPropertySource(properties = {
        "spring.kafka.consumer.group-id=test-fatal-error-group",
        "app.kafka.topic.recovery-completed=test-fatal-error.recovery-completed",
        "app.kafka.topic.recovery-dlq=test-fatal-error.recovery-dlq"
})
@DisplayName("Verify Inbox idempotency after skipped commit and re-polling due to a fatal error")
public class NotificationFatalErrorRetryIntegrationTest extends IntegrationTestSupport {

    @Value("${app.kafka.topic.recovery-completed}")
    private String topicName;

    @Value("${spring.kafka.consumer.enable-auto-commit}")
    private boolean enableAutoCommit;

    @Autowired
    private NotificationUseCase notificationUseCase;

    @Autowired
    private NotificationInboxSdjRepository inboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean(name = "resendEmailAdapter")
    private EmailPort emailPort;

    @MockitoBean
    private DlqPort dlqPort;

    @TestConfiguration
    static class KafkaTestTopicConfig {
        @Bean
        public NewTopic testRecoveryTopic(@Value("${app.kafka.topic.recovery-completed}") String topicName) {
            return new NewTopic(topicName, 1, (short) 1);
        }
    }

    @AfterEach
    void tearDown() {
        inboxRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("Validate YAML integrity: enable-auto-commit must be disabled")
    void verifyAutoCommitIsFalse() {
        Assertions.assertFalse(
                enableAutoCommit,
                "In CI, enable-auto-commit must be false to prevent skipped commits on fatal errors"
        );
    }

    @Test
    @DisplayName("If an error occurs mid-batch during processing, Kafka is already committed, but unprocessed events safely wait in DB as PENDING")
    void fatalErrorInterruptsProcessing_andNextDBPollResumes() throws Exception {
        // given:
        String evt1Id = UUID.randomUUID().toString();
        String evt2Id = UUID.randomUUID().toString();
        String evt3Id = UUID.randomUUID().toString();

        produce(sampleEvent(evt1Id));
        produce(sampleEvent(evt2Id));
        produce(sampleEvent(evt3Id));

        AtomicInteger processCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            int current = processCount.incrementAndGet();
            if (current == 2) {
                // 2번째로 처리되는 이벤트에서 Error 발생(Exception 아님)
                throw new NoClassDefFoundError("Simulated Cloud Run cold-start linkage error");
            }
            return null;
        }).when(emailPort).sendRecoveryEmail(any());

        // when 1차 시도: 수신(3건 Inbox PENDING 저장 및 Kafka 커밋) -> 2번째 메일 발송 중 뻗음
        Assertions.assertThrows(
                NoClassDefFoundError.class,
                () -> notificationUseCase.processNotifications()
        );

        // then 1차 검증:
        List<NotificationJpaEntity> allInboxes = inboxRepository.findAll();

        long successCount = allInboxes.stream().filter(e -> e.getStatus() == Notification.Status.SUCCESS).count();
        long pendingCount = allInboxes.stream().filter(e -> e.getStatus() == Notification.Status.PENDING).count();

        // 1개는 성공, 2개는 실패
        Assertions.assertEquals(1, successCount, "One event should be successfully processed");
        Assertions.assertEquals(2, pendingCount, "Two events should remain PENDING");
        Assertions.assertEquals(3, allInboxes.size(), "All 3 events MUST exist in Inbox (Kafka ingestion succeeded)");

        verify(dlqPort, never()).sendToDlq(any());

        // ============================================
        // Step 2. 재시도
        // ============================================

        reset(emailPort);
        doNothing().when(emailPort).sendRecoveryEmail(any());

        // when 2차 시도: DB 에서 PENDING 인 2건만 읽어와서 발송
        notificationUseCase.processNotifications();

        // then 2차 검증:
        verify(emailPort, times(2)).sendRecoveryEmail(any());

        long finalSuccessCount = inboxRepository.findAll().stream()
                .filter(e -> e.getStatus() == Notification.Status.SUCCESS).count();

        Assertions.assertEquals(3, finalSuccessCount, "All events should eventually be SUCCESS");
    }

    private RecoveryCompletedEvent sampleEvent(String eventId) {
        return new RecoveryCompletedEvent(
                eventId,
                "user-id",
                "user-name",
                "test@example.com",
                Collections.emptyList(),
                Collections.emptyList(),
                LocalDateTime.now()
        );
    }

    private void produce(RecoveryCompletedEvent event) throws Exception {
        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(topicName, event.eventId(), json).get(10, TimeUnit.SECONDS);
    }
}
