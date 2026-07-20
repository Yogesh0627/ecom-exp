package com.ecoexpress.common.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Resend-backed email sender. A direct HTTPS call to the Resend API (no SDK) on a background thread,
 * so sending never adds latency to — or fails — the request that triggered it. When no API key is
 * configured it is a clean no-op, so notifications are still recorded and the app runs offline-clean.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResendEmailSender implements EmailSender {

    private static final String ENDPOINT = "https://api.resend.com/emails";

    private final EmailProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    @Async
    public void send(String toEmail, String subject, String htmlBody) {
        if (!props.isEnabled()) {
            log.debug("Email skipped (no Resend key): '{}' to {}", subject, toEmail);
            return;
        }
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        try {
            String payload = mapper.writeValueAsString(Map.of(
                    "from", props.getFrom(),
                    "to", List.of(toEmail),
                    "subject", subject,
                    "html", htmlBody));

            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + props.getResendApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Resend rejected email '{}' to {} ({}): {}",
                        subject, toEmail, response.statusCode(), response.body());
            } else {
                log.info("Email sent: '{}' to {}", subject, toEmail);
            }
        } catch (Exception e) {
            // Best-effort: a delivery failure is logged, never propagated.
            log.warn("Failed to send email '{}' to {}: {}", subject, toEmail, e.getMessage());
        }
    }
}
