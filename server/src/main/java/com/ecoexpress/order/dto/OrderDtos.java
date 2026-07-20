package com.ecoexpress.order.dto;

import com.ecoexpress.order.domain.AddressType;
import com.ecoexpress.order.domain.OrderStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {}

    // ---------- requests ----------

    public record CreateAddressRequest(
            String label,
            @NotBlank @Size(max = 120) String recipientName,
            @NotBlank @Pattern(regexp = "^(\\+91)?[6-9][0-9]{9}$",
                    message = "must be a valid Indian mobile number") String phone,
            @NotBlank String line1,
            String line2,
            String landmark,
            @NotBlank String city,
            @NotBlank String state,
            @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$",
                    message = "must be a valid 6-digit pincode") String pincode,
            AddressType type,
            Boolean isDefault) {}

    public record CheckoutRequest(
            @NotNull UUID addressId,
            @DecimalMin("0.00") BigDecimal shippingFee,
            @Size(max = 500) String customerNote,
            /** Optional coupon code; validated and redeemed atomically with the order. */
            @Size(max = 40) String couponCode) {}

    public record TransitionRequest(
            @NotNull OrderStatus status,
            @Size(max = 500) String note) {}

    public record CancelRequest(@Size(max = 500) String reason) {}

    // ---------- responses ----------

    public record AddressResponse(
            UUID id, String label, String recipientName, String phone,
            String line1, String line2, String landmark,
            String city, String state, String pincode, String country,
            AddressType type, boolean isDefault) {}

    public record OrderItemResponse(
            UUID id,
            UUID variantId,
            /** From the snapshot, not the live catalog — this is what was actually bought. */
            String productName,
            String variantName,
            String sku,
            String imageUrl,
            int qty,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal taxRatePct,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            String hsnCode) {}

    public record StatusHistoryResponse(
            OrderStatus fromStatus, OrderStatus toStatus, String note, Instant at) {}

    public record OrderResponse(
            UUID id,
            String orderNumber,
            OrderStatus status,
            List<OrderItemResponse> items,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            /** Populated for an intra-state supply; zero when IGST applies. */
            BigDecimal cgstTotal,
            BigDecimal sgstTotal,
            /** Populated for an inter-state supply; zero when CGST/SGST apply. */
            BigDecimal igstTotal,
            BigDecimal taxTotal,
            BigDecimal shippingFee,
            BigDecimal grandTotal,
            String currency,
            ShippingAddress shipTo,
            Instant placedAt,
            String customerNote,
            List<StatusHistoryResponse> history) {}

    public record ShippingAddress(
            String recipientName, String phone, String line1, String line2,
            String landmark, String city, String state, String pincode, String country) {}

    public record OrderSummaryResponse(
            UUID id, String orderNumber, OrderStatus status,
            BigDecimal grandTotal, String currency, int itemCount, Instant placedAt) {}

    /** Paginated admin order list. Shape matches the frontend's generic PageResponse<T>. */
    public record OrderPageResponse(
            List<OrderSummaryResponse> content,
            int page, int size, long totalElements, int totalPages,
            boolean first, boolean last) {}
}
