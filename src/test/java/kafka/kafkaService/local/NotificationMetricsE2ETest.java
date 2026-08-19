package kafka.kafkaService.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import kafka.kafkaService.email.application.port.out.*;
import kafka.kafkaService.email.application.port.out.dto.RecoveryCompletedEvent;
import kafka.kafkaService.email.application.service.InboxStateService;
import kafka.kafkaService.email.application.service.NotificationService;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "management.metrics.tags.env=test-local"
})
class NotificationMetricsE2ETest {

    @Autowired
    private NotificationService notificationService;

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
    private InboxStateService inboxStateService;

    @MockitoBean
    private DlqPort dlqPort;

    @Test
    @DisplayName("GCP Cloud Monitoring 파이프라인 E2E 검증 및 Shutdown Flush 테스트")
    void verifyMetricsPipelineAndGracefulShutdown() throws Exception {

        /* =========================================================
         * 1. 데이터 세팅 및 Mock 시나리오 준비
         * ========================================================= */

        given(messagePullPort.pullAndProcess(any())).willReturn(0);

        // [최초 시도 그룹: retryCount = 0]
        Notification initSuccess = createDomain("init-succ-1", 0);
        Notification initFail = createDomain("init-fail-1", 0);

        // [재시도 그룹: retryCount > 0]
        Notification retrySuccess = createDomain("retry-succ-1", 1);
        Notification retryFail = createDomain("retry-fail-1", 1);
        Notification retryDead = createDomain("retry-dead-1", 2);

        given(inboxStateService.findPendingOrFailedCandidates())
                .willReturn(List.of(initSuccess, initFail, retrySuccess, retryFail, retryDead));

        // 1-3. Resend API 동작 조작
        doThrow(new RuntimeException("Resend API Timeout (Mock)"))
                .when(resendEmailAdapter)
                .sendRecoveryEmail(argThat(event ->
                        event.eventId().contains("fail") || event.eventId().contains("dead")
                ));

        /* =========================================================
         * 2. 서비스 로직 실행 (Act)
         * ========================================================= */

        System.out.println("========== [1] 통합 메인 로직 실행 시작 ==========");
        notificationService.processNotifications();
        System.out.println("========== [1] 통합 메인 로직 실행 완료 ==========");

        /* =========================================================
         * 3. Graceful Shutdown & Metric Flush 확인 (Assert)
         * ========================================================= */

        System.out.println("========== [2] 애플리케이션 종료 트리거 (Graceful Shutdown) ==========");
        // Micrometer 내부 Hook -> GCP 로 강제 Flush
        meterRegistry.close(); // applicationContext.close();

        System.out.println("========== [3] 테스트 종료 (GCP 콘솔을 확인하세요!) ==========");
        System.out.println("예상 지표: initSuccess=1, initFail=1, retrySuccess=1, retryFail=1, dead=1");
    }

    private Notification createDomain(String eventId, int retryCount) throws Exception {
        RecoveryCompletedEvent event = new RecoveryCompletedEvent(
                eventId, "user_" + eventId, "tester", "test@example.com",
                List.of(), List.of(), LocalDateTime.now()
        );
        String payload = objectMapper.writeValueAsString(event);

        Notification.Status status = (retryCount == 0) ? Notification.Status.PENDING : Notification.Status.FAILED;

        return Notification.reconstitute(
                eventId, "user_" + eventId, "test@example.com", payload, status, retryCount, LocalDateTime.now(), null
        );
    }
}