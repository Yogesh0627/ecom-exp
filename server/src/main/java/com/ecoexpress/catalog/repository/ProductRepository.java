package com.ecoexpress.catalog.repository;

import com.ecoexpress.catalog.domain.Product;
import com.ecoexpress.catalog.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>,
        JpaSpecificationExecutor<Product> {

    /**
     * Product detail page.
     *
     * <p>Fetches variants (+ their one-to-one nutrition) but NOT images. Both
     * {@code variants} and {@code images} are Lists, and Hibernate refuses to fetch two
     * bags at once — it cannot attribute a joined row to the right collection
     * (MultipleBagFetchException). Making them Sets would compile, but produce a
     * variants x images cartesian product.
     *
     * <p>Images load instead via {@code @BatchSize} on the collection: one extra query
     * for all variants' images, rather than one per variant. Two queries total, no
     * row multiplication.
     */
    @EntityGraph(attributePaths = {"variants", "variants.nutritionFacts", "brand", "category"})
    @Query("SELECT p FROM Product p WHERE p.slug = :slug")
    Optional<Product> findBySlugWithDetails(@Param("slug") String slug);

    @EntityGraph(attributePaths = {"variants", "brand", "category"})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithVariants(@Param("id") UUID id);

    boolean existsBySlug(String slug);

    /**
     * Full-text search against the GENERATED search_vector column (GIN-indexed in V2).
     *
     * <p>Native query because JPQL cannot express tsvector matching. plainto_tsquery,
     * not to_tsquery: it treats the input as words rather than query syntax, so a user
     * typing "rice & dal" gets results instead of a syntax error.
     *
     * <p>ts_rank orders by relevance; the name is weighted 'A' and description 'B' in
     * the generated column, so a name match outranks a description mention.
     */
    /**
     * Full-text relevance PLUS a substring (ILIKE) fallback on the name, so search-as-you-type
     * matches partial words too ("tom" → "Tomato", which plainto_tsquery alone would miss). Ranked
     * matches sort first; substring-only matches follow, ordered by name.
     */
    @Query(value = """
            SELECT * FROM products p
            WHERE p.deleted_at IS NULL
              AND p.status = 'ACTIVE'
              AND (p.search_vector @@ plainto_tsquery('english', :q)
                   OR lower(p.name) LIKE '%' || lower(:q) || '%')
            ORDER BY ts_rank(p.search_vector, plainto_tsquery('english', :q)) DESC, p.name ASC
            """,
            countQuery = """
            SELECT count(*) FROM products p
            WHERE p.deleted_at IS NULL
              AND p.status = 'ACTIVE'
              AND (p.search_vector @@ plainto_tsquery('english', :q)
                   OR lower(p.name) LIKE '%' || lower(:q) || '%')
            """,
            nativeQuery = true)
    Page<Product> search(@Param("q") String query, Pageable pageable);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    /**
     * Active products in the same category, excluding one product — the candidate set for
     * automatic "you may also like" (ranked and stock-filtered by the service). Fetches variants
     * (+ nutrition) so the caller can rank on organic/health without N+1 queries.
     */
    @EntityGraph(attributePaths = {"variants", "variants.nutritionFacts", "brand", "category"})
    @Query("""
            SELECT p FROM Product p
            WHERE p.category.id = :categoryId
              AND p.status = com.ecoexpress.catalog.domain.ProductStatus.ACTIVE
              AND p.id <> :excludeProductId
            """)
    java.util.List<Product> findActiveInCategoryExcluding(
            @Param("categoryId") UUID categoryId,
            @Param("excludeProductId") UUID excludeProductId,
            Pageable pageable);
}
