package com.ecoexpress.engagement.repository;

import com.ecoexpress.engagement.domain.Banner;
import com.ecoexpress.engagement.domain.BannerPlacement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BannerRepository extends JpaRepository<Banner, UUID> {

    /** Active banners for a placement, in display order. Window filtering is done in the service. */
    @Query("""
            SELECT b FROM Banner b
            WHERE b.placement = :placement AND b.isActive = true
            ORDER BY b.position ASC
            """)
    List<Banner> findActiveByPlacement(@Param("placement") BannerPlacement placement);

    /** Admin listing: every non-deleted banner, grouped by placement then display order. */
    List<Banner> findAllByOrderByPlacementAscPositionAsc();
}
