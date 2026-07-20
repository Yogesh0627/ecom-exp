package com.ecoexpress.catalog.service;

import com.ecoexpress.catalog.domain.Brand;
import com.ecoexpress.catalog.domain.Category;
import com.ecoexpress.catalog.domain.NutritionFacts;
import com.ecoexpress.catalog.domain.Product;
import com.ecoexpress.catalog.domain.ProductStatus;
import com.ecoexpress.catalog.domain.ProductVariant;
import com.ecoexpress.catalog.dto.CatalogDtos.AdminProductRow;
import com.ecoexpress.catalog.dto.CatalogDtos.CreateProductRequest;
import com.ecoexpress.catalog.dto.CatalogDtos.CreateVariantRequest;
import com.ecoexpress.catalog.dto.CatalogDtos.NutritionRequest;
import com.ecoexpress.catalog.dto.CatalogDtos.PageResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.ProductResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.ProductSummaryResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.UpdateProductRequest;
import com.ecoexpress.catalog.mapper.CatalogMapper;
import com.ecoexpress.catalog.repository.BrandRepository;
import com.ecoexpress.catalog.repository.CategoryRepository;
import com.ecoexpress.catalog.repository.ProductRepository;
import com.ecoexpress.catalog.repository.ProductVariantRepository;
import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.common.exception.ApiExceptions.ConflictException;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    /** Mirrors products_gst_rate_chk in V8. */
    private static final List<BigDecimal> ALLOWED_GST_RATES = List.of(
            new BigDecimal("0"), new BigDecimal("0.25"), new BigDecimal("3"),
            new BigDecimal("5"), new BigDecimal("12"), new BigDecimal("18"),
            new BigDecimal("28"));

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final CatalogMapper mapper;
    private final jakarta.persistence.EntityManager entityManager;

    /**
     * Product detail by slug.
     *
     * <p>Cached: the product page is the hottest read in the app and the catalog changes
     * rarely. The cache is provider-agnostic — Caffeine locally, Upstash Redis in prod.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#slug")
    public ProductResponse getBySlug(String slug) {
        Product p = productRepository.findBySlugWithDetails(slug)
                .orElseThrow(() -> new NotFoundException("No product with slug '" + slug + "'."));
        return mapper.toProduct(p);
    }

    /** A candidate similar product plus its default variant id, so the caller can stock-filter. */
    public record SimilarProduct(ProductSummaryResponse product, UUID defaultVariantId) {}

    /**
     * Automatic "you may also like": active products in the same category, ranked by organic
     * status then discount depth then name. Deliberately does NOT filter stock — that's the
     * controller's job (module boundary; the RecommendationController does the same), so this stays
     * free of the inventory module. Returns more than asked so the controller can drop out-of-stock
     * ones and still fill the row.
     */
    @Transactional(readOnly = true)
    public List<SimilarProduct> similar(String slug, int limit) {
        Product product = productRepository.findBySlugWithDetails(slug)
                .orElseThrow(() -> new NotFoundException("No product with slug '" + slug + "'."));
        if (product.getCategory() == null) {
            return List.of();
        }
        int fetch = Math.max(limit * 3, 12);
        List<Product> candidates = productRepository.findActiveInCategoryExcluding(
                product.getCategory().getId(), product.getId(),
                org.springframework.data.domain.PageRequest.of(0, fetch));

        return candidates.stream()
                .sorted(SIMILAR_ORDER)
                .map(p -> {
                    ProductVariant def = p.defaultVariant();
                    return def == null ? null
                            : new SimilarProduct(mapper.toSummary(p), def.getId());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** Organic first, then bigger discount, then name — a sensible default "relevance". */
    private static final java.util.Comparator<Product> SIMILAR_ORDER =
            java.util.Comparator
                    .comparing((Product p) -> Boolean.TRUE.equals(p.getIsOrganic()) ? 0 : 1)
                    .thenComparing(p -> {
                        ProductVariant v = p.defaultVariant();
                        // Higher discount first => negate.
                        return v == null ? BigDecimal.ZERO.negate() : v.discountPercent().negate();
                    })
                    .thenComparing(Product::getName);

    /**
     * Replaces the images on a product's default variant. First URL becomes the primary (the card
     * and gallery lead image). Evicts the products cache so the new images show immediately.
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse replaceImages(String slug, List<String> urls) {
        Product product = productRepository.findBySlugWithDetails(slug)
                .orElseThrow(() -> new NotFoundException("No product with slug '" + slug + "'."));
        ProductVariant variant = product.defaultVariant();
        if (variant == null) {
            throw new BadRequestException("Product has no variant to attach images to.");
        }
        // orphanRemoval on the collection deletes the old rows; add fresh ones in order.
        // Flush the DELETEs before inserting: the product_images_one_primary unique index would
        // otherwise reject the new primary while the old primary row still exists (Hibernate orders
        // inserts before deletes within a single flush).
        variant.getImages().clear();
        entityManager.flush();
        int position = 0;
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            variant.getImages().add(com.ecoexpress.catalog.domain.ProductImage.builder()
                    .variant(variant)
                    .url(url.trim())
                    .position(position)
                    .isPrimary(position == 0)
                    .build());
            position++;
        }
        log.info("Replaced images for {} ({} image(s))", slug, position);
        return mapper.toProduct(product);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> search(String query, Pageable pageable) {
        Page<Product> page = (query == null || query.isBlank())
                ? productRepository.findByStatus(ProductStatus.ACTIVE, pageable)
                : productRepository.search(query, pageable);
        return toPageResponse(page.map(mapper::toSummary));
    }

    /**
     * Admin listing: EVERY status (not just ACTIVE), optionally filtered by a name fragment and/or
     * a status. Soft-deleted rows are excluded by the entity's @SQLRestriction. This is the view a
     * catalog manager needs — a DRAFT they just created must be visible so they can publish it.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminProductRow> adminList(String query, ProductStatus status,
                                                   Pageable pageable) {
        Page<Product> page = productRepository.findAll((r, q, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (query != null && !query.isBlank()) {
                predicates.add(cb.like(cb.lower(r.get("name")), "%" + query.toLowerCase().trim() + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(r.get("status"), status));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, pageable);
        return toPageResponse(page.map(mapper::toAdminRow));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> listByCategory(String categorySlug, Pageable pageable) {
        Category root = categoryRepository.findBySlug(categorySlug)
                .orElseThrow(() -> new NotFoundException("No category '" + categorySlug + "'."));

        // Browsing "Fruits" must include everything filed under its children.
        List<UUID> ids = categoryRepository.findSubtreeIds(root.getId());
        Page<Product> page = productRepository.findAll(
                (r, q, cb) -> cb.and(
                        r.get("category").get("id").in(ids),
                        cb.equal(r.get("status"), ProductStatus.ACTIVE)),
                pageable);
        return toPageResponse(page.map(mapper::toSummary));
    }

    /**
     * Creates a product and its variants.
     *
     * <p>Evicts the whole products cache: a new product can appear in listings, and
     * scoping the eviction to one key would leave stale pages behind.
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsBySlug(request.slug())) {
            throw new ConflictException("A product with slug '" + request.slug() + "' exists.");
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("No category " + request.categoryId()));

        Brand brand = null;
        if (request.brandId() != null) {
            brand = brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new NotFoundException("No brand " + request.brandId()));
        }

        Product product = Product.builder()
                .name(request.name())
                .slug(request.slug())
                .description(request.description())
                .brand(brand)
                .category(category)
                .origin(request.origin())
                .isOrganic(request.isOrganic() == null || request.isOrganic())
                // Default to nil-rated: most of this catalog is fresh unbranded produce,
                // which is 0% GST. Packaged goods set a rate explicitly.
                .gstRatePct(validGstRate(request.gstRatePct()))
                .hsnCode(request.hsnCode())
                .status(ProductStatus.DRAFT)
                .build();

        validateVariants(request.variants());

        for (CreateVariantRequest vr : request.variants()) {
            if (variantRepository.existsBySku(vr.sku())) {
                throw new ConflictException("SKU '" + vr.sku() + "' is already in use.");
            }
            product.getVariants().add(buildVariant(product, vr));
        }

        // Exactly one default — the storefront card has to show a price.
        ensureSingleDefault(product);

        Product saved = productRepository.save(product);
        log.info("Created product {} ({}) with {} variant(s)",
                saved.getSlug(), saved.getId(), saved.getVariants().size());
        return mapper.toProduct(saved);
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = productRepository.findByIdWithVariants(id)
                .orElseThrow(() -> new NotFoundException("No product " + id));

        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.origin() != null) {
            product.setOrigin(request.origin());
        }
        if (request.isOrganic() != null) {
            product.setIsOrganic(request.isOrganic());
        }
        if (request.categoryId() != null) {
            product.setCategory(categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new NotFoundException("No category " + request.categoryId())));
        }
        if (request.brandId() != null) {
            product.setBrand(brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new NotFoundException("No brand " + request.brandId())));
        }
        if (request.gstRatePct() != null) {
            product.setGstRatePct(validGstRate(request.gstRatePct()));
        }
        if (request.hsnCode() != null) {
            product.setHsnCode(request.hsnCode());
        }
        if (request.status() != null) {
            applyStatus(product, request.status());
        }
        return mapper.toProduct(product);
    }

    /**
     * Valid Indian GST rates only. Null defaults to 0 (nil-rated fresh produce, the bulk
     * of this catalog). The set mirrors products_gst_rate_chk; checking it here returns a
     * clean 400 naming the field rather than a 500 from the DB constraint.
     */
    private BigDecimal validGstRate(BigDecimal rate) {
        if (rate == null) {
            return BigDecimal.ZERO;
        }
        boolean allowed = ALLOWED_GST_RATES.stream().anyMatch(r -> r.compareTo(rate) == 0);
        if (!allowed) {
            throw new BadRequestException(
                    "GST rate " + rate + " is not a valid Indian rate (allowed: 0, 0.25, 3, 5, 12, 18, 28).");
        }
        return rate;
    }

    /**
     * Soft delete. Never a hard delete: order_items reference variants with ON DELETE
     * RESTRICT, because an old invoice must still render. Removing a product from the
     * catalog must not be able to destroy order history.
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public void softDelete(UUID id) {
        Product product = productRepository.findByIdWithVariants(id)
                .orElseThrow(() -> new NotFoundException("No product " + id));
        Instant now = Instant.now();
        product.setDeletedAt(now);
        product.setStatus(ProductStatus.ARCHIVED);
        product.getVariants().forEach(v -> v.setDeletedAt(now));
        log.info("Soft-deleted product {} and {} variant(s)", id, product.getVariants().size());
    }

    private void applyStatus(Product product, ProductStatus target) {
        if (target == ProductStatus.ACTIVE) {
            // Publishing something unbuyable is worse than leaving it in draft.
            boolean sellable = product.getVariants().stream()
                    .anyMatch(v -> Boolean.TRUE.equals(v.getIsActive()) && !v.isDeleted());
            if (!sellable) {
                throw new BadRequestException(
                        "Cannot publish a product with no active variant.");
            }
            if (product.getPublishedAt() == null) {
                product.setPublishedAt(Instant.now());
            }
        }
        product.setStatus(target);
    }

    private void validateVariants(List<CreateVariantRequest> variants) {
        // Duplicate SKUs within one request would only fail later, mid-insert, with a
        // constraint error that names the DB rather than the field.
        long distinct = variants.stream().map(CreateVariantRequest::sku).distinct().count();
        if (distinct != variants.size()) {
            throw new BadRequestException("Variant SKUs must be unique within a product.");
        }
        for (CreateVariantRequest v : variants) {
            // Mirrors variants_price_lte_mrp. Checked here to return a clean 400 naming
            // the SKU instead of a 500 from a constraint violation.
            if (v.price().compareTo(v.mrp()) > 0) {
                throw new BadRequestException(
                        "Variant '" + v.sku() + "': price cannot exceed MRP.");
            }
        }
    }

    private void ensureSingleDefault(Product product) {
        List<ProductVariant> defaults = product.getVariants().stream()
                .filter(v -> Boolean.TRUE.equals(v.getIsDefault()))
                .toList();
        if (defaults.isEmpty()) {
            product.getVariants().get(0).setIsDefault(true);
        } else if (defaults.size() > 1) {
            throw new BadRequestException("Only one variant can be the default.");
        }
    }

    private ProductVariant buildVariant(Product product, CreateVariantRequest vr) {
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(vr.sku())
                .barcode(vr.barcode())
                .name(vr.name())
                .weightGrams(vr.weightGrams())
                .mrp(vr.mrp())
                .price(vr.price())
                .isDefault(Boolean.TRUE.equals(vr.isDefault()))
                .isActive(true)
                .build();

        if (vr.nutrition() != null) {
            variant.setNutritionFacts(buildNutrition(variant, vr.nutrition()));
        }

        // Attach uploaded images (URLs already stored via the file endpoint). First = primary,
        // which is what the storefront card and product page show.
        if (vr.imageUrls() != null) {
            int position = 0;
            for (String url : vr.imageUrls()) {
                if (url == null || url.isBlank()) {
                    continue;
                }
                variant.getImages().add(com.ecoexpress.catalog.domain.ProductImage.builder()
                        .variant(variant)
                        .url(url.trim())
                        .position(position)
                        .isPrimary(position == 0)
                        .build());
                position++;
            }
        }
        return variant;
    }

    private NutritionFacts buildNutrition(ProductVariant variant, NutritionRequest n) {
        // Nulls are passed through untouched: "not measured" must not become zero.
        return NutritionFacts.builder()
                .variant(variant)
                .caloriesKcal(n.caloriesKcal())
                .proteinG(n.proteinG())
                .fatG(n.fatG())
                .carbohydratesG(n.carbohydratesG())
                .fiberG(n.fiberG())
                .sugarG(n.sugarG())
                .ironMg(n.ironMg())
                .vitaminAMcg(n.vitaminAMcg())
                .vitaminCMg(n.vitaminCMg())
                .vitaminDMcg(n.vitaminDMcg())
                .potassiumMg(n.potassiumMg())
                .sodiumMg(n.sodiumMg())
                .source(n.source() == null
                        ? com.ecoexpress.catalog.domain.NutritionSource.MANUAL
                        : n.source())
                .sourceRef(n.sourceRef())
                .build();
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
