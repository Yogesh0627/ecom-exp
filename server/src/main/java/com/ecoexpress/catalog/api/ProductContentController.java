package com.ecoexpress.catalog.api;

import com.ecoexpress.catalog.domain.ProductContent;
import com.ecoexpress.catalog.dto.CatalogDtos.ProductContentResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.UpdateContentRequest;
import com.ecoexpress.catalog.mapper.CatalogMapper;
import com.ecoexpress.catalog.service.ProductContentService;
import com.ecoexpress.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rich product content (V11): AI-drafted, human-approved.
 *
 * <p>The public read returns content only when PUBLISHED. Everything else is admin-only: generating
 * a draft (an AI call), editing it, and publishing/unpublishing. Served on its own path (not folded
 * into the cached product-detail response) so publishing takes effect immediately.
 */
@Tag(name = "Product content")
@RestController
@RequiredArgsConstructor
public class ProductContentController {

    private final ProductContentService contentService;
    private final CatalogMapper mapper;

    @Operation(summary = "Published rich content for a product (public); 204 if none")
    @GetMapping("/api/v1/products/{slug}/content")
    public ResponseEntity<ProductContentResponse> published(@PathVariable String slug) {
        return contentService.getForProduct(slug)
                .filter(ProductContent::isPublished)
                .map(c -> ResponseEntity.ok(mapper.toContent(c)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Current content in any status (admin)")
    @GetMapping("/api/v1/admin/products/{slug}/content")
    @PreAuthorize("hasAuthority('product:write')")
    public ResponseEntity<ProductContentResponse> adminGet(@PathVariable String slug) {
        return contentService.getForProduct(slug)
                .map(c -> ResponseEntity.ok(mapper.toContent(c)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Generate an AI draft, grounded in the product's real facts (admin)")
    @PostMapping("/api/v1/admin/products/{slug}/content/generate")
    @PreAuthorize("hasAuthority('product:write')")
    public ResponseEntity<ProductContentResponse> generate(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        ProductContent draft = contentService.generateDraft(slug, admin == null ? null : admin.getId());
        return ResponseEntity.ok(mapper.toContent(draft));
    }

    @Operation(summary = "Edit content sections (admin)")
    @PutMapping("/api/v1/admin/products/{slug}/content")
    @PreAuthorize("hasAuthority('product:write')")
    public ResponseEntity<ProductContentResponse> update(
            @PathVariable String slug,
            @Valid @RequestBody UpdateContentRequest req) {
        ProductContent edits = ProductContent.builder()
                .overview(req.overview())
                .advantages(req.advantages())
                .healthBenefits(req.healthBenefits())
                .nutrientSupport(req.nutrientSupport())
                .whyChoose(req.whyChoose())
                .storageTips(req.storageTips())
                .build();
        return ResponseEntity.ok(mapper.toContent(contentService.update(slug, edits)));
    }

    @Operation(summary = "Publish content so shoppers see it (admin)")
    @PostMapping("/api/v1/admin/products/{slug}/content/publish")
    @PreAuthorize("hasAuthority('product:write')")
    public ResponseEntity<ProductContentResponse> publish(@PathVariable String slug) {
        return ResponseEntity.ok(mapper.toContent(contentService.publish(slug)));
    }

    @Operation(summary = "Unpublish content back to draft (admin)")
    @PostMapping("/api/v1/admin/products/{slug}/content/unpublish")
    @PreAuthorize("hasAuthority('product:write')")
    public ResponseEntity<ProductContentResponse> unpublish(@PathVariable String slug) {
        return ResponseEntity.ok(mapper.toContent(contentService.unpublish(slug)));
    }
}
