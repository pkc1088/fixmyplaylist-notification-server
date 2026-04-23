package kafka.kafkaService.email.application.port.out;

import kafka.kafkaService.global.dto.RecoveryCompletedEvent;

// 콜백 정의
public interface EventProcessor {
    // 성공 시 할 일
    void process(RecoveryCompletedEvent event) throws Exception;
    // 실패 시 할 일
    void onFail(String rawMessage);
}