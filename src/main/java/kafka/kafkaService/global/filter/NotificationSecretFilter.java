package kafka.kafkaService.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class NotificationSecretFilter extends OncePerRequestFilter {

    private final String expectedSecret;


    public NotificationSecretFilter(
            @Value("${notification.secret}") String expectedSecret
    ) {
        this.expectedSecret = expectedSecret;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/api/internal/notifications")) {
            String requestSecret = request.getHeader("X-Notification-Secret");

            if (requestSecret == null || !requestSecret.equals(expectedSecret)) {
                log.warn("Invalid Secret Key attempt on {}", request.getRequestURI());
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.getWriter().write("Invalid Secret Key");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}