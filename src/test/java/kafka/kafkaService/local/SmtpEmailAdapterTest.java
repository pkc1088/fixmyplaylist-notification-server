package kafka.kafkaService.local;

import kafka.kafkaService.email.application.port.out.EmailPort;
import kafka.kafkaService.global.dto.RecoveryCompletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@TestPropertySource("classpath:application.yml")
public class SmtpEmailAdapterTest {

    @Autowired
    private EmailPort emailPort;


    @Test
    public void sendEmailTest() throws Exception {
        String userId = "112735690496635663877";
        String userName = "pkc1088";
        String userEmail = "pkcmax@naver.com";

        RecoveryCompletedEvent recoveryCompletedEvent = new RecoveryCompletedEvent(
                userId,
                userName,
                userEmail,
                makeSomeRecoveryDetail(),
                makeSomeCleanupDetail(),
                LocalDateTime.now()
        );

        emailPort.sendRecoveryEmail(recoveryCompletedEvent);
    }

    private List<RecoveryCompletedEvent.RecoveryDetail> makeSomeRecoveryDetail() {
        List<RecoveryCompletedEvent.RecoveryDetail> list = new ArrayList<>();

        String playlistId = "playlistId-A";
        String playlistTitle = "playlistTitle-A";
        String targetVideoId = "targetVideoId-";
        String targetVideoTitle = "targetVideoTitle-";
        String sourceVideoId = "sourceVideoId-";
        String sourceVideoTitle = "sourceVideoTitle-";

        for (int i = 0; i < 3; i++) {
            list.add(new RecoveryCompletedEvent.RecoveryDetail(
                    playlistId,
                    playlistTitle,
                    targetVideoId + i,
                    targetVideoTitle + i,
                    sourceVideoId + i,
                    sourceVideoTitle + i
            ));
        }

        return list;
    }

    private List<RecoveryCompletedEvent.CleanupDetail> makeSomeCleanupDetail() {
        List<RecoveryCompletedEvent.CleanupDetail> list = new ArrayList<>();

        String playlistId = "playlistId-B";
        String playlistTitle = "playlistTitle-B";
        String targetVideoId = "targetVideoId-";
        String targetVideoTitle = "targetVideoTitle-";

        for (int i = 0; i < 3; i++) {
            list.add(new RecoveryCompletedEvent.CleanupDetail(
                    playlistId,
                    playlistTitle,
                    targetVideoId + i,
                    targetVideoTitle + i
            ));
        }

        return list;
    }
}
