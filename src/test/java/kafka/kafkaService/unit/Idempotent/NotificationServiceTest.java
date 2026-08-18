package kafka.kafkaService.unit.Idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.kafkaService.email.application.port.out.*;
import kafka.kafkaService.email.application.port.out.dto.RecoveryCompletedEvent;
import kafka.kafkaService.email.application.service.InboxStateService;
import kafka.kafkaService.email.application.service.NotificationService;
import kafka.kafkaService.email.domain.model.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private MessagePullPort messagePullPort;

    @Mock
    private EmailPort resendEmailAdapter;

    @Mock
    private DlqPort dlqPort;

    @Mock
    private InboxStateService inboxStateService;

    @Mock
    private NotificationMetricsPort notificationMetricsPort;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationService notificationService;

    private RecoveryCompletedEvent dummyEvent;
    private Notification dummyNotification;
    private final String dummyPayload = "{\"eventId\":\"evt_test_123\"}";


    @BeforeEach
    void setUp() {
        dummyEvent = new RecoveryCompletedEvent(
                "evt_test_123", "userId", "userName", "test@test.com",
                Collections.emptyList(), Collections.emptyList(), LocalDateTime.now()
        );

        dummyNotification = Notification.create(
                "evt_test_123", "userId", "test@test.com", dummyPayload
        );
    }

    @Test
    @DisplayName("Success Flow: Inbox 적재 -> DB에서 대상 조회 -> 이메일 발송 -> 도메인 상태 SUCCESS 변경")
    void process_SuccessFlow() throws Exception {
        // given
        // 1. 수신부 모킹
        doAnswer(invocation -> {
            EventProcessor processor = invocation.getArgument(0);
            processor.process(dummyEvent);
            return 1;
        }).when(messagePullPort).pullAndProcess(any(EventProcessor.class));

        given(objectMapper.writeValueAsString(dummyEvent)).willReturn(dummyPayload);

        // 2. 처리부 모킹
        given(inboxStateService.findPendingOrFailedCandidates()).willReturn(List.of(dummyNotification));
        given(objectMapper.readValue(dummyPayload, RecoveryCompletedEvent.class)).willReturn(dummyEvent);

        // when
        notificationService.processNotifications();

        // then
        // 1. 이메일 발송 API 가 정상 호출되었는가?
        verify(resendEmailAdapter, times(1)).sendRecoveryEmail(dummyEvent);

        // 2. DB 업데이트 메서드에 넘어간 파라미터(Notification) 캡처
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(inboxStateService, times(1)).updateNotification(captor.capture());

        // 3. 도메인 스스로 상태를 SUCCESS 로 잘 변경했는지 검증
        Notification captured = captor.getValue();
        assertThat(captured.getStatus()).isEqualTo(Notification.Status.SUCCESS);

        // 4. 성공 메트릭 기록 검증
        verify(notificationMetricsPort, times(1)).recordSuccess();
        verify(dlqPort, never()).sendToDlq(anyString());
    }

    @Test
    @DisplayName("Empty Target: 처리할 알림이(PENDING/FAILED) 없으면 메일 발송 및 업데이트를 스킵한다")
    void process_EmptyTarget_SkipsProcessing() throws Exception {
        // given
        given(messagePullPort.pullAndProcess(any())).willReturn(0);
        given(inboxStateService.findPendingOrFailedCandidates()).willReturn(Collections.emptyList());

        // when
        notificationService.processNotifications();

        // then
        verify(resendEmailAdapter, never()).sendRecoveryEmail(any());
        verify(inboxStateService, never()).updateNotification(any());
    }

    @Test
    @DisplayName("Resend API Fail (재시도 가능): 메일 발송 실패 시 도메인 상태가 FAILED 로 변경되고 RetryCount 가 증가한다")
    void process_ResendApiFail_ChangesStatusToFailed() throws Exception {
        // given
        given(messagePullPort.pullAndProcess(any())).willReturn(0);
        given(inboxStateService.findPendingOrFailedCandidates()).willReturn(List.of(dummyNotification));
        given(objectMapper.readValue(dummyPayload, RecoveryCompletedEvent.class)).willReturn(dummyEvent);

        // 이메일 발송 시 예외 발생
        doThrow(new RuntimeException("Resend API Timeout")).when(resendEmailAdapter).sendRecoveryEmail(dummyEvent);

        // when
        notificationService.processNotifications();

        // then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(inboxStateService, times(1)).updateNotification(captor.capture());

        Notification captured = captor.getValue();
        assertThat(captured.getStatus()).isEqualTo(Notification.Status.FAILED);
        assertThat(captured.getRetryCount()).isEqualTo(1); // 1회 실패로 카운트 증가

        verify(notificationMetricsPort, times(1)).recordFail();
    }

    @Test
    @DisplayName("Resend API Fail (최대 재시도 초과): 재시도 횟수 소진 시 도메인 상태가 DEAD 로 전이된다")
    void process_ResendApiFail_ChangesStatusToDead() throws Exception {
        // given
        // 이미 2번 실패하여 다음 실패 시 DEAD 가 되어야 하는 엔티티
        Notification almostDeadNotification = Notification.reconstitute(
                "evt_test_123", "userId", "test@test.com", dummyPayload,
                Notification.Status.FAILED, 2, LocalDateTime.now(), LocalDateTime.now()
        );

        given(messagePullPort.pullAndProcess(any())).willReturn(0);
        given(inboxStateService.findPendingOrFailedCandidates()).willReturn(List.of(almostDeadNotification));
        given(objectMapper.readValue(dummyPayload, RecoveryCompletedEvent.class)).willReturn(dummyEvent);

        doThrow(new RuntimeException("Resend API Timeout")).when(resendEmailAdapter).sendRecoveryEmail(dummyEvent);

        // when
        notificationService.processNotifications();

        // then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(inboxStateService, times(1)).updateNotification(captor.capture());

        Notification captured = captor.getValue();
        assertThat(captured.getStatus()).isEqualTo(Notification.Status.DEAD);

        verify(notificationMetricsPort, times(1)).recordFinalizeDead();
    }

    @Test
    @DisplayName("Kafka Ingestion Fail: 수신 단계에서 파싱 에러 발생 시 DLQ로 전송한다")
    void process_KafkaPullFail_TriggersDlq() throws Exception {
        // given: onFail 콜백이 실행되도록 모킹
        doAnswer(invocation -> {
            EventProcessor processor = invocation.getArgument(0);
            processor.onFail("malformed_raw_message");
            return 0;
        }).when(messagePullPort).pullAndProcess(any(EventProcessor.class));

        // when
        notificationService.processNotifications();

        // then
        verify(dlqPort, times(1)).sendToDlq("malformed_raw_message");
    }
}