package com.ecoexpress.engagement.domain;

import com.ecoexpress.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/** A merchandising banner (V6, PRD §6). Placement + a display window drive what shows where. */
@Entity
@Table(name = "banners")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "subtitle")
    private String subtitle;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "mobile_image_url")
    private String mobileImageUrl;

    @Column(name = "link_url")
    private String linkUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "placement", nullable = false)
    @Builder.Default
    private BannerPlacement placement = BannerPlacement.HOME_HERO;

    @Column(name = "position", nullable = false)
    @Builder.Default
    private Integer position = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "active_from")
    private Instant activeFrom;

    @Column(name = "active_until")
    private Instant activeUntil;

    /** Live when active and within its window (open-ended windows count as live). */
    public boolean isLiveAt(Instant when) {
        if (!Boolean.TRUE.equals(isActive)) {
            return false;
        }
        if (activeFrom != null && when.isBefore(activeFrom)) {
            return false;
        }
        return activeUntil == null || when.isBefore(activeUntil);
    }
}
