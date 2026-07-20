package com.ecoexpress.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * AI provider settings, from {@code ecoexpress.ai.*}.
 *
 * @param apiKey        Gemini API key. When blank, AI is disabled and features fall back cleanly.
 * @param model         text/vision model id (e.g. gemini-2.0-flash)
 * @param baseUrl       Gemini generativelanguage endpoint
 * @param timeoutMs     per-call timeout — an AI call must never hang a request thread indefinitely
 * @param monthlyBudgetInr hard cap on spend per calendar month; calls are refused once reached
 * @param costPer1kInputInr / costPer1kOutputInr rough token pricing, for the cost estimate
 */
@ConfigurationProperties(prefix = "ecoexpress.ai")
public record AiProperties(
        String apiKey,
        String model,
        String baseUrl,
        Integer timeoutMs,
        BigDecimal monthlyBudgetInr,
        BigDecimal costPer1kInputInr,
        BigDecimal costPer1kOutputInr) {

    public AiProperties {
        if (model == null || model.isBlank()) {
            model = "gemini-2.0-flash";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        }
        if (timeoutMs == null || timeoutMs <= 0) {
            timeoutMs = 20_000;
        }
        if (monthlyBudgetInr == null) {
            monthlyBudgetInr = new BigDecimal("5000");
        }
        // Rough Gemini Flash pricing converted to INR; only used for the spend estimate, so an
        // approximate figure is fine — the point is a visible trend, not accounting to the paisa.
        if (costPer1kInputInr == null) {
            costPer1kInputInr = new BigDecimal("0.006");
        }
        if (costPer1kOutputInr == null) {
            costPer1kOutputInr = new BigDecimal("0.024");
        }
    }

    /** AI is live only when a key is present. Everything downstream checks this. */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
