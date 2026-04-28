package kafka.kafkaService.unit.Idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.kafkaService.email.application.port.out.DlqPort;
import kafka.kafkaService.email.application.port.out.EmailPort;
import kafka.kafkaService.email.application.port.out.EventProcessor;
import kafka.kafkaService.email.application.port.out.MessagePullPort;
import kafka.kafkaService.email.application.service.InboxStateService;
import kafka.kafkaService.email.application.service.NotificationService;
import kafka.kafkaService.email.application.service.dto.RecoveryCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationService notificationService;

    private RecoveryCompletedEvent dummyEvent;

    @BeforeEach
    void setUp() {
        dummyEvent = new RecoveryCompletedEvent(
                "evt_test_123", "userId", "userName", "test@test.com",
                Collections.emptyList(), Collections.emptyList(), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("Success Flow: Inbox save success -> Call Resend API -> Status update to SUCCESS")
    void process_SuccessFlow() throws Exception {
        // given
        doAnswer(invocation -> {
            EventProcessor processor = invocation.getArgument(0);
            processor.process(dummyEvent);
            return 1;
        }).when(messagePullPort).pullAndProcess(any(EventProcessor.class));

        // general use case
        given(inboxStateService.saveToInboxIdempotent(eq(dummyEvent), any())).willReturn(true);

        // when
        notificationService.processPendingNotifications();

        // then: Resend 호출과 상태 업데이트 1번씩 실행되어야 함
        verify(resendEmailAdapter, times(1)).sendRecoveryEmail(dummyEvent);
        verify(inboxStateService, times(1)).updateInboxStatusToSuccess("evt_test_123");
        verify(dlqPort, never()).sendToDlq(anyString());
    }

    @Test
    @DisplayName("Idempotent Test: Do not send email and just ignore when Inbox returns false (already processed event)")
    void process_IdempotencyDefense() throws Exception {
        // given
        doAnswer(invocation -> {
            EventProcessor processor = invocation.getArgument(0);
            processor.process(dummyEvent);
            return 1;
        }).when(messagePullPort).pullAndProcess(any(EventProcessor.class));

        // duplicated event
        given(inboxStateService.saveToInboxIdempotent(eq(dummyEvent), any())).willReturn(false);

        // when
        notificationService.processPendingNotifications();

        // then
        verify(resendEmailAdapter, never()).sendRecoveryEmail(any());
        verify(inboxStateService, never()).updateInboxStatusToSuccess(anyString());
    }

    @Test
    @DisplayName("Resend API Fail: fallback to OnFail() after failing to send emails")
    void process_ResendApiFail_TriggersOnFail() throws Exception {
        // given
        doAnswer(invocation -> {
            EventProcessor processor = invocation.getArgument(0);
            try {
                processor.process(dummyEvent);
            } catch (Exception e) {
                processor.onFail("dummy_raw_message");
            }
            return 1;
        }).when(messagePullPort).pullAndProcess(any(EventProcessor.class));

        given(inboxStateService.saveToInboxIdempotent(eq(dummyEvent), any())).willReturn(true);
        given(objectMapper.readValue(eq("dummy_raw_message"), eq(RecoveryCompletedEvent.class))).willReturn(dummyEvent);
        // Resend API 예외
        doThrow(new RuntimeException("Resend API Timeout")).when(resendEmailAdapter).sendRecoveryEmail(dummyEvent);

        // when
        notificationService.processPendingNotifications();

        // then: SUCCESS 업데이트는 실행되지 않고, DLQ 발행 및 FAILED 업데이트가 실행되어야 함
        verify(inboxStateService, never()).updateInboxStatusToSuccess(anyString());
        verify(dlqPort, times(1)).sendToDlq(eq("dummy_raw_message"));
        verify(inboxStateService, times(1)).updateInboxStatusToFailed(anyString());
    }
}