package com.ecoexpress.inventory.service;

import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.inventory.domain.Inventory;
import com.ecoexpress.inventory.domain.InventoryBatch;
import com.ecoexpress.inventory.domain.StockTransaction;
import com.ecoexpress.inventory.domain.StockTransactionType;
import com.ecoexpress.inventory.exception.InsufficientStockException;
import com.ecoexpress.inventory.repository.InventoryBatchRepository;
import com.ecoexpress.inventory.repository.InventoryRepository;
import com.ecoexpress.inventory.repository.StockTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * All stock movement goes through here.
 *
 * <p>Two rules hold for every method that changes stock:
 * <ol>
 *   <li><b>Lock the row first.</b> Reads that lead to a decision ("is there enough?")
 *       use SELECT ... FOR UPDATE. Checking availability without the lock is the classic
 *       oversell: two requests both read 1 unit, both say yes.</li>
 *   <li><b>Write a ledger row in the same transaction.</b> {@code inventory.qty_on_hand}
 *       is only a cache of {@code stock_transactions}. If the two are written apart, they
 *       can diverge and "inventory accuracy" (PRD §2) becomes a claim rather than a fact.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryBatchRepository batchRepository;
    private final StockTransactionRepository ledger;
    private final com.ecoexpress.inventory.repository.LowStockAlertRepository lowStockAlertRepository;

    /**
     * Reserves stock for a cart/order. Does not move physical goods.
     *
     * @throws InsufficientStockException when fewer than {@code qty} units are available
     */
    @Transactional
    public void reserve(UUID variantId, UUID warehouseId, int qty, UUID orderRef) {
        requirePositive(qty);
        // FOR UPDATE: serialises concurrent reservations of the same variant.
        Inventory inv = lockOrThrow(variantId, warehouseId);

        if (inv.available() < qty) {
            // Thrown before any write, so the transaction rolls back cleanly.
            throw new InsufficientStockException(
                    "Only " + inv.available() + " left, " + qty + " requested.");
        }

        inv.setQtyReserved(inv.getQtyReserved() + qty);
        writeLedger(inv, null, StockTransactionType.RESERVATION, qty, "ORDER", orderRef, null);
        log.debug("Reserved {} of variant {} (available now {})", qty, variantId, inv.available());
    }

    /**
     * Releases a reservation — cart expired, order cancelled before shipping.
     *
     * <p>Clamped rather than throwing: releasing more than is reserved means we already
     * lost track, and refusing to release would strand the stock permanently. Log loudly
     * and free what we can.
     */
    @Transactional
    public void release(UUID variantId, UUID warehouseId, int qty, UUID orderRef) {
        requirePositive(qty);
        Inventory inv = lockOrThrow(variantId, warehouseId);

        int toRelease = Math.min(qty, inv.getQtyReserved());
        if (toRelease < qty) {
            log.warn("Release of {} for variant {} exceeds reserved {} — releasing {} and "
                            + "continuing; reservation accounting has drifted.",
                    qty, variantId, inv.getQtyReserved(), toRelease);
        }
        if (toRelease == 0) {
            return;
        }

        inv.setQtyReserved(inv.getQtyReserved() - toRelease);
        writeLedger(inv, null, StockTransactionType.RELEASE, -toRelease, "ORDER", orderRef, null);
    }

    /**
     * Ships reserved stock: the goods physically leave.
     *
     * <p>Consumes {@code qty} from both on_hand and reserved, and draws down batches
     * FEFO so the earliest-expiring stock goes out first.
     */
    @Transactional
    public void commitSale(UUID variantId, UUID warehouseId, int qty, UUID orderRef) {
        requirePositive(qty);
        Inventory inv = lockOrThrow(variantId, warehouseId);

        if (inv.getQtyReserved() < qty) {
            throw new BadRequestException(
                    "Cannot ship " + qty + ": only " + inv.getQtyReserved() + " reserved.");
        }
        if (inv.getQtyOnHand() < qty) {
            // Should be unreachable — reserved <= on_hand is a CHECK constraint. If it
            // happens, the ledger and the shelf disagree and a human must look.
            throw new IllegalStateException(
                    "on_hand " + inv.getQtyOnHand() + " < reserved qty " + qty
                            + " for inventory " + inv.getId() + " — stock accounting is broken.");
        }

        List<BatchDraw> draws = allocateFefo(inv, qty);

        inv.setQtyOnHand(inv.getQtyOnHand() - qty);
        inv.setQtyReserved(inv.getQtyReserved() - qty);

        // One ledger row per batch: a recall needs to know which lot shipped to whom.
        for (BatchDraw d : draws) {
            d.batch().setQtyRemaining(d.batch().getQtyRemaining() - d.qty());
            writeLedger(inv, d.batch(), StockTransactionType.SALE, -d.qty(), "ORDER", orderRef, null);
        }
        // The reservation is consumed by the shipment, so unwind it in the ledger too.
        writeLedger(inv, null, StockTransactionType.RELEASE, -qty, "ORDER", orderRef,
                "consumed by shipment");

        evaluateLowStock(inv);
        log.info("Shipped {} of variant {} from {} batch(es)", qty, variantId, draws.size());
    }

    /** Receives stock from a supplier: creates a batch and increases on-hand. */
    @Transactional
    public InventoryBatch receiveStock(UUID variantId, UUID warehouseId, String lotNo, int qty,
                                       java.math.BigDecimal costPrice,
                                       java.time.LocalDate expiryDate, UUID supplierRef) {
        requirePositive(qty);
        Inventory inv = lockOrThrow(variantId, warehouseId);

        if (batchRepository.findByInventoryIdAndLotNo(inv.getId(), lotNo).isPresent()) {
            // batches_lot_uq would reject this anyway; a 400 is clearer than a 500.
            throw new BadRequestException("Lot '" + lotNo + "' has already been received.");
        }

        InventoryBatch batch = InventoryBatch.builder()
                .inventory(inv)
                .lotNo(lotNo)
                .qtyReceived(qty)
                .qtyRemaining(qty)
                .costPrice(costPrice)
                .expiryDate(expiryDate)
                .build();
        batchRepository.save(batch);

        inv.setQtyOnHand(inv.getQtyOnHand() + qty);
        writeLedger(inv, batch, StockTransactionType.RECEIPT, qty, "PURCHASE_ORDER", supplierRef, null);

        evaluateLowStock(inv);
        log.info("Received {} of variant {} as lot {} (expires {})", qty, variantId, lotNo, expiryDate);
        return batch;
    }

    /** Writes off expired or damaged stock from a specific batch. */
    @Transactional
    public void writeOff(UUID variantId, UUID warehouseId, UUID batchId, int qty,
                         StockTransactionType type, String note) {
        requirePositive(qty);
        if (type != StockTransactionType.DAMAGE && type != StockTransactionType.EXPIRY) {
            throw new BadRequestException("Write-off must be DAMAGE or EXPIRY.");
        }
        Inventory inv = lockOrThrow(variantId, warehouseId);
        InventoryBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new NotFoundException("No batch " + batchId));

        if (batch.getQtyRemaining() < qty) {
            throw new BadRequestException(
                    "Batch has only " + batch.getQtyRemaining() + " left.");
        }
        // Writing off stock that is promised to a customer would let available() go
        // negative. Free the reservation first, then write off.
        if (inv.available() < qty) {
            throw new BadRequestException(
                    "Only " + inv.available() + " unreserved units available to write off; "
                            + inv.getQtyReserved() + " are reserved for orders.");
        }

        batch.setQtyRemaining(batch.getQtyRemaining() - qty);
        inv.setQtyOnHand(inv.getQtyOnHand() - qty);
        writeLedger(inv, batch, type, -qty, "ADJUSTMENT", null, note);
        evaluateLowStock(inv);
        log.info("Wrote off {} of variant {} from batch {} ({})", qty, variantId, batchId, type);
    }

    /**
     * Applies a reason-coded stock adjustment through the ledger.
     *
     * <p>The adjustment row is written by the caller (with its approval fields); this method
     * moves the actual stock and records the ledger entry, so the correction is auditable the
     * same way a sale or receipt is. A negative delta cannot drive available stock below what
     * is reserved for orders.
     */
    @Transactional
    public void applyAdjustment(UUID variantId, UUID warehouseId, int qtyDelta, UUID adjustmentRef,
                                String note) {
        if (qtyDelta == 0) {
            throw new BadRequestException("An adjustment cannot be zero.");
        }
        Inventory inv = lockOrThrow(variantId, warehouseId);

        if (qtyDelta < 0 && inv.available() < -qtyDelta) {
            throw new BadRequestException("Cannot remove " + (-qtyDelta) + ": only "
                    + inv.available() + " unreserved units are available.");
        }

        inv.setQtyOnHand(inv.getQtyOnHand() + qtyDelta);
        writeLedger(inv, null, StockTransactionType.ADJUSTMENT, qtyDelta, "ADJUSTMENT",
                adjustmentRef, note);
        evaluateLowStock(inv);
        log.info("Applied adjustment {} to variant {} (on-hand now {})",
                qtyDelta, variantId, inv.getQtyOnHand());
    }

    /**
     * Opens or resolves the low-stock alert for a stock row after its on-hand changes.
     *
     * <p>At most one OPEN alert per inventory row (a partial unique index enforces it), so a
     * product hovering at its reorder point does not spam an alert on every movement. When
     * stock recovers above the reorder point the open alert is resolved rather than deleted —
     * the history of "we ran low on X three times last month" is worth keeping.
     */
    private void evaluateLowStock(Inventory inv) {
        var open = lowStockAlertRepository.findOpenForInventory(inv.getId());
        boolean low = inv.isBelowReorderPoint();

        if (low && open.isEmpty()) {
            lowStockAlertRepository.save(com.ecoexpress.inventory.domain.LowStockAlert.builder()
                    .inventory(inv)
                    .qtyAtTrigger(inv.getQtyOnHand())
                    .reorderPoint(inv.getReorderPoint())
                    .build());
            log.info("Low-stock alert opened for variant {} at {} (reorder point {})",
                    inv.getVariant().getId(), inv.getQtyOnHand(), inv.getReorderPoint());
        } else if (!low && open.isPresent()) {
            open.get().setResolvedAt(java.time.Instant.now());
            log.info("Low-stock alert resolved for variant {} (recovered to {})",
                    inv.getVariant().getId(), inv.getQtyOnHand());
        }
    }

    @Transactional(readOnly = true)
    public int availableFor(UUID variantId) {
        return inventoryRepository.totalAvailable(variantId);
    }

    /**
     * Independent audit: replays the ledger and compares it against the cached on-hand.
     * A non-empty result is a real bug, not a warning.
     */
    @Transactional(readOnly = true)
    public List<Object[]> findLedgerDrift() {
        return inventoryRepository.findLedgerDrift();
    }

    /**
     * Picks batches first-expiry-first-out.
     *
     * <p>Runs before any mutation so an unsatisfiable request throws without leaving a
     * half-drawn batch behind.
     */
    private List<BatchDraw> allocateFefo(Inventory inv, int qty) {
        List<BatchDraw> draws = new ArrayList<>();
        int remaining = qty;

        for (InventoryBatch batch : batchRepository.findFefoOrder(inv.getId())) {
            if (remaining == 0) {
                break;
            }
            int take = Math.min(remaining, batch.getQtyRemaining());
            draws.add(new BatchDraw(batch, take));
            remaining -= take;
        }

        if (remaining > 0) {
            // on_hand says the stock exists but no batch holds it: the batches and the
            // rollup disagree. Refuse rather than ship untracked goods — a recall would
            // have no lot to trace.
            throw new IllegalStateException(
                    "Batches hold " + (qty - remaining) + " units but on_hand claims "
                            + qty + " for inventory " + inv.getId() + ".");
        }
        return draws;
    }

    private Inventory lockOrThrow(UUID variantId, UUID warehouseId) {
        return inventoryRepository.findForUpdate(variantId, warehouseId)
                .orElseThrow(() -> new NotFoundException(
                        "Variant " + variantId + " is not stocked at warehouse " + warehouseId));
    }

    private void writeLedger(Inventory inv, InventoryBatch batch, StockTransactionType type,
                             int delta, String refType, UUID refId, String note) {
        ledger.save(StockTransaction.builder()
                .inventory(inv)
                .batch(batch)
                .type(type)
                .qtyDelta(delta)
                .refType(refType)
                .refId(refId)
                .note(note)
                .build());
    }

    private void requirePositive(int qty) {
        if (qty <= 0) {
            throw new BadRequestException("Quantity must be greater than zero.");
        }
    }

    /** How much to draw from one batch. */
    private record BatchDraw(InventoryBatch batch, int qty) {}
}
