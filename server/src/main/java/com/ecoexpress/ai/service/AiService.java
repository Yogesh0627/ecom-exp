package com.ecoexpress.ai.service;

import com.ecoexpress.ai.client.AiClient;
import com.ecoexpress.ai.config.AiProperties;
import com.ecoexpress.ai.domain.AiFeature;
import com.ecoexpress.ai.domain.AiRequestStatus;
import com.ecoexpress.ai.exception.AiException;
import com.ecoexpress.ai.repository.AiRequestLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * The single doorway for every AI call in the app.
 *
 * <p>Features never call {@link AiClient} directly — they go through here, and this class owns the
 * three concerns that must be handled the same way everywhere:
 * <ol>
 *   <li><b>Budget.</b> Before any call it checks month-to-date spend against
 *       {@code ecoexpress.ai.monthly-budget-inr} and refuses once the cap is hit. AI on the hot
 *       path of five features is exactly where a runaway cost hides.</li>
 *   <li><b>Accounting.</b> Every call — success or failure — writes an {@code ai_request_logs}
 *       row with tokens, latency, and an estimated cost, so spend is visible before the bill.</li>
 *   <li><b>Failure shape.</b> Provider errors are classified (RATE_LIMITED vs FAILED vs TIMEOUT)
 *       and surfaced as a 503, never a 500 — the AI is a dependency, not the server.</li>
 * </ol>
 *
 * <p>The log write uses REQUIRES_NEW so accounting survives even when the calling feature's
 * transaction rolls back — a failed fridge scan must still record that we paid for the call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiClient client;
    private final AiProperties props;
    private final AiRequestLogRepository logRepository;
    private final AiRequestLogger requestLogger;
    private final AiStatusService aiStatusService;

    /** A completed AI call: the model's text plus the accounting that was logged. */
    public record AiResult(String text, int tokensIn, int tokensOut, BigDecimal costInr) {}

    public boolean isEnabled() {
        return client.isAvailable();
    }

    /** Text generation, metered and logged. */
    public AiResult generateText(AiFeature feature, UUID userId, String systemInstruction,
                                 String prompt, boolean jsonOutput, String refType, UUID refId) {
        return run(feature, userId, refType, refId,
                () -> client.generateText(systemInstruction, prompt, jsonOutput));
    }

    /** Vision generation (image + prompt), metered and logged. */
    public AiResult generateFromImage(AiFeature feature, UUID userId, String prompt,
                                      AiClient.ImageInput image, boolean jsonOutput,
                                      String refType, UUID refId) {
        return run(feature, userId, refType, refId,
                () -> client.generateFromImage(prompt, image, jsonOutput));
    }

    private AiResult run(AiFeature feature, UUID userId, String refType, UUID refId,
                         java.util.function.Supplier<AiClient.AiTextResult> call) {
        if (!client.isAvailable()) {
            throw new AiException("AI features are not enabled (no API key configured).");
        }
        requireBudget(feature);

        long start = System.nanoTime();
        try {
            AiClient.AiTextResult r = call.get();
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            BigDecimal cost = estimateCost(r.tokensIn(), r.tokensOut());
            // Separate bean => a real REQUIRES_NEW transaction (see AiRequestLogger).
            requestLogger.record(feature, userId, refType, refId, r.tokensIn(), r.tokensOut(),
                    (int) latencyMs, cost, AiRequestStatus.SUCCESS, null);
            return new AiResult(r.text(), r.tokensIn(), r.tokensOut(), cost);

        } catch (AiException e) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            AiRequestStatus status = e.isRateLimited() ? AiRequestStatus.RATE_LIMITED
                    : AiRequestStatus.FAILED;
            // Remember a rate-limit/quota rejection so /ai/status can warn users right away.
            if (e.isRateLimited()) {
                aiStatusService.markRateLimited(e.getRetryAfterSeconds(), e.getMessage());
            }
            // A failed call still cost latency (and sometimes tokens); record it so a failing
            // integration is visible in the same place as spend, not just in error logs.
            requestLogger.record(feature, userId, refType, refId, null, null, (int) latencyMs, null,
                    status, e.getMessage());
            throw e;
        }
    }

    /**
     * Refuses the call if this month's spend has hit the budget. Fail-closed: if the budget is
     * configured to zero or the spend query errors, we do NOT silently allow unlimited spend.
     */
    private void requireBudget(AiFeature feature) {
        BigDecimal budget = props.monthlyBudgetInr();
        if (budget == null || budget.signum() <= 0) {
            throw new AiException("AI spending budget is not configured.");
        }
        BigDecimal spent = logRepository.spendSince(monthStart());
        if (spent.compareTo(budget) >= 0) {
            log.error("AI monthly budget reached ({} of {} INR) — refusing {} call",
                    spent, budget, feature);
            throw new AiException("This month's AI budget has been reached. Please try later.");
        }
    }

    /** Estimated rupee cost from token counts and the configured per-1k pricing. */
    private BigDecimal estimateCost(int tokensIn, int tokensOut) {
        BigDecimal in = props.costPer1kInputInr()
                .multiply(BigDecimal.valueOf(tokensIn)).divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        BigDecimal out = props.costPer1kOutputInr()
                .multiply(BigDecimal.valueOf(tokensOut)).divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        return in.add(out).setScale(4, RoundingMode.HALF_UP);
    }

    private Instant monthStart() {
        return Instant.now().atZone(ZoneId.of("Asia/Kolkata"))
                .toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneId.of("Asia/Kolkata"))
                .toInstant().truncatedTo(ChronoUnit.SECONDS);
    }
}
