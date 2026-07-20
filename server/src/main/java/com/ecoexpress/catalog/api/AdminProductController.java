package com.ecoexpress.catalog.api;

import com.ecoexpress.catalog.domain.ProductStatus;
import com.ecoexpress.catalog.dto.CatalogDtos.AdminProductRow;
import com.ecoexpress.catalog.dto.CatalogDtos.PageResponse;
import com.ecoexpress.catalog.repository.ProductVariantRepository;
import com.ecoexpress.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Admin product listing (PRD §6). Separate from the public {@link ProductController} because it
 * returns products in EVERY status — the storefront search is deliberately ACTIVE-only, which would
 * hide a manager's own DRAFT the moment they created it.
 */
@Tag(name = "Admin Products")
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;
    private final ProductVariantRepository variantRepository;

    /** A variant option for admin pickers (stock receipts, purchase orders). */
    public record VariantOption(UUID variantId, String sku, String productName,
                                String variantName, BigDecimal price) {}

    @Operation(summary = "List products across all statuses, for management")
    @GetMapping
    @PreAuthorize("hasAuthority('product:write')")
    public ResponseEntity<PageResponse<AdminProductRow>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProductStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.adminList(q, status, pageable));
    }

    /**
     * Variant search for pickers — receive-stock and purchase-order lines need to select a variant
     * by SKU or product name. Gated on inventory:read since that is who uses it (stocking staff),
     * not only catalog managers.
     */
    @Operation(summary = "Search variants by SKU or product name (for stock/PO pickers)")
    @GetMapping("/variants")
    @PreAuthorize("hasAuthority('inventory:read')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<VariantOption>> searchVariants(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        List<VariantOption> options = variantRepository
                .searchForAdmin(q.trim(), PageRequest.of(0, 20)).stream()
                .map(v -> new VariantOption(v.getId(), v.getSku(),
                        v.getProduct().getName(), v.getName(), v.getPrice()))
                .toList();
        return ResponseEntity.ok(options);
    }
}
