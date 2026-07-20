package com.ecoexpress.common.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A tiny public readiness ping the storefront polls on load. Under {@code /api/v1} (so it is
 * CORS-enabled, unlike {@code /actuator/**}) and dependency-free, so it answers the instant the app
 * is serving — which on a free host means "the container has finished cold-starting". The UI shows a
 * red→green indicator based on this.
 */
@Tag(name = "Health")
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @Operation(summary = "Readiness ping (public) — 200 once the app is serving")
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        return ResponseEntity.ok(Map.of("status", "ready", "at", System.currentTimeMillis()));
    }
}
