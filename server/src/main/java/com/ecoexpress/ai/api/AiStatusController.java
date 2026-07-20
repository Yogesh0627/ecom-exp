package com.ecoexpress.ai.api;

import com.ecoexpress.ai.service.AiStatusService;
import com.ecoexpress.ai.service.AiStatusService.AiStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live AI availability for the storefront — lets the UI show "AI is over its limit, try again in
 * ~Ns" instead of a generic error. Public and dependency-free (like the readiness probe).
 */
@Tag(name = "AI status")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiStatusController {

    private final AiStatusService aiStatusService;

    @Operation(summary = "Whether AI features are currently available (quota/rate-limit aware)")
    @GetMapping("/status")
    public ResponseEntity<AiStatus> status() {
        return ResponseEntity.ok(aiStatusService.status());
    }
}
