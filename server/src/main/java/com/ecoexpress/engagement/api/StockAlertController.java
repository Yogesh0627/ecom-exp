package com.ecoexpress.engagement.api;

import com.ecoexpress.common.security.AuthenticatedUser;
import com.ecoexpress.engagement.service.StockAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Back-in-stock alerts: a signed-in shopper subscribes to an out-of-stock variant and is notified
 * when it returns. See {@link StockAlertService}.
 */
@Tag(name = "Stock alerts")
@RestController
@RequestMapping("/api/v1/stock-alerts")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class StockAlertController {

    private final StockAlertService stockAlertService;

    public record SubscribeRequest(@NotNull UUID variantId) {}

    @Operation(summary = "Notify me when this variant is back in stock")
    @PostMapping
    public ResponseEntity<Void> subscribe(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody SubscribeRequest request) {
        stockAlertService.subscribe(user.getId(), request.variantId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cancel a back-in-stock alert")
    @DeleteMapping("/{variantId}")
    public ResponseEntity<Void> unsubscribe(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID variantId) {
        stockAlertService.unsubscribe(user.getId(), variantId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Variant ids I'm waiting on")
    @GetMapping
    public ResponseEntity<Map<String, List<UUID>>> mine(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(Map.of("variantIds", stockAlertService.activeVariantIds(user.getId())));
    }
}
