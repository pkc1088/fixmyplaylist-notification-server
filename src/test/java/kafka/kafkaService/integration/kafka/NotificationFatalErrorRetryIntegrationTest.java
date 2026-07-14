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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    @DisplayName("If an error occurs mid-batch, the commit is skipped, and successfully processed events are not re-sent on re-polling")
    void fatalErrorSkipsCommit_andRePollSkipsAlreadySucceededEvent() throws Exception {
        // given:
        String evt1Id = UUID.randomUUID().toString();
        String evt2Id = UUID.randomUUID().toString();
        String evt3Id = UUID.randomUUID().toString();

        RecoveryCompletedEvent event1 = sampleEvent(evt1Id);
        RecoveryCompletedEvent event2 = sampleEvent(evt2Id);
        RecoveryCompletedEvent event3 = sampleEvent(evt3Id);

        produce(event1);
        produce(event2);
        produce(event3);

        // evt-2 처리 시에만 Error(Exception 아님) 발생 스텁
        doNothing().when(emailPort)
                .sendRecoveryEmail(argThat(e -> e.eventId().equals(evt1Id)));

        doThrow(new NoClassDefFoundError("Simulated Cloud Run cold-start linkage error"))
                .when(emailPort)
                .sendRecoveryEmail(argThat(e -> e.eventId().equals(evt2Id)));

        // when: 최초 처리 시도 -> evt-2 Error 로 프로세스 중단
        Assertions.assertThrows(
                NoClassDefFoundError.class,
                () -> notificationUseCase.processPendingNotifications()
        );

        // then:
        assertInboxStatus(evt1Id, Notification.Status.SUCCESS);
        assertInboxStatus(evt2Id, Notification.Status.PENDING);
        Assertions.assertTrue(
                inboxRepository.findById(evt3Id).isEmpty(),
                "evt-3 should not yet exist in the Inbox because batch processing was interrupted"
        );

        verify(dlqPort, never()).sendToDlq(any());


        // 재폴링 시뮬레이션 (새 컨테이너가 뜬 상황)


        reset(emailPort);
        doNothing().when(emailPort).sendRecoveryEmail(any());

        // when: 재시도
        notificationUseCase.processPendingNotifications();

        // then: evt-1은 멱등성에 의해 중복 발송 스킵
        verify(emailPort, never())
                .sendRecoveryEmail(argThat(e -> e.eventId().equals(evt1Id)));

        // evt-3은 정상 발송 및 성공 처리
        verify(emailPort, times(1))
                .sendRecoveryEmail(argThat(e -> e.eventId().equals(evt3Id)));

        assertInboxStatus(evt3Id, Notification.Status.SUCCESS);

        // evt-2는 PK 중복으로 스킵되니까 PENDING 상태 유지 (스케줄러 Retry 대상)
        verify(emailPort, never())
                .sendRecoveryEmail(argThat(e -> e.eventId().equals(evt2Id)));

        assertInboxStatus(evt2Id, Notification.Status.PENDING);
    }

    private void assertInboxStatus(String eventId, Notification.Status expected) {
        NotificationJpaEntity entity = inboxRepository.findById(eventId)
                .orElseThrow(() -> new AssertionError("Inbox should contain " + eventId));

        Assertions.assertEquals(expected, entity.getStatus(), "Unexpected status for " + eventId);
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
