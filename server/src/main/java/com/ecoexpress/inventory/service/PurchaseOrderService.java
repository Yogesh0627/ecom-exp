package com.ecoexpress.inventory.service;

import com.ecoexpress.catalog.domain.ProductVariant;
import com.ecoexpress.catalog.repository.ProductVariantRepository;
import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.inventory.domain.PurchaseOrder;
import com.ecoexpress.inventory.domain.PurchaseOrderItem;
import com.ecoexpress.inventory.domain.PurchaseOrderStatus;
import com.ecoexpress.inventory.domain.Supplier;
import com.ecoexpress.inventory.domain.Warehouse;
import com.ecoexpress.inventory.repository.PurchaseOrderRepository;
import com.ecoexpress.inventory.repository.SupplierRepository;
import com.ecoexpress.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Purchase-order lifecycle: draft → submit → receive (full or partial) → cancel.
 *
 * <p>Receiving a PO does NOT touch stock directly — it delegates to
 * {@link InventoryService#receiveStock}, so every received unit still writes a batch and a
 * ledger row. The PO layer owns the paper trail and the state machine; the inventory layer
 * owns the stock truth. Keeping that boundary is what stops two code paths from both
 * claiming to move stock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter PO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PurchaseOrderRepository poRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryService inventoryService;

    /** One requested line: which variant, how many, at what unit cost. */
    public record LineRequest(UUID variantId, int qty, BigDecimal unitCost) {}

    @Transactional
    public PurchaseOrder createDraft(UUID supplierId, UUID warehouseId, LocalDate expectedAt,
                                     String notes, List<LineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BadRequestException("A purchase order needs at least one line.");
        }
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new NotFoundException("No supplier " + supplierId));
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new NotFoundException("No warehouse " + warehouseId));

        PurchaseOrder po = PurchaseOrder.builder()
                .poNumber(nextPoNumber())
                .supplier(supplier)
                .warehouse(warehouse)
                .status(PurchaseOrderStatus.DRAFT)
                .expectedAt(expectedAt)
                .notes(notes)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        for (LineRequest line : lines) {
            if (line.qty() <= 0) {
                throw new BadRequestException("Line quantities must be greater than zero.");
            }
            if (line.unitCost() == null || line.unitCost().signum() < 0) {
                throw new BadRequestException("Unit cost cannot be negative.");
            }
            ProductVariant variant = variantRepository.findById(line.variantId())
                    .orElseThrow(() -> new NotFoundException("No variant " + line.variantId()));

            BigDecimal lineTotal = line.unitCost().multiply(BigDecimal.valueOf(line.qty()));
            po.getItems().add(PurchaseOrderItem.builder()
                    .purchaseOrder(po)
                    .variant(variant)
                    .qtyOrdered(line.qty())
                    .qtyReceived(0)
                    .unitCost(line.unitCost())
                    .lineTotal(lineTotal)
                    .build());
            subtotal = subtotal.add(lineTotal);
        }

        po.setSubtotal(subtotal);
        po.setGrandTotal(subtotal.add(po.getTaxTotal()));
        PurchaseOrder saved = poRepository.save(po);
        log.info("Created PO {} for supplier {} ({} lines, {} {})",
                saved.getPoNumber(), supplier.getCode(), saved.getItems().size(),
                saved.getGrandTotal(), saved.getCurrency());
        return saved;
    }

    @Transactional
    public PurchaseOrder submit(UUID poId) {
        PurchaseOrder po = load(poId);
        requireTransition(po, PurchaseOrderStatus.SUBMITTED);
        po.setStatus(PurchaseOrderStatus.SUBMITTED);
        return po;
    }

    @Transactional
    public PurchaseOrder cancel(UUID poId, String reason) {
        PurchaseOrder po = load(poId);
        requireTransition(po, PurchaseOrderStatus.CANCELLED);
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        po.setNotes(reason);
        // Already-received stock stays received — a cancel only stops the outstanding
        // remainder. The received units are physically here and already in the ledger.
        log.info("Cancelled PO {}", po.getPoNumber());
        return po;
    }

    /** One receipt line: how many of a PO line arrived, with lot and expiry. */
    public record ReceiptLine(UUID poItemId, int qtyReceived, String lotNo, LocalDate expiryDate) {}

    /**
     * Receives stock against a submitted PO. Supports partial receipts across several calls.
     *
     * <p>Each received line goes through {@code InventoryService.receiveStock}, which creates
     * the batch and the ledger RECEIPT row. The PO's status advances to PARTIALLY_RECEIVED or
     * RECEIVED based on whether every line is now complete.
     */
    @Transactional
    public PurchaseOrder receive(UUID poId, List<ReceiptLine> receipts) {
        PurchaseOrder po = load(poId);
        if (!po.getStatus().canReceive()) {
            throw new BadRequestException(
                    "Cannot receive against a PO that is " + po.getStatus() + ".");
        }
        if (receipts == null || receipts.isEmpty()) {
            throw new BadRequestException("Nothing to receive.");
        }

        for (ReceiptLine r : receipts) {
            if (r.qtyReceived() <= 0) {
                throw new BadRequestException("Received quantity must be greater than zero.");
            }
            PurchaseOrderItem item = po.getItems().stream()
                    .filter(i -> i.getId().equals(r.poItemId()))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(
                            "PO line " + r.poItemId() + " is not on PO " + po.getPoNumber()));

            // Over-receipt is a data-entry error or a dispute — refuse, do not silently
            // accept (mirrors po_item_received_chk, but as a clean 400 naming the line).
            if (item.getQtyReceived() + r.qtyReceived() > item.getQtyOrdered()) {
                throw new BadRequestException("Receiving " + r.qtyReceived()
                        + " would exceed the " + item.outstanding() + " still outstanding on this line.");
            }

            // Delegates to the ledger-writing path: batch + RECEIPT transaction, tied to this PO.
            inventoryService.receiveStock(
                    item.getVariant().getId(), po.getWarehouse().getId(),
                    r.lotNo(), r.qtyReceived(), item.getUnitCost(), r.expiryDate(), po.getId());

            item.setQtyReceived(item.getQtyReceived() + r.qtyReceived());
        }

        po.setStatus(po.isFullyReceived()
                ? PurchaseOrderStatus.RECEIVED
                : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        if (po.getStatus() == PurchaseOrderStatus.RECEIVED) {
            po.setReceivedAt(Instant.now());
        }
        log.info("PO {} now {}", po.getPoNumber(), po.getStatus());
        return po;
    }

    private PurchaseOrder load(UUID poId) {
        return poRepository.findByIdWithItems(poId)
                .orElseThrow(() -> new NotFoundException("No purchase order " + poId));
    }

    private void requireTransition(PurchaseOrder po, PurchaseOrderStatus target) {
        if (!po.getStatus().canTransitionTo(target)) {
            throw new BadRequestException(
                    "Cannot move a PO from " + po.getStatus() + " to " + target + ".");
        }
    }

    private String nextPoNumber() {
        LocalDate today = LocalDate.now(IST);
        Instant dayStart = today.atStartOfDay(IST).toInstant();
        long todayCount = poRepository.countSince(dayStart);
        return "PO-%s-%04d".formatted(today.format(PO_DATE), todayCount + 1);
    }
}
