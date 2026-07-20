package com.ecoexpress.ai.api;

import com.ecoexpress.ai.config.AiProperties;
import com.ecoexpress.ai.repository.AiRequestLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI spend dashboard (PRD §6). Surfaces month-to-date Gemini spend against the configured budget,
 * broken down by feature — the same accounting {@code AiService} writes on every call, aggregated
 * for the admin so the bill never arrives as a surprise.
 */
@Tag(name = "Admin AI Spend")
@RestController
@RequestMapping("/api/v1/admin/ai-spend")
@RequiredArgsConstructor
public class AiSpendController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AiRequestLogRepository logRepository;
    private final AiProperties props;

    @Operation(summary = "Month-to-date AI spend vs budget, by feature")
    @GetMapping
    @PreAuthorize("hasAuthority('analytics:read')")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> summary() {
        Instant monthStart = Instant.now().atZone(IST).toLocalDate()
                .withDayOfMonth(1).atStartOfDay(IST).toInstant().truncatedTo(ChronoUnit.SECONDS);

        BigDecimal spend = logRepository.spendSince(monthStart);
        long calls = logRepository.countSince(monthStart);
        BigDecimal budget = props.monthlyBudgetInr();

        List<Map<String, Object>> byFeature = new ArrayList<>();
        for (Object[] row : logRepository.usageByFeatureSince(monthStart)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("feature", String.valueOf(row[0]));
            m.put("calls", ((Number) row[1]).longValue());
            m.put("tokensIn", ((Number) row[2]).longValue());
            m.put("tokensOut", ((Number) row[3]).longValue());
            m.put("costInr", row[4]);
            byFeature.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("monthSpendInr", spend);
        out.put("budgetInr", budget);
        out.put("totalCalls", calls);
        out.put("byFeature", byFeature);
        return ResponseEntity.ok(out);
    }
}
