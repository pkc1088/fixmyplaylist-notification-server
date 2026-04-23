package kafka.kafkaService.local.email;

import kafka.kafkaService.email.application.port.out.EmailPort;
import kafka.kafkaService.global.dto.RecoveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@SpringBootTest
@TestPropertySource("classpath:application.yml")
public class ResendEmailAdapterTest {

    @Autowired
    private EmailPort resendEmailAdapter;


    @Test
    public void sendEmailTest() {

        String eventId = "eventId-234";
        String userId = "112735690496635663877";
        String userName = "pkc1088";
        String userEmail = "pkc1088@gmail.com";  //  "pkcmax@naver.com";

        RecoveryCompletedEvent recoveryCompletedEvent = new RecoveryCompletedEvent(
                eventId,
                userId,
                userName,
                userEmail,
                makeSomeRecoveryDetail(),
                makeSomeCleanupDetail(),
                LocalDateTime.now()
        );

        try {
            resendEmailAdapter.sendRecoveryEmail(recoveryCompletedEvent);
            log.info("Success (Event ID: {})", eventId);
        } catch (Exception e) {
            log.info(e.getMessage());
        }
    }

    private List<RecoveryCompletedEvent.RecoveryDetail> makeSomeRecoveryDetail() {
        List<RecoveryCompletedEvent.RecoveryDetail> list = new ArrayList<>();

        String playlistId = "playlistId-A";
        String targetVideoId = "targetVideoId-";
        String targetVideoTitle = "targetVideoTitle-";
        String sourceVideoId = "sourceVideoId-";
        String sourceVideoTitle = "sourceVideoTitle-";

        for (int i = 0; i < 3; i++) {
            list.add(new RecoveryCompletedEvent.RecoveryDetail(
                    playlistId,
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
        String targetVideoId = "targetVideoId-";
        String targetVideoTitle = "targetVideoTitle-";

        for (int i = 0; i < 3; i++) {
            list.add(new RecoveryCompletedEvent.CleanupDetail(
                    playlistId,
                    targetVideoId + i,
                    targetVideoTitle + i
            ));
        }

        return list;
    }
}
