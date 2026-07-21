package com.ecoexpress.engagement.repository;

import com.ecoexpress.engagement.domain.StockAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockAlertRepository extends JpaRepository<StockAlert, UUID> {

    /** An open (un-notified) alert for this user+variant, if one exists. */
    Optional<StockAlert> findByUserIdAndVariantIdAndNotifiedAtIsNull(UUID userId, UUID variantId);

    /** All open alerts for a variant — fulfilled when it comes back in stock. */
    List<StockAlert> findByVariantIdAndNotifiedAtIsNull(UUID variantId);

    /** Variant ids this user is currently waiting on (to render the button state). */
    @org.springframework.data.jpa.repository.Query(
            "SELECT a.variant.id FROM StockAlert a WHERE a.user.id = :userId AND a.notifiedAt IS NULL")
    List<UUID> findActiveVariantIdsByUser(UUID userId);
}
