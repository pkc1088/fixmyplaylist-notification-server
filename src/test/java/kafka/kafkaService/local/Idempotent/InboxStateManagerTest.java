package kafka.kafkaService.local.Idempotent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.kafkaService.email.application.port.out.NotificationInboxPort;
import kafka.kafkaService.email.application.service.InboxStateManager;
import kafka.kafkaService.email.domain.model.NotificationInbox;
import kafka.kafkaService.global.dto.RecoveryCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InboxStateManagerTest {

    @Mock
    private NotificationInboxPort inboxPort;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private InboxStateManager inboxStateManager;

    private RecoveryCompletedEvent dummyEvent;

    @BeforeEach
    void setUp() {
        dummyEvent = new RecoveryCompletedEvent(
                "evt_test_123", "userId", "userName", "test@test.com",
                Collections.emptyList(), Collections.emptyList(), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("Initial Event: Successfully save it to DB then return True.")
    void saveToInboxIdempotent_Success() throws JsonProcessingException {
        // given
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        given(inboxPort.save(any(NotificationInbox.class))).willReturn(null);

        // when
        boolean isNew = inboxStateManager.saveToInboxIdempotent(dummyEvent);

        // then
        assertThat(isNew).isTrue();
        verify(inboxPort).save(any(NotificationInbox.class));
    }

    @Test
    @DisplayName("Duplicated Event: Catch PK Error then return False.")
    void saveToInboxIdempotent_Duplicate() throws JsonProcessingException {
        // given
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        // DataIntegrityViolationException
        given(inboxPort.save(any(NotificationInbox.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate PK"));

        // when
        boolean isNew = inboxStateManager.saveToInboxIdempotent(dummyEvent);

        // then
        assertThat(isNew).isFalse();
    }
}