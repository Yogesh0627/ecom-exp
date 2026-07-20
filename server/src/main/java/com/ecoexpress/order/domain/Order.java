package com.ecoexpress.order.domain;

import com.ecoexpress.common.domain.BaseEntity;
import com.ecoexpress.identity.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A customer order (V4). A historical record, not a live view of the catalog.
 *
 * <p><b>Everything is snapshot.</b> The delivery address is copied onto these columns
 * rather than referenced, and each line copies the product name, SKU and price. Rename a
 * product, reprice it, or delete an address next month and this order still renders
 * exactly as the customer received it. Joining live rows to old orders silently rewrites
 * history — and for an invoice that is an accounting problem, not a cosmetic one.
 *
 * <p><b>GST.</b> Indian GST is intra-state (CGST+SGST) or inter-state (IGST), never both
 * — enforced by orders_gst_split_chk. The DB also asserts
 * grand_total = subtotal - discount + tax + shipping, so a rounding bug in the pricing
 * engine cannot ship a wrong total to a customer.
 */
@Entity
@Table(name = "orders")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends BaseEntity {

    /** Human-facing reference, e.g. ECO-20260717-0001. */
    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountTotal = BigDecimal.ZERO;

    /** Central GST — intra-state only. */
    @Column(name = "cgst_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cgstTotal = BigDecimal.ZERO;

    /** State GST — intra-state only. */
    @Column(name = "sgst_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal sgstTotal = BigDecimal.ZERO;

    /** Integrated GST — inter-state only. */
    @Column(name = "igst_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal igstTotal = BigDecimal.ZERO;

    /** Must equal cgst + sgst + igst (orders_tax_sums_chk). */
    @Column(name = "tax_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    // --- delivery address SNAPSHOT (deliberately not an FK) ---

    @Column(name = "ship_recipient_name", nullable = false)
    private String shipRecipientName;

    @Column(name = "ship_phone", nullable = false)
    private String shipPhone;

    @Column(name = "ship_line1", nullable = false)
    private String shipLine1;

    @Column(name = "ship_line2")
    private String shipLine2;

    @Column(name = "ship_landmark")
    private String shipLandmark;

    @Column(name = "ship_city", nullable = false)
    private String shipCity;

    @Column(name = "ship_state", nullable = false)
    private String shipState;

    @Column(name = "ship_pincode", nullable = false)
    private String shipPincode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ship_country", nullable = false, length = 2)
    @Builder.Default
    private String shipCountry = "IN";

    /** Which warehouse fulfils this order. Its state decides the GST split. */
    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "placed_at")
    private Instant placedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "customer_note")
    private String customerNote;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    /** True while stock is still promised to this order. */
    public boolean holdsReservation() {
        return status.holdsReservation();
    }
}
