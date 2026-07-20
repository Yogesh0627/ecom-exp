package com.ecoexpress.ai.api;

import com.ecoexpress.ai.domain.FridgeScan;
import com.ecoexpress.ai.domain.FridgeScanItem;
import com.ecoexpress.ai.service.FridgeScanService;
import com.ecoexpress.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Smart Fridge AI (PRD §5.1). The client sends a fridge photo (base64); Gemini Vision detects
 * ingredients and the response maps them to what we sell, so the app can suggest the missing items.
 */
@Tag(name = "Smart Fridge")
@RestController
@RequestMapping("/api/v1/fridge-scans")
@RequiredArgsConstructor
public class FridgeScanController {

    private final FridgeScanService fridgeScanService;

    public record ScanRequest(
            @NotBlank String imageBase64,
            String mimeType,
            String imageUrl) {}

    public record DetectedItemView(
            String detectedName, BigDecimal confidence, String quantity,
            boolean needsConfirmation,
            // The catalog match, if any — the basis for "add missing items to cart".
            UUID matchedVariantId, String matchedProductName, BigDecimal matchedPrice) {}

    public record ScanResultView(
            String scanId, String status,
            int detectedCount, int matchedCount,
            List<DetectedItemView> items) {}

    @Operation(summary = "Scan a fridge photo and detect ingredients")
    @PostMapping
    public ResponseEntity<ScanResultView> scan(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ScanRequest r) {
        FridgeScan scan = fridgeScanService.scan(user.getId(), r.imageBase64(),
                r.mimeType() == null ? "image/jpeg" : r.mimeType(), r.imageUrl());
        return ResponseEntity.ok(view(scan));
    }

    private ScanResultView view(FridgeScan scan) {
        List<DetectedItemView> items = scan.getItems().stream().map(this::itemView).toList();
        long matched = items.stream().filter(i -> i.matchedVariantId() != null).count();
        return new ScanResultView(scan.getId().toString(), scan.getStatus().name(),
                items.size(), (int) matched, items);
    }

    private DetectedItemView itemView(FridgeScanItem i) {
        var v = i.getVariant();
        boolean needsConfirm = i.getConfidence() == null
                || i.getConfidence().compareTo(new BigDecimal("0.6")) < 0;
        return new DetectedItemView(
                i.getDetectedName(), i.getConfidence(), i.getQuantityGuess(), needsConfirm,
                v == null ? null : v.getId(),
                v == null ? null : v.getProduct().getName(),
                v == null ? null : v.getPrice());
    }
}
