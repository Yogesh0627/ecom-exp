package com.ecoexpress.ai.service;

import com.ecoexpress.ai.domain.AiFeature;
import com.ecoexpress.ai.domain.AiRequestLog;
import com.ecoexpress.ai.domain.AiRequestStatus;
import com.ecoexpress.ai.config.AiProperties;
import com.ecoexpress.ai.repository.AiRequestLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Writes the AI accounting row in its OWN transaction.
 *
 * <p>A separate bean, not a method on {@link AiService}, for the same reason
 * {@code TokenRevocationService} is separate: a {@code REQUIRES_NEW} method invoked as
 * {@code this.method()} inside the same class bypasses Spring's proxy and silently runs in the
 * caller's transaction. Then a rollback in the calling feature (a failed fridge scan) would
 * discard the cost record — under-reporting spend exactly when something is failing. Calling it
 * as a distinct bean makes the new transaction real, so the accounting commits regardless.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRequestLogger {

    private final AiRequestLogRepository logRepository;
    private final AiProperties props;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AiFeature feature, UUID userId, String refType, UUID refId,
                       Integer tokensIn, Integer tokensOut, Integer latencyMs, BigDecimal cost,
                       AiRequestStatus status, String error) {
        try {
            logRepository.save(AiRequestLog.builder()
                    .userId(userId)
                    .feature(feature)
                    .model(props.model())
                    .provider("GEMINI")
                    .tokensIn(tokensIn)
                    .tokensOut(tokensOut)
                    .latencyMs(latencyMs)
                    .costInr(cost)
                    .status(status)
                    .error(error == null ? null : error.substring(0, Math.min(error.length(), 500)))
                    .refType(refType)
                    .refId(refId)
                    .build());
        } catch (Exception e) {
            // A logging failure must never mask the real AI result. Log and move on.
            log.error("Failed to write ai_request_logs row for {}", feature, e);
        }
    }
}
