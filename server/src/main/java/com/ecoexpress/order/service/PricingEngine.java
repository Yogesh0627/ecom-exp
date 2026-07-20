package com.ecoexpress.order.service;

import com.ecoexpress.cart.domain.CartItem;
import com.ecoexpress.catalog.domain.ProductVariant;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Order pricing and Indian GST.
 *
 * <p><b>Rounding.</b> Everything is BigDecimal with HALF_UP at 2dp, applied per line and
 * then summed — never summed at full precision and rounded once at the end. The invoice
 * shows per-line figures, so the printed lines must add up to the printed total. Rounding
 * only the total leaves an invoice whose own numbers disagree, and customers do check.
 *
 * <p><b>Place of supply.</b> For goods, GST follows the delivery state. Same state as the
 * dispatching warehouse means CGST+SGST (each half the rate); different state means IGST
 * (the full rate). Never both — orders_gst_split_chk enforces that at the database.
 *
 * <p><b>Prices are tax-exclusive here.</b> GST is added on top of the catalog price. If
 * the business decides shelf prices are tax-inclusive, this is the one class that changes
 * — which is why the split lives here and not scattered across checkout.
 */
@Component
public class PricingEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    /**
     * A priced line.
     *
     * @param taxableValue  qty * unitPrice - discount, i.e. what GST applies to
     * @param lineTotal     taxableValue + tax
     */
    public record PricedLine(
            ProductVariant variant,
            int qty,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal taxRatePct,
            BigDecimal taxableValue,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            String hsnCode) {}

    /**
     * A priced order.
     *
     * @param interState true when IGST applies
     */
    public record PricedOrder(
            List<PricedLine> lines,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal cgstTotal,
            BigDecimal sgstTotal,
            BigDecimal igstTotal,
            BigDecimal taxTotal,
            BigDecimal shippingFee,
            BigDecimal grandTotal,
            boolean interState) {}

    /**
     * @param warehouseState state the goods dispatch FROM
     * @param deliveryState  state the goods deliver TO (the place of supply)
     */
    public PricedOrder price(List<CartItem> items, String warehouseState, String deliveryState,
                             BigDecimal shippingFee, BigDecimal orderDiscount) {

        boolean interState = isInterState(warehouseState, deliveryState);

        List<PricedLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal cgstTotal = BigDecimal.ZERO;
        BigDecimal sgstTotal = BigDecimal.ZERO;
        BigDecimal igstTotal = BigDecimal.ZERO;

        for (CartItem item : items) {
            ProductVariant v = item.getVariant();
            BigDecimal unitPrice = v.getPrice();
            BigDecimal gross = money(unitPrice.multiply(BigDecimal.valueOf(item.getQty())));

            // No per-line discount yet; coupons land here when the promo module arrives.
            BigDecimal lineDiscount = BigDecimal.ZERO;
            BigDecimal taxable = money(gross.subtract(lineDiscount));

            BigDecimal rate = v.getProduct().getGstRatePct();
            BigDecimal tax = money(taxable.multiply(rate).divide(HUNDRED, 4, RoundingMode.HALF_UP));

            BigDecimal cgst = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal sgst = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal igst = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            if (interState) {
                igst = tax;
            } else {
                // Split the ROUNDED total, then give any odd paisa to CGST — so
                // cgst + sgst always equals tax exactly. Rounding each half
                // independently can produce a pair that sums to one paisa off the
                // total, which trips orders_tax_sums_chk.
                cgst = money(tax.divide(TWO, 4, RoundingMode.HALF_UP));
                sgst = money(tax.subtract(cgst));
            }

            BigDecimal lineTotal = money(taxable.add(tax));

            lines.add(new PricedLine(v, item.getQty(), unitPrice, lineDiscount, rate,
                    taxable, cgst, sgst, igst, tax, lineTotal, v.getProduct().getHsnCode()));

            subtotal = subtotal.add(gross);
            cgstTotal = cgstTotal.add(cgst);
            sgstTotal = sgstTotal.add(sgst);
            igstTotal = igstTotal.add(igst);
        }

        subtotal = money(subtotal);
        BigDecimal discountTotal = money(orderDiscount == null ? BigDecimal.ZERO : orderDiscount);
        // A discount above the subtotal would make the order negative — and
        // orders_discount_chk would reject it anyway.
        if (discountTotal.compareTo(subtotal) > 0) {
            discountTotal = subtotal;
        }

        BigDecimal taxTotal = money(cgstTotal.add(sgstTotal).add(igstTotal));
        BigDecimal shipping = money(shippingFee == null ? BigDecimal.ZERO : shippingFee);
        BigDecimal grandTotal = money(subtotal.subtract(discountTotal).add(taxTotal).add(shipping));

        return new PricedOrder(lines, subtotal, discountTotal, money(cgstTotal), money(sgstTotal),
                money(igstTotal), taxTotal, shipping, grandTotal, interState);
    }

    /**
     * Inter-state when the delivery state differs from the warehouse state.
     *
     * <p>Compared case- and whitespace-insensitively: "MH" and "mh " must not be treated
     * as different states and silently switch the tax type. A null or blank delivery
     * state falls back to intra-state rather than guessing IGST — but callers should not
     * be passing one, since ship_state is NOT NULL.
     */
    private boolean isInterState(String warehouseState, String deliveryState) {
        if (warehouseState == null || deliveryState == null) {
            return false;
        }
        return !normalise(warehouseState).equals(normalise(deliveryState));
    }

    private String normalise(String s) {
        return s.trim().toUpperCase();
    }

    private BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
