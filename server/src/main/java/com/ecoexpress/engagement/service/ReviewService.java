package com.ecoexpress.engagement.service;

import com.ecoexpress.catalog.domain.Product;
import com.ecoexpress.catalog.repository.ProductRepository;
import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.common.exception.ApiExceptions.ConflictException;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.engagement.domain.Review;
import com.ecoexpress.engagement.domain.ReviewImage;
import com.ecoexpress.engagement.domain.ReviewStatus;
import com.ecoexpress.engagement.repository.ReviewRepository;
import com.ecoexpress.identity.domain.User;
import com.ecoexpress.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reviews: writing, moderation, and the verified-purchase rule.
 *
 * <p>The rule that gives the feature its integrity: {@code verifiedPurchase} is derived from a
 * DELIVERED order line, never accepted from the client. And a review only becomes visible on the
 * product page after a moderator approves it — reviews start PENDING.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * @param autoApproveVerified when true, a verified-purchase review skips the moderation queue.
     *        Driven by the review.auto_approve_verified setting; passed in so this service does
     *        not reach into the settings module.
     */
    @Transactional
    public Review create(UUID userId, UUID productId, short rating, String title, String body,
                         List<String> imageUrls, boolean autoApproveVerified) {
        if (rating < 1 || rating > 5) {
            throw new BadRequestException("Rating must be between 1 and 5.");
        }
        // Cheap pre-check; the partial unique index is the real guarantee (see the catch below).
        if (reviewRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new ConflictException("You have already reviewed this product.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("No product " + productId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("No user " + userId));

        // The verified badge: proven from a delivered order line, not taken on trust.
        UUID deliveredOrderItem = reviewRepository
                .findDeliveredOrderItem(userId, productId).orElse(null);
        boolean verified = deliveredOrderItem != null;

        Review review = Review.builder()
                .user(user)
                .product(product)
                .orderItemId(deliveredOrderItem)
                .rating(rating)
                .title(title)
                .body(body)
                .verifiedPurchase(verified)
                .status(verified && autoApproveVerified ? ReviewStatus.APPROVED : ReviewStatus.PENDING)
                .build();

        if (imageUrls != null) {
            int pos = 0;
            for (String url : imageUrls) {
                review.getImages().add(ReviewImage.builder()
                        .review(review).url(url).position(pos++).build());
            }
        }
        if (review.getStatus() == ReviewStatus.APPROVED) {
            // Approved-on-create still records who/when for the audit trail: the system.
            review.setModeratedAt(Instant.now());
        }

        try {
            Review saved = reviewRepository.save(review);
            log.info("Review {} created for product {} (verified={}, status={})",
                    saved.getId(), productId, verified, saved.getStatus());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Lost the race against a concurrent first review for the same (user, product).
            throw new ConflictException("You have already reviewed this product.");
        }
    }

    /** Approve or reject a review (moderation). */
    @Transactional
    public Review moderate(UUID reviewId, ReviewStatus decision, User moderator, String note) {
        if (decision != ReviewStatus.APPROVED && decision != ReviewStatus.REJECTED) {
            throw new BadRequestException("A moderation decision must be APPROVED or REJECTED.");
        }
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("No review " + reviewId));

        review.setStatus(decision);
        review.setModeratedBy(moderator);
        review.setModeratedAt(Instant.now());
        review.setModerationNote(note);
        log.info("Review {} {} by {}", reviewId, decision, moderator == null ? "system" : moderator.getId());
        return review;
    }

    /** A user flags a review as inappropriate; it goes back to the moderation queue. */
    @Transactional
    public void flag(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("No review " + reviewId));
        // Only flag something currently public; flagging a rejected/pending review is a no-op.
        if (review.getStatus() == ReviewStatus.APPROVED) {
            review.setStatus(ReviewStatus.FLAGGED);
            log.info("Review {} flagged for re-moderation", reviewId);
        }
    }

    /** Marks a review helpful. A real system would dedupe per user; kept simple here. */
    @Transactional
    public void markHelpful(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("No review " + reviewId));
        review.setHelpfulCount(review.getHelpfulCount() + 1);
    }
}
