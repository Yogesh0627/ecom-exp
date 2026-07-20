package com.ecoexpress.catalog.repository;

import com.ecoexpress.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Root categories for the storefront nav. */
    @Query("SELECT c FROM Category c WHERE c.parent IS NULL AND c.isActive = true ORDER BY c.position")
    List<Category> findActiveRoots();

    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId AND c.isActive = true ORDER BY c.position")
    List<Category> findActiveChildren(UUID parentId);

    /**
     * All descendant ids of a category, inclusive.
     *
     * <p>Recursive CTE rather than repeated queries: browsing "Fruits" must include
     * products filed under its children, and the depth is not known at query time.
     * Cycle-safe via the depth cap — the tree is asserted to be <= 3 deep, but a bad
     * row must not hang the query.
     */
    @Query(value = """
            WITH RECURSIVE tree AS (
                SELECT id, 1 AS depth FROM categories
                 WHERE id = :rootId AND deleted_at IS NULL
                UNION ALL
                SELECT c.id, t.depth + 1 FROM categories c
                  JOIN tree t ON c.parent_id = t.id
                 WHERE c.deleted_at IS NULL AND t.depth < 10
            )
            SELECT id FROM tree
            """, nativeQuery = true)
    List<UUID> findSubtreeIds(UUID rootId);
}
