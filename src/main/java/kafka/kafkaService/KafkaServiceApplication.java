package kafka.kafkaService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableRetry
@EnableScheduling
@SpringBootApplication(scanBasePackages = "kafka.kafkaService")
public class KafkaServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(KafkaServiceApplication.class, args);
	}
}
