package com.ecoexpress.engagement.api;

import com.ecoexpress.engagement.domain.Setting;
import com.ecoexpress.engagement.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Settings")
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    public record UpdateRequest(@NotBlank String value) {}

    /** Only settings flagged public — the storefront reads delivery fee, thresholds, support info. */
    @Operation(summary = "Public settings for the storefront")
    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicSettings() {
        return ResponseEntity.ok(settingsService.publicSettings());
    }

    @Operation(summary = "All settings (admin)")
    @GetMapping
    @PreAuthorize("hasAuthority('settings:write')")
    public ResponseEntity<List<Map<String, Object>>> all() {
        List<Map<String, Object>> out = settingsService.all().stream()
                .map(this::view).toList();
        return ResponseEntity.ok(out);
    }

    @Operation(summary = "Update a setting's JSON value (admin)")
    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('settings:write')")
    public ResponseEntity<Void> update(@PathVariable String key, @Valid @RequestBody UpdateRequest r) {
        settingsService.set(key, r.value());
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> view(Setting s) {
        return Map.of("key", s.getKey(), "value", s.getValue(),
                "description", s.getDescription() == null ? "" : s.getDescription(),
                "isPublic", s.getIsPublic());
    }
}
