package com.ecoexpress.ai.service;

import com.ecoexpress.ai.domain.PantryItem;
import com.ecoexpress.ai.domain.PantrySource;
import com.ecoexpress.ai.domain.PantryUnit;
import com.ecoexpress.ai.repository.PantryItemRepository;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.engagement.domain.NotificationType;
import com.ecoexpress.engagement.service.NotificationService;
import com.ecoexpress.identity.domain.User;
import com.ecoexpress.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The AI Pantry (PRD §5.5): the household inventory that lets the app "consume pantry before
 * suggesting purchases".
 *
 * <p>Every operation is scoped to the caller's own user id. {@link #hasIngredient} is the hook
 * the recommender and meal planner call to avoid suggesting something the user already owns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PantryService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter EXPIRY_FMT = DateTimeFormatter.ofPattern("d MMM");

    private final PantryItemRepository pantryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<PantryItem> list(UUID userId) {
        return pantryRepository.findActiveForUser(userId);
    }

    @Transactional
    public PantryItem add(UUID userId, String ingredientName, BigDecimal qty, PantryUnit unit,
                          LocalDate expiryDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("No user " + userId));
        PantryItem item = pantryRepository.save(PantryItem.builder()
                .user(user)
                .ingredientName(ingredientName.trim())
                .qty(qty == null ? BigDecimal.ONE : qty)
                .unit(unit == null ? PantryUnit.PIECE : unit)
                .expiryDate(expiryDate)
                .source(PantrySource.MANUAL)
                .build());
        log.debug("Pantry: {} added '{}'", userId, ingredientName);
        return item;
    }

    /** Marks an item consumed. Scoped to the owner — you cannot consume someone else's pantry. */
    @Transactional
    public void consume(UUID userId, UUID itemId) {
        PantryItem item = pantryRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("No pantry item " + itemId));
        if (!item.getUser().getId().equals(userId)) {
            throw new NotFoundException("No pantry item " + itemId);
        }
        item.setConsumedAt(Instant.now());
    }

    @Transactional
    public void remove(UUID userId, UUID itemId) {
        PantryItem item = pantryRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("No pantry item " + itemId));
        if (!item.getUser().getId().equals(userId)) {
            throw new NotFoundException("No pantry item " + itemId);
        }
        item.setDeletedAt(Instant.now());
    }

    /** True when the user already has this ingredient — the "don't sell what they have" check. */
    @Transactional(readOnly = true)
    public boolean hasIngredient(UUID userId, String ingredientName) {
        return pantryRepository.userHasIngredient(userId, ingredientName);
    }

    /** One line of a delivered order, reduced to just what the pantry needs. */
    public record DeliveredLine(String ingredientName, int qtyUnits) {}

    /**
     * Auto-stocks the pantry from a delivered order (PRD §5.5 — close the loop so what you buy
     * shows up in your inventory without re-typing it). Idempotent per order, and best-effort:
     * runs in its OWN transaction so a pantry hiccup can never roll back the delivery that
     * triggered it. Items land as {@link PantrySource#ORDER} with the source order recorded; unit
     * is {@code PIECE} (one delivered pack = one unit) since we don't parse pack weights here.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int addFromDelivery(UUID userId, UUID orderId, List<DeliveredLine> lines) {
        if (lines == null || lines.isEmpty() || pantryRepository.existsBySourceOrder(orderId)) {
            return 0;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("No user " + userId));
        int added = 0;
        for (DeliveredLine line : lines) {
            if (line.ingredientName() == null || line.ingredientName().isBlank()) {
                continue;
            }
            pantryRepository.save(PantryItem.builder()
                    .user(user)
                    .ingredientName(line.ingredientName().trim())
                    .qty(BigDecimal.valueOf(Math.max(1, line.qtyUnits())))
                    .unit(PantryUnit.PIECE)
                    .source(PantrySource.ORDER)
                    .sourceOrderId(orderId)
                    .build());
            added++;
        }
        log.info("Pantry: auto-stocked {} item(s) for user {} from order {}", added, userId, orderId);
        return added;
    }

    /**
     * Emails each user whose pantry items expire within {@code daysAhead} days (or already lapsed)
     * and haven't been reminded yet, then stamps {@code expiryNotifiedAt} so nobody is nagged
     * twice. One summary notification per user (in-app + email via {@link NotificationService}).
     *
     * @return the number of users notified
     */
    @Transactional
    public int sendExpiryReminders(int daysAhead) {
        LocalDate cutoff = LocalDate.now(IST).plusDays(Math.max(0, daysAhead));
        List<PantryItem> due = pantryRepository.findExpiringUnnotified(cutoff);
        if (due.isEmpty()) {
            return 0;
        }

        // Group by owner, preserving expiry order (the query returns soonest-first is not
        // guaranteed, so we don't rely on it for correctness — only the grouping matters).
        Map<UUID, List<PantryItem>> byUser = due.stream()
                .collect(Collectors.groupingBy(i -> i.getUser().getId(), LinkedHashMap::new,
                        Collectors.toList()));

        Instant now = Instant.now();
        int notified = 0;
        for (List<PantryItem> items : byUser.values()) {
            User user = items.get(0).getUser();
            String summary = items.stream()
                    .map(i -> i.getIngredientName()
                            + (i.getExpiryDate() != null ? " (by " + EXPIRY_FMT.format(i.getExpiryDate()) + ")" : ""))
                    .collect(Collectors.joining(", "));
            String title = items.size() == 1
                    ? "A pantry item is expiring soon"
                    : items.size() + " pantry items are expiring soon";
            String body = "Use these before they spoil: " + summary
                    + ". Open your pantry to plan meals around them.";
            try {
                notificationService.notify(user, NotificationType.PANTRY_EXPIRY, title, body,
                        "{\"expiringCount\":" + items.size() + "}");
                notified++;
            } catch (Exception e) {
                log.warn("Could not send pantry-expiry reminder to user {}: {}",
                        user.getId(), e.getMessage());
            }
            // Stamp regardless — notify() is best-effort and never throws; a null email address
            // shouldn't leave the item to retry forever.
            items.forEach(i -> i.setExpiryNotifiedAt(now));
        }
        return notified;
    }
}
