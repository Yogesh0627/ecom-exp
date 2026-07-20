package com.ecoexpress.order.service;

import com.ecoexpress.cart.domain.Cart;
import com.ecoexpress.cart.domain.CartStatus;
import com.ecoexpress.cart.repository.CartRepository;
import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.identity.domain.User;
import com.ecoexpress.inventory.domain.Warehouse;
import com.ecoexpress.inventory.repository.WarehouseRepository;
import com.ecoexpress.inventory.service.InventoryService;
import com.ecoexpress.order.domain.Address;
import com.ecoexpress.order.domain.Order;
import com.ecoexpress.order.domain.OrderItem;
import com.ecoexpress.order.domain.OrderStatus;
import com.ecoexpress.order.domain.OrderStatusHistory;
import com.ecoexpress.order.repository.AddressRepository;
import com.ecoexpress.order.repository.OrderRepository;
import com.ecoexpress.order.repository.OrderStatusHistoryRepository;
import com.ecoexpress.order.service.PricingEngine.PricedLine;
import com.ecoexpress.order.service.PricingEngine.PricedOrder;
import com.ecoexpress.promotion.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Checkout and the order lifecycle.
 *
 * <p><b>Checkout is one transaction.</b> Reserving stock, snapshotting the catalog,
 * pricing and converting the cart either all happen or none do. A partial checkout is the
 * worst outcome available: stock reserved against an order that does not exist, invisible
 * to both the customer and ops.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final AddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryService inventoryService;
    private final PricingEngine pricingEngine;
    private final CouponService couponService;
    private final com.ecoexpress.engagement.service.NotificationService notificationService;
    private final com.ecoexpress.ai.service.PantryService pantryService;

    @Transactional
    public Order checkout(UUID userId, UUID addressId, BigDecimal shippingFee, String customerNote,
                          String couponCode) {
        Cart cart = cartRepository.findByUserAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException("Your cart is empty."));
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Your cart is empty.");
        }

        // Scoped to the user: an address id alone would let anyone ship to someone
        // else's saved address, or probe which ids exist.
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new NotFoundException("No such address."));

        Warehouse warehouse = pickWarehouse();

        BigDecimal shipping = shippingFee == null ? BigDecimal.ZERO : shippingFee;
        BigDecimal orderDiscount = BigDecimal.ZERO;
        CouponService.CouponQuote quote = null;

        // Coupon is quoted (validated) here, but only REDEEMED after the order is saved, so a
        // failed checkout does not consume a use. The discount applies to the pre-tax subtotal;
        // free-shipping zeroes the fee instead of discounting.
        if (couponCode != null && !couponCode.isBlank()) {
            BigDecimal subtotal = cartSubtotal(cart);
            quote = couponService.quote(couponCode, userId, subtotal);
            if (quote.freeShipping()) {
                shipping = BigDecimal.ZERO;
            } else {
                orderDiscount = quote.discount();
            }
        }

        // Price BEFORE reserving: if the pricing engine throws, nothing has been held.
        PricedOrder priced = pricingEngine.price(
                cart.getItems(), warehouse.getState(), address.getState(),
                shipping, orderDiscount);

        User user = cart.getUser();
        Order order = Order.builder()
                .orderNumber(nextOrderNumber())
                .user(user)
                .status(OrderStatus.PENDING_PAYMENT)
                .subtotal(priced.subtotal())
                .discountTotal(priced.discountTotal())
                .cgstTotal(priced.cgstTotal())
                .sgstTotal(priced.sgstTotal())
                .igstTotal(priced.igstTotal())
                .taxTotal(priced.taxTotal())
                .shippingFee(priced.shippingFee())
                .grandTotal(priced.grandTotal())
                .currency("INR")
                // Address SNAPSHOT — copied, never referenced.
                .shipRecipientName(address.getRecipientName())
                .shipPhone(address.getPhone())
                .shipLine1(address.getLine1())
                .shipLine2(address.getLine2())
                .shipLandmark(address.getLandmark())
                .shipCity(address.getCity())
                .shipState(address.getState())
                .shipPincode(address.getPincode())
                .shipCountry(address.getCountry())
                .warehouseId(warehouse.getId())
                .placedAt(Instant.now())
                .customerNote(customerNote)
                .build();

        for (PricedLine line : priced.lines()) {
            var v = line.variant();
            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .variant(v)
                    // Catalog SNAPSHOT — this is what the invoice renders, forever.
                    .productNameSnapshot(v.getProduct().getName())
                    .variantNameSnapshot(v.getName())
                    .skuSnapshot(v.getSku())
                    .imageUrlSnapshot(v.primaryImage() == null ? null : v.primaryImage().getUrl())
                    .qty(line.qty())
                    .unitPrice(line.unitPrice())
                    .discountAmount(line.discountAmount())
                    .taxRatePct(line.taxRatePct())
                    .taxAmount(line.taxAmount())
                    .lineTotal(line.lineTotal())
                    .hsnCode(line.hsnCode())
                    .build());
        }

        Order saved = orderRepository.save(order);

        // Reserve last: reserve() locks each inventory row, and holding those locks for
        // the least time possible keeps concurrent checkouts moving. Any shortfall throws
        // InsufficientStockException and rolls the whole order back.
        for (PricedLine line : priced.lines()) {
            inventoryService.reserve(line.variant().getId(), warehouse.getId(),
                    line.qty(), saved.getId());
        }

        // Redeem the coupon now that the order exists — writes the append-only redemption row
        // and bumps the counter, under the coupon's row lock. In the same transaction as the
        // order, so a rollback (e.g. a stock shortfall above) also un-redeems the coupon.
        if (quote != null) {
            couponService.redeem(quote.coupon().getCode(), user, saved, cartSubtotal(cart));
        }

        recordTransition(saved, null, OrderStatus.PENDING_PAYMENT, null, "Order placed");

        notificationService.notify(user,
                com.ecoexpress.engagement.domain.NotificationType.ORDER_PLACED,
                "Order placed", "Your order " + saved.getOrderNumber() + " has been placed.",
                "{\"orderId\":\"" + saved.getId() + "\"}");

        cart.setStatus(CartStatus.CONVERTED);
        cartRepository.save(cart);

        log.info("Order {} placed by {}: {} line(s), total {} {}",
                saved.getOrderNumber(), userId, saved.getItems().size(),
                saved.getGrandTotal(), saved.getCurrency());
        return saved;
    }

    /**
     * Moves an order to a new status, enforcing the state machine and the stock
     * consequences.
     */
    @Transactional
    public Order transition(UUID orderId, OrderStatus target, User actor, String note) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new NotFoundException("No order " + orderId));

        OrderStatus current = order.getStatus();
        if (current == target) {
            return order;
        }
        if (!current.canTransitionTo(target)) {
            throw new BadRequestException(
                    "Cannot move an order from " + current + " to " + target + ".");
        }

        switch (target) {
            case SHIPPED -> shipStock(order);
            case DELIVERED -> stockPantryFromDelivery(order);
            case CANCELLED -> {
                releaseStock(order);
                order.setCancelledAt(Instant.now());
                order.setCancellationReason(note);
            }
            // A refund is financial; the goods are handled by RETURNED, which precedes it.
            default -> { }
        }

        order.setStatus(target);
        recordTransition(order, current, target, actor, note);
        notifyOnStatus(order, target);
        log.info("Order {}: {} -> {}", order.getOrderNumber(), current, target);
        return order;
    }

    /** Raises a customer notification for the status changes they care about. */
    private void notifyOnStatus(Order order, OrderStatus target) {
        var type = switch (target) {
            case SHIPPED -> com.ecoexpress.engagement.domain.NotificationType.ORDER_SHIPPED;
            case DELIVERED -> com.ecoexpress.engagement.domain.NotificationType.ORDER_DELIVERED;
            case CANCELLED -> com.ecoexpress.engagement.domain.NotificationType.ORDER_CANCELLED;
            default -> null;
        };
        if (type == null) {
            return;
        }
        String title = switch (target) {
            case SHIPPED -> "Order shipped";
            case DELIVERED -> "Order delivered";
            default -> "Order cancelled";
        };
        notificationService.notify(order.getUser(), type, title,
                "Your order " + order.getOrderNumber() + " is now " + target + ".",
                "{\"orderId\":\"" + order.getId() + "\"}");
    }

    /**
     * On delivery, seed the customer's pantry with what they just received — closing the "buy it →
     * it's in my pantry" loop (PRD §5.5). Best-effort and isolated (the pantry service runs in its
     * own transaction): auto-stocking must never fail or roll back the delivery itself.
     */
    private void stockPantryFromDelivery(Order order) {
        try {
            var lines = order.getItems().stream()
                    .map(i -> new com.ecoexpress.ai.service.PantryService.DeliveredLine(
                            i.getProductNameSnapshot(), i.getQty() == null ? 1 : i.getQty()))
                    .toList();
            pantryService.addFromDelivery(order.getUser().getId(), order.getId(), lines);
        } catch (Exception e) {
            log.warn("Could not auto-stock pantry for order {}: {}",
                    order.getOrderNumber(), e.getMessage());
        }
    }

    /** Customer-initiated cancel, allowed only while nothing has shipped. */
    @Transactional
    public Order cancelByCustomer(UUID orderId, UUID userId, String reason) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new NotFoundException("No order " + orderId));
        if (!order.getUser().getId().equals(userId)) {
            // Same message as a missing order: confirming that someone else's order
            // exists is an information leak.
            throw new NotFoundException("No order " + orderId);
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new BadRequestException(
                    "This order is already " + order.getStatus() + " and cannot be cancelled.");
        }
        return transition(orderId, OrderStatus.CANCELLED, order.getUser(), reason);
    }

    /**
     * Releases reservations held by unpaid orders past their window.
     *
     * <p>Without this, every abandoned checkout permanently removes stock from sale —
     * the shelf has it, the storefront says sold out, and nobody notices until someone
     * counts.
     */
    @Transactional
    public int releaseExpiredReservations(java.time.Duration holdWindow) {
        var expired = orderRepository.findExpiredPendingPayment(Instant.now().minus(holdWindow));
        for (Order order : expired) {
            releaseStock(order);
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(Instant.now());
            order.setCancellationReason("Payment not completed in time");
            recordTransition(order, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, null,
                    "Auto-cancelled: payment window elapsed");
        }
        if (!expired.isEmpty()) {
            log.info("Released reservations for {} expired unpaid order(s)", expired.size());
        }
        return expired.size();
    }

    /** Pre-tax subtotal of the cart at live prices — the base a coupon discounts. */
    private BigDecimal cartSubtotal(Cart cart) {
        return cart.getItems().stream()
                .map(i -> i.getVariant().getPrice().multiply(java.math.BigDecimal.valueOf(i.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void shipStock(Order order) {
        for (OrderItem item : order.getItems()) {
            inventoryService.commitSale(item.getVariant().getId(), order.getWarehouseId(),
                    item.getQty(), order.getId());
        }
    }

    private void releaseStock(Order order) {
        // Only if the order still holds one. Releasing against a shipped or already
        // cancelled order would decrement qty_reserved twice and corrupt availability.
        if (!order.holdsReservation()) {
            return;
        }
        for (OrderItem item : order.getItems()) {
            inventoryService.release(item.getVariant().getId(), order.getWarehouseId(),
                    item.getQty(), order.getId());
        }
    }

    private void recordTransition(Order order, OrderStatus from, OrderStatus to,
                                  User actor, String note) {
        historyRepository.save(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(from)
                .toStatus(to)
                .changedBy(actor)
                .note(note)
                .build());
    }

    /**
     * Single-warehouse for now: pick the only active one.
     *
     * <p>Multi-warehouse routing (nearest to the pincode, split shipments) is a real
     * feature, not a line of code — the schema supports N warehouses and this is the one
     * place that has to change. Failing loudly beats silently picking an arbitrary
     * warehouse and getting the GST state wrong.
     */
    private Warehouse pickWarehouse() {
        var active = warehouseRepository.findByIsActiveTrue();
        if (active.isEmpty()) {
            throw new BadRequestException("No warehouse is available to fulfil orders.");
        }
        if (active.size() > 1) {
            log.warn("{} active warehouses; routing picks the first. Implement pincode-based "
                    + "routing before running more than one.", active.size());
        }
        return active.get(0);
    }

    /**
     * ECO-yyyyMMdd-NNNN in IST.
     *
     * <p>Racy by construction: two concurrent checkouts can read the same count. The
     * unique index on order_number is the real guarantee — the loser's transaction fails
     * and retries rather than quietly issuing a duplicate. A sequence would be better and
     * is the obvious upgrade when volume justifies it.
     */
    private String nextOrderNumber() {
        LocalDate today = LocalDate.now(IST);
        Instant dayStart = today.atStartOfDay(IST).toInstant();
        long todayCount = orderRepository.countSince(dayStart);
        return "ECO-%s-%04d".formatted(today.format(ORDER_DATE), todayCount + 1);
    }
}
