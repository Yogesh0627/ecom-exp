package com.ecoexpress.engagement.api;

import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.common.security.AuthenticatedUser;
import com.ecoexpress.engagement.domain.Review;
import com.ecoexpress.engagement.domain.ReviewStatus;
import com.ecoexpress.engagement.repository.ReviewRepository;
import com.ecoexpress.engagement.service.ReviewService;
import com.ecoexpress.identity.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
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

@Tag(name = "Reviews")
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    // review.auto_approve_verified — a settings-backed flag. Read from config for now; when the
    // settings module lands this reads from there. Default false: everything is moderated.
    private static final boolean AUTO_APPROVE_VERIFIED = false;

    public record CreateReviewRequest(
            @NotNull UUID productId,
            @Min(1) @Max(5) short rating,
            @Size(max = 150) String title,
            @Size(max = 4000) String body,
            List<String> imageUrls) {}

    public record ModerateRequest(@NotNull ReviewStatus decision, @Size(max = 500) String note) {}

    public record ReviewResponse(
            UUID id, UUID productId, String reviewerName, short rating,
            String title, String body, boolean verifiedPurchase, ReviewStatus status,
            int helpfulCount, List<String> imageUrls, Instant createdAt) {}

    @Operation(summary = "Write a review (verified badge is set by the server, not the client)")
    @PostMapping
    public ResponseEntity<ReviewResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateReviewRequest r) {
        Review review = reviewService.create(user.getId(), r.productId(), r.rating(),
                r.title(), r.body(), r.imageUrls(), AUTO_APPROVE_VERIFIED);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(review));
    }

    @Operation(summary = "Approved reviews for a product (public)")
    @GetMapping("/product/{productId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ReviewResponse>> forProduct(
            @PathVariable UUID productId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Review> page = reviewRepository.findByProductIdAndStatus(
                productId, ReviewStatus.APPROVED, pageable);
        return ResponseEntity.ok(page.getContent().stream().map(this::toResponse).toList());
    }

    @Operation(summary = "Flag an approved review for re-moderation")
    @PostMapping("/{id}/flag")
    public ResponseEntity<Void> flag(@PathVariable UUID id) {
        reviewService.flag(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark a review helpful")
    @PostMapping("/{id}/helpful")
    public ResponseEntity<Void> helpful(@PathVariable UUID id) {
        reviewService.markHelpful(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- moderation (staff) ----------

    @Operation(summary = "Moderation queue: pending + flagged reviews")
    @GetMapping("/moderation-queue")
    @PreAuthorize("hasAuthority('review:moderate')")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> queue(
            @RequestParam(defaultValue = "PENDING") ReviewStatus status,
            @PageableDefault(size = 30) Pageable pageable) {
        Page<Review> page = reviewRepository.findByStatusOrderByCreatedAtAsc(status, pageable);
        return ResponseEntity.ok(Map.of(
                "total", page.getTotalElements(),
                "reviews", page.getContent().stream().map(this::toResponse).toList()));
    }

    @Operation(summary = "Approve or reject a review")
    @PostMapping("/{id}/moderate")
    @PreAuthorize("hasAuthority('review:moderate')")
    // @Transactional so the response is built inside the session: toResponse walks the review's
    // lazy user/product/images. Without it the moderation commits but serialising the reply throws
    // LazyInitializationException — a 500 on an action that actually succeeded (see War Story 19).
    @Transactional
    public ResponseEntity<ReviewResponse> moderate(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id,
            @Valid @RequestBody ModerateRequest r) {
        var moderator = userRepository.findById(actor.getId())
                .orElseThrow(() -> new NotFoundException("No user " + actor.getId()));
        Review review = reviewService.moderate(id, r.decision(), moderator, r.note());
        return ResponseEntity.ok(toResponse(review));
    }

    private ReviewResponse toResponse(Review r) {
        return new ReviewResponse(
                r.getId(), r.getProduct().getId(),
                // First name only — a review byline should not expose a full name.
                firstName(r.getUser().getFullName()),
                r.getRating(), r.getTitle(), r.getBody(),
                Boolean.TRUE.equals(r.getVerifiedPurchase()), r.getStatus(),
                r.getHelpfulCount(),
                r.getImages().stream().map(com.ecoexpress.engagement.domain.ReviewImage::getUrl).toList(),
                r.getCreatedAt());
    }

    private String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Customer";
        }
        return fullName.trim().split("\\s+")[0];
    }
}
