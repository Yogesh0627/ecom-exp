package com.ecoexpress.catalog.api;

import com.ecoexpress.catalog.dto.CatalogDtos.CreateProductRequest;
import com.ecoexpress.catalog.dto.CatalogDtos.PageResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.ProductResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.ProductSummaryResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.SetImagesRequest;
import com.ecoexpress.catalog.dto.CatalogDtos.UpdateProductRequest;
import com.ecoexpress.catalog.service.ProductService;
import com.ecoexpress.catalog.service.ProductService.SimilarProduct;
import com.ecoexpress.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Products.
 *
 * <p>Reads are public (PRD §10 — browse before login). Writes require a specific
 * permission, checked with @PreAuthorize against the JWT's authorities. Note these bind
 * to permissions, not roles, so re-cutting roles later needs no change here.
 */
@Tag(name = "Products")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final InventoryService inventoryService;

    @Operation(summary = "Search or list products")
    @GetMapping
    public ResponseEntity<PageResponse<ProductSummaryResponse>> search(
            @RequestParam(required = false) String q,
            // Capped by PageableDefault: an unbounded ?size=100000 is a trivial DoS.
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.search(q, pageable));
    }

    @Operation(summary = "List products in a category, including its sub-categories")
    @GetMapping("/category/{slug}")
    public ResponseEntity<PageResponse<ProductSummaryResponse>> byCategory(
            @PathVariable String slug,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.listByCategory(slug, pageable));
    }

    @Operation(summary = "Get a product by slug")
    @GetMapping("/{slug}")
    public ResponseEntity<ProductResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(productService.getBySlug(slug));
    }

    @Operation(summary = "Similar products (\"you may also like\") — same category, in stock")
    @GetMapping("/{slug}/similar")
    public ResponseEntity<List<ProductSummaryResponse>> similar(
            @PathVariable String slug,
            @RequestParam(defaultValue = "8") int limit) {
        // Stock filtering lives here (the controller has InventoryService; the catalog service
        // stays free of the inventory module). Never recommend something a shopper cannot buy.
        List<ProductSummaryResponse> out = productService.similar(slug, limit).stream()
                .filter(s -> inventoryService.availableFor(s.defaultVariantId()) > 0)
                .map(SimilarProduct::product)
                .limit(limit)
                .toList();
        return ResponseEntity.ok(out);
    }

    @Operation(summary = "Create a product with its variants")
    @PostMapping
    @PreAuthorize("hasAuthority('product:write')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @Operation(summary = "Update a product")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('product:write')")
    public ResponseEntity<ProductResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @Operation(summary = "Replace a product's images (admin)")
    @org.springframework.web.bind.annotation.PutMapping("/{slug}/images")
    @PreAuthorize("hasAuthority('product:write')")
    public ResponseEntity<ProductResponse> setImages(
            @PathVariable String slug,
            @Valid @RequestBody SetImagesRequest request) {
        return ResponseEntity.ok(productService.replaceImages(slug, request.urls()));
    }

    @Operation(summary = "Soft-delete a product")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('product:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
