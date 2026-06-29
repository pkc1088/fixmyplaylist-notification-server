package kafka.kafkaService.global.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

@Slf4j
@Configuration
public class MetricsConfig {

    private static final String INSTANCE_ID = fetchInstanceId();


    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("instance_id", INSTANCE_ID);
    }

    private static String fetchInstanceId() {
        String metadataUrl = "http://metadata.google.internal/computeMetadata/v1/instance/id";
        try {
            URL url = new URL(metadataUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Metadata-Flavor", "Google");
            conn.setRequestMethod("GET");

            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    return in.readLine();
                }
            }
        } catch (Exception e) {
            // pass for local
        }

        return UUID.randomUUID().toString();
    }
}