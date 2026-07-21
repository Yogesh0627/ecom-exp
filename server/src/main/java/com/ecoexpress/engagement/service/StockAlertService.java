package com.ecoexpress.engagement.service;

import com.ecoexpress.catalog.domain.Product;
import com.ecoexpress.catalog.domain.ProductVariant;
import com.ecoexpress.catalog.repository.ProductVariantRepository;
import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.engagement.domain.NotificationType;
import com.ecoexpress.engagement.domain.StockAlert;
import com.ecoexpress.engagement.repository.StockAlertRepository;
import com.ecoexpress.identity.domain.User;
import com.ecoexpress.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Back-in-stock alerts (V13): shoppers subscribe to an out-of-stock variant and are notified once
 * inventory returns. The restock trigger is {@link #notifyBackInStock(UUID)}, called by the
 * inventory layer when a variant crosses 0 -> positive.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockAlertService {

    private final StockAlertRepository alertRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /** Subscribe the user to this variant. Idempotent: a second call returns the existing alert. */
    @Transactional
    public void subscribe(UUID userId, UUID variantId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new NotFoundException("Variant not found."));
        if (Boolean.FALSE.equals(variant.getIsActive())) {
            throw new BadRequestException("This item is not available.");
        }
        alertRepository.findByUserIdAndVariantIdAndNotifiedAtIsNull(userId, variantId)
                .ifPresentOrElse(
                        existing -> { /* already subscribed — nothing to do */ },
                        () -> alertRepository.save(StockAlert.builder()
                                .user(userRepository.getReferenceById(userId))
                                .variant(variant)
                                .build()));
    }

    /** Cancel an open alert for this user+variant, if any. */
    @Transactional
    public void unsubscribe(UUID userId, UUID variantId) {
        alertRepository.findByUserIdAndVariantIdAndNotifiedAtIsNull(userId, variantId)
                .ifPresent(alertRepository::delete);
    }

    /** Variant ids the user is currently waiting on, so the UI can show "You'll be notified". */
    @Transactional(readOnly = true)
    public List<UUID> activeVariantIds(UUID userId) {
        return alertRepository.findActiveVariantIdsByUser(userId);
    }

    /**
     * Fulfils every open alert for a variant that just came back in stock. Best-effort: a single
     * failed notification must not abort a stock receipt, so failures are logged and skipped.
     *
     * <p>Runs inside the caller's inventory transaction, so the alerts are marked notified atomically
     * with the stock change that triggered them.
     */
    @Transactional
    public void notifyBackInStock(UUID variantId) {
        List<StockAlert> open = alertRepository.findByVariantIdAndNotifiedAtIsNull(variantId);
        if (open.isEmpty()) {
            return;
        }
        ProductVariant variant = open.get(0).getVariant();
        Product product = variant.getProduct();
        String title = product.getName() + " is back in stock";
        String body = "Good news — " + product.getName() + " (" + variant.getName()
                + ") is available again. Order now before it sells out.";
        String payload = "{\"productSlug\":\"" + product.getSlug() + "\",\"variantId\":\""
                + variantId + "\"}";

        Instant now = Instant.now();
        for (StockAlert alert : open) {
            User user = alert.getUser();
            try {
                notificationService.notify(user, NotificationType.BACK_IN_STOCK, title, body, payload);
                alert.setNotifiedAt(now);
            } catch (Exception e) {
                log.warn("Back-in-stock notify failed for user {} variant {}: {}",
                        user == null ? "?" : user.getId(), variantId, e.getMessage());
            }
        }
        log.info("Notified {} shopper(s) that variant {} is back in stock", open.size(), variantId);
    }
}
