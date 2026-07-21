package com.ecoexpress.catalog.mapper;

import com.ecoexpress.catalog.domain.Brand;
import com.ecoexpress.catalog.domain.Category;
import com.ecoexpress.catalog.domain.NutritionFacts;
import com.ecoexpress.catalog.domain.Product;
import com.ecoexpress.catalog.domain.ProductContent;
import com.ecoexpress.catalog.domain.ProductImage;
import com.ecoexpress.catalog.domain.ProductVariant;
import com.ecoexpress.catalog.dto.CatalogDtos.AdminProductRow;
import com.ecoexpress.catalog.dto.CatalogDtos.BrandResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.CategoryResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.ImageResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.NutritionResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.ProductContentResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.ProductResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.ProductSummaryResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.VariantResponse;
import com.ecoexpress.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Entity -> DTO mapping.
 *
 * <p>Hand-written rather than MapStruct-generated: several fields are computed
 * ({@code discountPercent}, {@code complete}) or flattened from a nested default
 * variant, which MapStruct would need expression annotations for anyway. Straight Java
 * is easier to read and to debug than a generated class full of qualifiers.
 */
@Component
@RequiredArgsConstructor
public class CatalogMapper {

    private final InventoryService inventoryService;

    public CategoryResponse toCategory(Category c, boolean withChildren) {
        if (c == null) {
            return null;
        }
        List<CategoryResponse> children = withChildren
                ? c.getChildren().stream()
                    .filter(ch -> Boolean.TRUE.equals(ch.getIsActive()))
                    .map(ch -> toCategory(ch, false))
                    .toList()
                : List.of();
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(),
                c.getImageUrl(), c.getPosition(), children);
    }

    public BrandResponse toBrand(Brand b) {
        return b == null ? null
                : new BrandResponse(b.getId(), b.getName(), b.getSlug(), b.getLogoUrl());
    }

    public ImageResponse toImage(ProductImage i) {
        return new ImageResponse(i.getId(), i.getUrl(), i.getAlt(), i.getPosition(),
                Boolean.TRUE.equals(i.getIsPrimary()));
    }

    public NutritionResponse toNutrition(NutritionFacts n) {
        if (n == null) {
            return null;
        }
        return new NutritionResponse(
                n.getBasisGrams(), n.getCaloriesKcal(), n.getProteinG(), n.getFatG(),
                n.getCarbohydratesG(), n.getFiberG(), n.getSugarG(), n.getIronMg(),
                n.getVitaminAMcg(), n.getVitaminCMg(), n.getVitaminDMcg(),
                n.getPotassiumMg(), n.getSodiumMg(), n.getSource(), n.isComplete());
    }

    public VariantResponse toVariant(ProductVariant v) {
        return new VariantResponse(
                v.getId(), v.getSku(), v.getBarcode(), v.getName(), v.getWeightGrams(),
                v.getMrp(), v.getPrice(), v.discountPercent(), v.getCurrency(),
                Boolean.TRUE.equals(v.getIsDefault()), Boolean.TRUE.equals(v.getIsActive()),
                inventoryService.availableFor(v.getId()),
                toNutrition(v.getNutritionFacts()),
                v.getImages().stream().map(this::toImage).toList());
    }

    public ProductContentResponse toContent(ProductContent c) {
        if (c == null) {
            return null;
        }
        return new ProductContentResponse(
                c.getOverview(), c.getAdvantages(), c.getHealthBenefits(), c.getNutrientSupport(),
                c.getWhyChoose(), c.getStorageTips(), c.getStatus().name(),
                Boolean.TRUE.equals(c.getGeneratedByAi()), c.getAiModel(),
                c.getGeneratedAt(), c.getPublishedAt());
    }

    public ProductResponse toProduct(Product p) {
        return new ProductResponse(
                p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getOrigin(),
                Boolean.TRUE.equals(p.getIsOrganic()), p.getGstRatePct(), p.getHsnCode(),
                p.getStatus(),
                toBrand(p.getBrand()), toCategory(p.getCategory(), false),
                p.getVariants().stream().map(this::toVariant).toList());
    }

    /**
     * Listing shape: flattens the default variant's price onto the card.
     *
     * <p>Tolerates a product with no variants (returns nulls rather than throwing) —
     * the API rejects creating one, but a bad row must not break the whole listing page.
     */
    public ProductSummaryResponse toSummary(Product p) {
        ProductVariant v = p.defaultVariant();
        ProductImage img = v == null ? null : v.primaryImage();
        return new ProductSummaryResponse(
                p.getId(),
                p.getName(),
                p.getSlug(),
                Boolean.TRUE.equals(p.getIsOrganic()),
                p.getBrand() == null ? null : p.getBrand().getName(),
                p.getCategory() == null ? null : p.getCategory().getSlug(),
                img == null ? null : img.getUrl(),
                v == null ? null : v.getPrice(),
                v == null ? null : v.getMrp(),
                v == null ? BigDecimal.ZERO : v.discountPercent(),
                v == null ? null : v.getSku());
    }

    /** Admin listing row: status-aware, includes the variant count and default price. */
    public AdminProductRow toAdminRow(Product p) {
        ProductVariant v = p.defaultVariant();
        return new AdminProductRow(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getStatus(),
                Boolean.TRUE.equals(p.getIsOrganic()),
                p.getCategory() == null ? null : p.getCategory().getName(),
                v == null ? null : v.getSku(),
                v == null ? null : v.getPrice(),
                v == null ? null : v.getMrp(),
                p.getVariants().size());
    }
}
