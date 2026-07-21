package com.ecoexpress.catalog.dto;

import com.ecoexpress.catalog.domain.NutritionSource;
import com.ecoexpress.catalog.domain.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response payloads for the catalog API. */
public final class CatalogDtos {

    private CatalogDtos() {}

    // ---------- responses ----------

    public record CategoryResponse(
            UUID id,
            String name,
            String slug,
            String description,
            String imageUrl,
            Integer position,
            List<CategoryResponse> children) {}

    public record BrandResponse(UUID id, String name, String slug, String logoUrl) {}

    public record ImageResponse(UUID id, String url, String alt, Integer position, boolean isPrimary) {}

    /**
     * Nutrition, per 100g. Every field is nullable: null means "not measured", which is
     * NOT the same as zero. {@code complete} tells the client whether the macros are all
     * present, so the UI can avoid presenting a partial total as a fact.
     */
    public record NutritionResponse(
            BigDecimal basisGrams,
            BigDecimal caloriesKcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbohydratesG,
            BigDecimal fiberG,
            BigDecimal sugarG,
            BigDecimal ironMg,
            BigDecimal vitaminAMcg,
            BigDecimal vitaminCMg,
            BigDecimal vitaminDMcg,
            BigDecimal potassiumMg,
            BigDecimal sodiumMg,
            NutritionSource source,
            boolean complete) {}

    public record VariantResponse(
            UUID id,
            String sku,
            String barcode,
            String name,
            BigDecimal weightGrams,
            BigDecimal mrp,
            BigDecimal price,
            /** Derived from mrp/price at read time — never stored, so it cannot drift. */
            BigDecimal discountPercent,
            String currency,
            boolean isDefault,
            boolean isActive,
            /** Units a customer can buy right now (on-hand minus reserved), summed across warehouses. */
            int availableStock,
            NutritionResponse nutrition,
            List<ImageResponse> images) {}

    public record ProductResponse(
            UUID id,
            String name,
            String slug,
            String description,
            String origin,
            boolean isOrganic,
            BigDecimal gstRatePct,
            String hsnCode,
            ProductStatus status,
            BrandResponse brand,
            CategoryResponse category,
            List<VariantResponse> variants) {}

    /**
     * Rich, AI-assisted product content (V11). Sections are prose (newline-separated lines render
     * as bullets on the storefront). The public endpoint returns this only when PUBLISHED; the
     * admin endpoint returns it in any status.
     */
    public record ProductContentResponse(
            String overview,
            String advantages,
            String healthBenefits,
            String nutrientSupport,
            String whyChoose,
            String storageTips,
            String status,
            boolean generatedByAi,
            String aiModel,
            Instant generatedAt,
            Instant publishedAt) {}

    /** Trimmed shape for listing/search: no nutrition, one image, default variant only. */
    public record ProductSummaryResponse(
            UUID id,
            String name,
            String slug,
            boolean isOrganic,
            String brandName,
            String categorySlug,
            String imageUrl,
            BigDecimal price,
            BigDecimal mrp,
            BigDecimal discountPercent,
            String sku) {}

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last) {}

    /**
     * Admin listing row. Unlike {@link ProductSummaryResponse} (storefront, ACTIVE-only) this
     * carries {@code status} and the variant count, and the admin query returns products in EVERY
     * status — so a just-created DRAFT is visible to the person who has to publish it.
     */
    public record AdminProductRow(
            UUID id,
            String name,
            String slug,
            ProductStatus status,
            boolean isOrganic,
            String categoryName,
            String sku,
            BigDecimal price,
            BigDecimal mrp,
            int variantCount) {}

    // ---------- requests ----------

    public record CreateCategoryRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                    message = "must be lowercase words separated by hyphens") String slug,
            String description,
            String imageUrl,
            UUID parentId,
            Integer position) {}

    public record CreateProductRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                    message = "must be lowercase words separated by hyphens") String slug,
            String description,
            UUID brandId,
            @NotNull UUID categoryId,
            String origin,
            Boolean isOrganic,
            /**
             * GST %. The exact-rate check (0/0.25/3/5/12/18/28) lives in the service so it
             * can return a clean 400 naming the field; the DB's products_gst_rate_chk is
             * the real guard. Bounds here just reject obvious nonsense early.
             */
            @DecimalMin("0.0") @DecimalMax("28.0") BigDecimal gstRatePct,
            /** HSN code for the invoice. */
            @Size(max = 20) String hsnCode,
            // At least one variant: a product with no variant has no price and cannot
            // be bought, so creating one is always a mistake.
            @NotNull @Size(min = 1, message = "a product needs at least one variant")
            @Valid List<CreateVariantRequest> variants) {}

    public record CreateVariantRequest(
            @NotBlank @Size(max = 64) String sku,
            String barcode,
            @NotBlank @Size(max = 120) String name,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than zero")
            BigDecimal weightGrams,
            @NotNull @DecimalMin("0.00") BigDecimal mrp,
            @NotNull @DecimalMin("0.00") BigDecimal price,
            Boolean isDefault,
            @Valid NutritionRequest nutrition,
            /** Image URLs from the upload endpoint; the first becomes the primary image. */
            List<String> imageUrls) {}

    public record NutritionRequest(
            @DecimalMin("0.0") BigDecimal caloriesKcal,
            @DecimalMin("0.0") BigDecimal proteinG,
            @DecimalMin("0.0") BigDecimal fatG,
            @DecimalMin("0.0") BigDecimal carbohydratesG,
            @DecimalMin("0.0") BigDecimal fiberG,
            @DecimalMin("0.0") BigDecimal sugarG,
            @DecimalMin("0.0") BigDecimal ironMg,
            @DecimalMin("0.0") BigDecimal vitaminAMcg,
            @DecimalMin("0.0") BigDecimal vitaminCMg,
            @DecimalMin("0.0") BigDecimal vitaminDMcg,
            @DecimalMin("0.0") BigDecimal potassiumMg,
            @DecimalMin("0.0") BigDecimal sodiumMg,
            NutritionSource source,
            String sourceRef) {}

    public record UpdateProductRequest(
            @Size(max = 200) String name,
            String description,
            UUID brandId,
            UUID categoryId,
            String origin,
            Boolean isOrganic,
            @DecimalMin("0.0") @DecimalMax("28.0") BigDecimal gstRatePct,
            @Size(max = 20) String hsnCode,
            ProductStatus status) {}

    /** Replace a product's images (on its default variant); first URL becomes the primary. */
    public record SetImagesRequest(@NotNull List<@NotBlank String> urls) {}

    /** Admin edit of the rich content sections. */
    public record UpdateContentRequest(
            @Size(max = 4000) String overview,
            @Size(max = 4000) String advantages,
            @Size(max = 4000) String healthBenefits,
            @Size(max = 4000) String nutrientSupport,
            @Size(max = 4000) String whyChoose,
            @Size(max = 4000) String storageTips) {}
}
