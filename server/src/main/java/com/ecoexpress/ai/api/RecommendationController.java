package com.ecoexpress.ai.api;

import com.ecoexpress.ai.domain.RecommendationRule;
import com.ecoexpress.ai.domain.RecommendationType;
import com.ecoexpress.ai.repository.RecommendationRuleRepository;
import com.ecoexpress.ai.service.RecommendationService;
import com.ecoexpress.ai.service.RecommendationService.Recommendation;
import com.ecoexpress.catalog.repository.CategoryRepository;
import com.ecoexpress.catalog.repository.ProductVariantRepository;
import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Intelligent recommendations (PRD §5.3). Reads are public (they drive "goes well with" on the
 * product page); creating rules is admin-only.
 */
@Tag(name = "Recommendations")
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final RecommendationRuleRepository ruleRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryService inventoryService;

    public record CreateRuleRequest(
            UUID triggerVariantId,
            UUID triggerCategoryId,
            @NotNull UUID suggestedVariantId,
            RecommendationType ruleType,
            BigDecimal weight,
            String reason) {}

    @Operation(summary = "Recommendations for a variant (\"goes well with\")")
    @GetMapping("/variant/{variantId}")
    public ResponseEntity<List<Recommendation>> forVariant(
            @PathVariable UUID variantId,
            @RequestParam(defaultValue = "8") int limit,
            @RequestParam(defaultValue = "false") boolean includeOutOfStock) {
        List<Recommendation> recs = recommendationService.forVariant(variantId, limit * 2);
        // Stock filtering lives here — the controller has InventoryService, the rule engine does
        // not (module boundary). Drop suggestions with no available units unless asked to keep them.
        List<Recommendation> filtered = recs.stream()
                .filter(r -> includeOutOfStock || inventoryService.availableFor(r.variantId()) > 0)
                .limit(limit)
                .toList();
        return ResponseEntity.ok(filtered);
    }

    @Operation(summary = "Create a recommendation rule (admin)")
    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('product:write')")
    public ResponseEntity<Map<String, Object>> createRule(@Valid @RequestBody CreateRuleRequest r) {
        // Mirrors rec_trigger_chk: exactly one trigger. Checked here for a clean 400.
        boolean hasVariant = r.triggerVariantId() != null;
        boolean hasCategory = r.triggerCategoryId() != null;
        if (hasVariant == hasCategory) {
            throw new BadRequestException(
                    "A rule must trigger on exactly one of a variant or a category.");
        }
        if (hasVariant && r.triggerVariantId().equals(r.suggestedVariantId())) {
            throw new BadRequestException("A rule cannot recommend a product to itself.");
        }

        var suggested = variantRepository.findById(r.suggestedVariantId())
                .orElseThrow(() -> new NotFoundException("No variant " + r.suggestedVariantId()));

        RecommendationRule.RecommendationRuleBuilder builder = RecommendationRule.builder()
                .suggestedVariant(suggested)
                .ruleType(r.ruleType() == null ? RecommendationType.COMPLEMENT : r.ruleType())
                .weight(r.weight() == null ? BigDecimal.ONE : r.weight())
                .reason(r.reason())
                .isActive(true);

        if (hasVariant) {
            builder.triggerVariant(variantRepository.findById(r.triggerVariantId())
                    .orElseThrow(() -> new NotFoundException("No variant " + r.triggerVariantId())));
        } else {
            builder.triggerCategory(categoryRepository.findById(r.triggerCategoryId())
                    .orElseThrow(() -> new NotFoundException("No category " + r.triggerCategoryId())));
        }

        RecommendationRule saved = ruleRepository.save(builder.build());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", saved.getId(), "ruleType", saved.getRuleType().name()));
    }
}
