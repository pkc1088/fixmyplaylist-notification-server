package kafka.kafkaService.email.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.kafkaService.email.application.port.out.NotificationInboxPort;
import kafka.kafkaService.email.domain.model.NotificationInbox;
import kafka.kafkaService.global.dto.RecoveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InboxStateManager {

    private final NotificationInboxPort notificationInboxPort;
    private final ObjectMapper objectMapper;


    @Transactional
    public boolean saveToInboxIdempotent(RecoveryCompletedEvent event) {
        try {
            String payloadJson = objectMapper.writeValueAsString(event);

            NotificationInbox inbox = NotificationInbox.builder()
                    .eventId(event.eventId())
                    .userId(event.userId())
                    .userEmail(event.userEmail())
                    .payload(payloadJson)
                    .build();

            notificationInboxPort.save(inbox);

            return true;

        } catch (DataIntegrityViolationException e) {
            return false; // 중복 이벤트 수신

        } catch (Exception e) {
            throw new RuntimeException("Fail to save Inbox", e);
        }
    }


    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000)
    )
    @Transactional
    public void updateInboxStatusToSuccess(String eventId) {
        notificationInboxPort.updateStatusDirectly(eventId, NotificationInbox.Status.SUCCESS);
    }


    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateInboxStatusToFailed(String eventId) {
        notificationInboxPort.updateStatusDirectly(eventId, NotificationInbox.Status.FAILED);
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleRetryFailure(String eventId, int currentRetryCount, int maxRetryCount) {

        if (currentRetryCount >= maxRetryCount - 1) {
            // 더 이상 가망 없는 애들은 DEAD
            notificationInboxPort.updateStatusDirectly(eventId, NotificationInbox.Status.DEAD);
            log.error("Mark Event {} after {} times of retry", eventId, maxRetryCount);

        } else {
            notificationInboxPort.updateStatusDirectly(eventId, NotificationInbox.Status.FAILED);
            notificationInboxPort.incrementRetryCountDirectly(eventId);
            log.warn("Retry Failed on Event {}. Increase retry count.", eventId);
        }
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsDeadImmediately(String eventId) {
        notificationInboxPort.updateStatusDirectly(eventId, NotificationInbox.Status.DEAD);
    }
}
