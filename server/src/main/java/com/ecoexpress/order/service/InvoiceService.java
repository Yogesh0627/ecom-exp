package com.ecoexpress.order.service;

import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.common.storage.StorageService;
import com.ecoexpress.order.domain.Invoice;
import com.ecoexpress.order.domain.Order;
import com.ecoexpress.order.domain.OrderItem;
import com.ecoexpress.order.domain.OrderStatus;
import com.ecoexpress.order.repository.InvoiceRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Tax invoices. The order is the frozen source of truth; this assigns a stable invoice number once
 * ({@link #getOrCreate}) and renders the PDF from the order + seller config on demand
 * ({@link #renderPdf}). Regenerating rather than serving a stored blob keeps the template editable
 * and stays provider-independent (no dependency on the storage adapter being able to read back).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private final InvoiceRepository invoiceRepository;
    private final StorageService storage;
    private final InvoiceProperties seller;

    /** An invoice exists only for genuinely-paid orders. */
    private static boolean invoiceable(OrderStatus s) {
        return switch (s) {
            case PAID, CONFIRMED, PACKED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, RETURNED, REFUNDED -> true;
            default -> false;
        };
    }

    /**
     * Ensures an invoice record for the order (stable number + issue date), caching a rendered PDF
     * to storage the first time for the record. Idempotent.
     */
    @Transactional
    public Invoice getOrCreate(Order order) {
        if (!invoiceable(order.getStatus())) {
            throw new BadRequestException("An invoice is available only after payment.");
        }
        return invoiceRepository.findByOrderId(order.getId()).orElseGet(() -> {
            Invoice invoice = Invoice.builder()
                    .orderId(order.getId())
                    // Gapless daily series derived from the (already gapless) order number.
                    .invoiceNumber(order.getOrderNumber().replaceFirst("^ECO-", "INV-"))
                    .build();
            invoice = invoiceRepository.save(invoice);
            try {
                byte[] pdf = renderPdf(order, invoice);
                var stored = storage.store("invoices", invoice.getInvoiceNumber() + ".pdf",
                        "application/pdf", pdf);
                invoice.setPdfKey(stored.key());
                invoice.setPdfUrl(stored.url());
            } catch (Exception e) {
                // A storage hiccup must not block issuing the invoice — it is regenerated on serve.
                log.warn("Could not cache invoice {} PDF: {}", invoice.getInvoiceNumber(), e.getMessage());
            }
            return invoice;
        });
    }

    /** Renders the invoice PDF from the order (frozen data) and its invoice record. */
    public byte[] renderPdf(Order order, Invoice invoice) {
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(20, 110, 60));
        Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
        Font cellBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

        // Seller header.
        doc.add(new Paragraph(seller.getName(), h1));
        StringBuilder sellerLine = new StringBuilder();
        if (!seller.getAddressLine().isBlank()) sellerLine.append(seller.getAddressLine());
        if (!seller.getCity().isBlank()) sellerLine.append(", ").append(seller.getCity());
        if (!seller.getState().isBlank()) sellerLine.append(", ").append(seller.getState());
        if (!seller.getPincode().isBlank()) sellerLine.append(" - ").append(seller.getPincode());
        if (sellerLine.length() > 0) doc.add(new Paragraph(sellerLine.toString(), small));
        StringBuilder ids = new StringBuilder();
        if (!seller.getGstin().isBlank()) ids.append("GSTIN: ").append(seller.getGstin());
        if (!seller.getFssai().isBlank()) ids.append(ids.length() > 0 ? "   " : "").append("FSSAI: ").append(seller.getFssai());
        if (ids.length() > 0) doc.add(new Paragraph(ids.toString(), small));

        Paragraph title = new Paragraph("TAX INVOICE", h2);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(12);
        title.setSpacingAfter(8);
        doc.add(title);

        // Invoice meta + bill-to, two columns.
        PdfPTable meta = new PdfPTable(2);
        meta.setWidthPercentage(100);
        meta.getDefaultCell().setBorder(0);
        meta.addCell(cell("Invoice No: " + invoice.getInvoiceNumber() + "\n"
                + "Date: " + DATE.format(invoice.getIssuedAt().atZone(IST)) + "\n"
                + "Order: " + order.getOrderNumber(), normal, Element.ALIGN_LEFT));
        String shipTo = order.getShipRecipientName() + "\n"
                + nn(order.getShipLine1()) + (isBlank(order.getShipLine2()) ? "" : ", " + order.getShipLine2()) + "\n"
                + order.getShipCity() + ", " + order.getShipState() + " - " + order.getShipPincode() + "\n"
                + "Phone: " + nn(order.getShipPhone());
        meta.addCell(cell("Bill / Ship To:\n" + shipTo, normal, Element.ALIGN_LEFT));
        meta.setSpacingAfter(10);
        doc.add(meta);

        // Line items.
        PdfPTable table = new PdfPTable(new float[]{4.5f, 1.2f, 0.9f, 1.4f, 1.4f, 1.0f, 1.4f, 1.6f});
        table.setWidthPercentage(100);
        for (String h : new String[]{"Item", "HSN", "Qty", "Rate", "Taxable", "GST%", "Tax", "Amount"}) {
            PdfPCell hc = new PdfPCell(new Phrase(h, cellBold));
            hc.setBackgroundColor(new Color(235, 245, 238));
            hc.setPadding(5);
            table.addCell(hc);
        }
        for (OrderItem it : order.getItems()) {
            BigDecimal taxable = it.getLineTotal().subtract(it.getTaxAmount());
            table.addCell(bodyCell(it.getProductNameSnapshot() + "\n" + it.getSkuSnapshot(), small, Element.ALIGN_LEFT));
            table.addCell(bodyCell(nn(it.getHsnCode()), normal, Element.ALIGN_CENTER));
            table.addCell(bodyCell(String.valueOf(it.getQty()), normal, Element.ALIGN_CENTER));
            table.addCell(bodyCell(money(it.getUnitPrice()), normal, Element.ALIGN_RIGHT));
            table.addCell(bodyCell(money(taxable), normal, Element.ALIGN_RIGHT));
            table.addCell(bodyCell(it.getTaxRatePct().stripTrailingZeros().toPlainString(), normal, Element.ALIGN_CENTER));
            table.addCell(bodyCell(money(it.getTaxAmount()), normal, Element.ALIGN_RIGHT));
            table.addCell(bodyCell(money(it.getLineTotal()), normal, Element.ALIGN_RIGHT));
        }
        doc.add(table);

        // Totals.
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(45);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setSpacingBefore(8);
        totalRow(totals, "Subtotal", money(order.getSubtotal()), normal);
        if (order.getDiscountTotal().signum() > 0) {
            totalRow(totals, "Discount", "- " + money(order.getDiscountTotal()), normal);
        }
        if (order.getCgstTotal().signum() > 0) totalRow(totals, "CGST", money(order.getCgstTotal()), normal);
        if (order.getSgstTotal().signum() > 0) totalRow(totals, "SGST", money(order.getSgstTotal()), normal);
        if (order.getIgstTotal().signum() > 0) totalRow(totals, "IGST", money(order.getIgstTotal()), normal);
        totalRow(totals, "Shipping", money(order.getShippingFee()), normal);
        totalRow(totals, "Grand Total", money(order.getGrandTotal()), cellBold);
        doc.add(totals);

        Paragraph foot = new Paragraph(
                "This is a computer-generated tax invoice and does not require a signature.", small);
        foot.setSpacingBefore(16);
        doc.add(foot);

        doc.close();
        return out.toByteArray();
    }

    private static void totalRow(PdfPTable t, String label, String value, Font font) {
        PdfPCell l = new PdfPCell(new Phrase(label, font));
        l.setBorder(0);
        l.setPadding(3);
        PdfPCell v = new PdfPCell(new Phrase(value, font));
        v.setBorder(0);
        v.setPadding(3);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(l);
        t.addCell(v);
    }

    private static PdfPCell cell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(0);
        c.setHorizontalAlignment(align);
        return c;
    }

    private static PdfPCell bodyCell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setPadding(5);
        c.setHorizontalAlignment(align);
        return c;
    }

    private static String money(BigDecimal v) {
        return "Rs. " + MONEY.format(v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP));
    }

    private static String nn(String s) { return s == null ? "" : s; }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
