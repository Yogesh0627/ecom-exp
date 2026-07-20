package com.ecoexpress.engagement.api;

import com.ecoexpress.engagement.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Admin analytics dashboard (PRD §6). Requires analytics:read. */
@Tag(name = "Admin Dashboard")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Dashboard summary: revenue, order counts, and things needing attention")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(dashboardService.summary());
    }

    @Operation(summary = "Top-selling products")
    @GetMapping("/top-products")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<List<Map<String, Object>>> topProducts(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.topProducts(limit));
    }
}
