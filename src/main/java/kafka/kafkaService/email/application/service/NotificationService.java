package kafka.kafkaService.email.application.service;

import kafka.kafkaService.email.application.port.in.NotificationUseCase;
import kafka.kafkaService.email.application.port.out.EmailPort;
import kafka.kafkaService.global.dto.RecoveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationUseCase {

    private final EmailPort emailPort;


    @Override
    public void processRecoveryNotification(RecoveryCompletedEvent event) {
        // 나중에 여기에 DB 에서 오늘 이 유저에게 메일 보낸 적 있는지 확인하는 등 비즈니스 로직이 들어감.
        // event 파싱(RECOVER, CLEANUP)은 메인 서버에서 해줌.

        emailPort.sendRecoveryEmail(event);
    }
}