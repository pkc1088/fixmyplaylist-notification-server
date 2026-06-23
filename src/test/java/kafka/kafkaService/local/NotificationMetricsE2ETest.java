package kafka.kafkaService.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import kafka.kafkaService.email.application.port.out.*;
import kafka.kafkaService.email.application.port.out.dto.RecoveryCompletedEvent;
import kafka.kafkaService.email.application.service.NotificationService;
import kafka.kafkaService.email.application.service.RetryNotificationService;
import kafka.kafkaService.email.domain.model.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "management.metrics.tags.env=test-local"
})
class NotificationMetricsE2ETest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RetryNotificationService retryNotificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ConfigurableApplicationContext applicationContext; // Graceful Shutdown 트리거용


    @MockitoBean
    private MessagePullPort messagePullPort;

    @MockitoBean
    private EmailPort resendEmailAdapter;

    @MockitoBean
    private NotificationInboxPort notificationInboxPort;

    @MockitoBean
    private DlqPort dlqPort;

    @Test
    @DisplayName("GCP Cloud Monitoring 파이프라인 E2E 검증 및 Shutdown Flush 테스트")
    void verifyMetricsPipelineAndGracefulShutdown() throws Exception {

        /* =========================================================
         * 1. 데이터 세팅 및 Mock 시나리오 준비
         * ========================================================= */

        // 시나리오 A: 최초 발송 (성공 1건, 실패 1건)
        doAnswer(invocation -> {
            EventProcessor processor = invocation.getArgument(0);

            // [성공 케이스]
            RecoveryCompletedEvent successEvent = createDummyEvent("init-success-1");
            processor.process(successEvent);

            // [실패 케이스]
            RecoveryCompletedEvent failEvent = createDummyEvent("init-fail-1");
            String failJson = objectMapper.writeValueAsString(failEvent);
            processor.onFail(failJson);

            return 2; // 총 2건 배치 처리
        }).when(messagePullPort).pullAndProcess(any(EventProcessor.class));

        when(notificationInboxPort.saveIdempotent(any())).thenReturn(true);

        // 시나리오 B: 재시도 발송 (성공 1건, FAILED 1건, DEAD 1건)
        // retryCount 가 2이므로, 이번에 실패하면 3이 되어 DEAD 판정
        Notification retrySuccess = Notification.reconstitute("retry-succ-1", "userA", "a@a.com", objectMapper.writeValueAsString(createDummyEvent("retry-succ-1")), Notification.Status.FAILED, 1, LocalDateTime.now(), null);
        Notification retryFail = Notification.reconstitute("retry-fail-1", "userB", "b@b.com", objectMapper.writeValueAsString(createDummyEvent("retry-fail-1")), Notification.Status.FAILED, 1, LocalDateTime.now(), null);
        Notification retryDead = Notification.reconstitute("retry-dead-1", "userC", "c@c.com", objectMapper.writeValueAsString(createDummyEvent("retry-dead-1")), Notification.Status.FAILED, 2, LocalDateTime.now(), null);

        when(notificationInboxPort.findRetryCandidates(any(), anyInt())).thenReturn(List.of(retrySuccess, retryFail, retryDead));

        // Resend API 가 fail 또는 dead 아이디를 가지면 에러를 던지도록 조작
        doThrow(new RuntimeException("Resend API Timeout (Mock)"))
                .when(resendEmailAdapter)
                .sendRecoveryEmail(argThat(event -> event.eventId().contains("fail") || event.eventId().contains("dead")));


        /* =========================================================
         * 2. 서비스 로직 실행 (Act)
         * ========================================================= */

        System.out.println("========== [1] 메인 로직 실행 시작 ==========");
        notificationService.processPendingNotifications();
        retryNotificationService.retryFailedNotifications();
        System.out.println("========== [1] 메인 로직 실행 완료 ==========");


        /* =========================================================
         * 3. Graceful Shutdown & Metric Flush 확인 (Assert)
         * ========================================================= */

        System.out.println("========== [2] 애플리케이션 종료 트리거 (Graceful Shutdown) ==========");
        // Micrometer 내부 Hook -> GCP 로 강제 Flush
        meterRegistry.close(); // applicationContext.close();

        System.out.println("========== [3] 테스트 종료 (GCP 콘솔을 확인하세요!) ==========");
    }

    private RecoveryCompletedEvent createDummyEvent(String eventId) {
        return new RecoveryCompletedEvent(
                eventId,
                "user_" + eventId,
                "tester",
                "test@example.com",
                List.of(),
                List.of(),
                LocalDateTime.now()
        );
    }
}