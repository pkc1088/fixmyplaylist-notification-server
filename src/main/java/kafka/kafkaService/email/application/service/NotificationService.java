package kafka.kafkaService.email.application.service;

import kafka.kafkaService.email.application.port.in.NotificationUseCase;
import kafka.kafkaService.email.application.port.out.DlqPort;
import kafka.kafkaService.email.application.port.out.EmailPort;
import kafka.kafkaService.email.application.port.out.MessagePullPort;
import kafka.kafkaService.global.dto.RecoveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import kafka.kafkaService.email.application.port.out.EventProcessor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationUseCase {

    private final MessagePullPort messagePullPort;
    private final EmailPort emailPort;
    private final DlqPort dlqPort;


    @Override
    public int processPendingNotifications() {
        // 콜백 전달
        return messagePullPort.pullAndProcess(new EventProcessor() {

            @Override
            public void process(RecoveryCompletedEvent event) throws Exception {
                // 실패 시 여기서 예외가 터짐
                emailPort.sendRecoveryEmail(event);
            }

            @Override
            public void onFail(String rawMessage, Exception e) {
                // 실패 처리 로직(DLQ)
                dlqPort.sendToDlq(rawMessage, e);
            }
        });
    }
}