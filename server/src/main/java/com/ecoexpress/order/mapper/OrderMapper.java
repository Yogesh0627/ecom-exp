package com.ecoexpress.order.mapper;

import com.ecoexpress.order.domain.Address;
import com.ecoexpress.order.domain.Order;
import com.ecoexpress.order.domain.OrderItem;
import com.ecoexpress.order.domain.OrderStatusHistory;
import com.ecoexpress.order.dto.OrderDtos.AddressResponse;
import com.ecoexpress.order.dto.OrderDtos.OrderItemResponse;
import com.ecoexpress.order.dto.OrderDtos.OrderResponse;
import com.ecoexpress.order.dto.OrderDtos.OrderSummaryResponse;
import com.ecoexpress.order.dto.OrderDtos.ShippingAddress;
import com.ecoexpress.order.dto.OrderDtos.StatusHistoryResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    /**
     * Renders a line from its SNAPSHOT columns, never the live variant.
     *
     * <p>This is the whole point of snapshotting: reaching through to
     * {@code item.getVariant().getProduct().getName()} here would silently rewrite old
     * invoices whenever a product is renamed or repriced.
     */
    public OrderItemResponse toItem(OrderItem i) {
        return new OrderItemResponse(
                i.getId(),
                i.getVariant().getId(),
                i.getProductNameSnapshot(),
                i.getVariantNameSnapshot(),
                i.getSkuSnapshot(),
                i.getImageUrlSnapshot(),
                i.getQty(),
                i.getUnitPrice(),
                i.getDiscountAmount(),
                i.getTaxRatePct(),
                i.getTaxAmount(),
                i.getLineTotal(),
                i.getHsnCode());
    }

    public OrderResponse toOrder(Order o, List<OrderStatusHistory> history) {
        return new OrderResponse(
                o.getId(), o.getOrderNumber(), o.getStatus(),
                o.getItems().stream().map(this::toItem).toList(),
                o.getSubtotal(), o.getDiscountTotal(),
                o.getCgstTotal(), o.getSgstTotal(), o.getIgstTotal(),
                o.getTaxTotal(), o.getShippingFee(), o.getGrandTotal(), o.getCurrency(),
                new ShippingAddress(o.getShipRecipientName(), o.getShipPhone(),
                        o.getShipLine1(), o.getShipLine2(), o.getShipLandmark(),
                        o.getShipCity(), o.getShipState(), o.getShipPincode(), o.getShipCountry()),
                o.getPlacedAt(), o.getCustomerNote(),
                history == null ? List.of() : history.stream().map(this::toHistory).toList());
    }

    public OrderSummaryResponse toSummary(Order o) {
        return new OrderSummaryResponse(o.getId(), o.getOrderNumber(), o.getStatus(),
                o.getGrandTotal(), o.getCurrency(), o.getItems().size(), o.getPlacedAt());
    }

    public StatusHistoryResponse toHistory(OrderStatusHistory h) {
        return new StatusHistoryResponse(h.getFromStatus(), h.getToStatus(), h.getNote(),
                h.getCreatedAt());
    }

    public AddressResponse toAddress(Address a) {
        return new AddressResponse(a.getId(), a.getLabel(), a.getRecipientName(), a.getPhone(),
                a.getLine1(), a.getLine2(), a.getLandmark(), a.getCity(), a.getState(),
                a.getPincode(), a.getCountry(), a.getType(),
                Boolean.TRUE.equals(a.getIsDefault()));
    }
}
