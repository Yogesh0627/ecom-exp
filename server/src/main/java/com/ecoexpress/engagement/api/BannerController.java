package com.ecoexpress.engagement.api;

import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.engagement.domain.Banner;
import com.ecoexpress.engagement.domain.BannerPlacement;
import com.ecoexpress.engagement.repository.BannerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Banners (PRD §6). Public read of live banners; admin CRUD. */
@Tag(name = "Banners")
@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerRepository bannerRepository;

    public record CreateBannerRequest(
            @NotBlank String title,
            String subtitle,
            @NotBlank String imageUrl,
            String mobileImageUrl,
            String linkUrl,
            @NotNull BannerPlacement placement,
            Integer position,
            Instant activeFrom,
            Instant activeUntil) {}

    public record BannerView(
            UUID id, String title, String subtitle, String imageUrl,
            String mobileImageUrl, String linkUrl, String placement, int position) {}

    public record AdminBannerRow(
            UUID id, String title, String subtitle, String imageUrl, String linkUrl,
            String placement, int position, boolean isActive, boolean live,
            Instant activeFrom, Instant activeUntil) {}

    @Operation(summary = "Live banners for a placement (public)")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<BannerView>> live(
            @RequestParam(defaultValue = "HOME_HERO") BannerPlacement placement) {
        Instant now = Instant.now();
        List<BannerView> banners = bannerRepository.findActiveByPlacement(placement).stream()
                .filter(b -> b.isLiveAt(now))
                .map(this::view)
                .toList();
        return ResponseEntity.ok(banners);
    }

    @Operation(summary = "All banners across placements (admin)")
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('banner:write')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AdminBannerRow>> all() {
        Instant now = Instant.now();
        List<AdminBannerRow> rows = bannerRepository.findAllByOrderByPlacementAscPositionAsc().stream()
                .map(b -> new AdminBannerRow(
                        b.getId(), b.getTitle(), b.getSubtitle(), b.getImageUrl(), b.getLinkUrl(),
                        b.getPlacement().name(), b.getPosition(),
                        Boolean.TRUE.equals(b.getIsActive()), b.isLiveAt(now),
                        b.getActiveFrom(), b.getActiveUntil()))
                .toList();
        return ResponseEntity.ok(rows);
    }

    @Operation(summary = "Create a banner (admin)")
    @PostMapping
    @PreAuthorize("hasAuthority('banner:write')")
    @Transactional
    public ResponseEntity<BannerView> create(@Valid @RequestBody CreateBannerRequest r) {
        Banner banner = bannerRepository.save(Banner.builder()
                .title(r.title())
                .subtitle(r.subtitle())
                .imageUrl(r.imageUrl())
                .mobileImageUrl(r.mobileImageUrl())
                .linkUrl(r.linkUrl())
                .placement(r.placement())
                .position(r.position() == null ? 0 : r.position())
                .isActive(true)
                .activeFrom(r.activeFrom())
                .activeUntil(r.activeUntil())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(banner));
    }

    @Operation(summary = "Delete a banner (admin)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('banner:write')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No banner " + id));
        banner.setDeletedAt(Instant.now());
        banner.setIsActive(false);
        return ResponseEntity.noContent().build();
    }

    private BannerView view(Banner b) {
        return new BannerView(b.getId(), b.getTitle(), b.getSubtitle(), b.getImageUrl(),
                b.getMobileImageUrl(), b.getLinkUrl(), b.getPlacement().name(), b.getPosition());
    }
}
