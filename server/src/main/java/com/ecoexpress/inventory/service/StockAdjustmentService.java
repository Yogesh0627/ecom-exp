package com.ecoexpress.inventory.service;

import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.identity.domain.User;
import com.ecoexpress.inventory.domain.AdjustmentReason;
import com.ecoexpress.inventory.domain.Inventory;
import com.ecoexpress.inventory.domain.StockAdjustment;
import com.ecoexpress.inventory.repository.InventoryRepository;
import com.ecoexpress.inventory.repository.StockAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Reason-coded stock adjustments with a two-step create-then-approve flow.
 *
 * <p>Stock does not move when an adjustment is <b>requested</b> — only when it is
 * <b>approved</b>. Separating the two means shrinkage (damage, theft, count corrections)
 * always has a named approver on record before a single unit leaves the books, which is the
 * control that keeps quiet losses from being quiet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockAdjustmentService {

    private final StockAdjustmentRepository adjustmentRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;

    /** Creates a PENDING adjustment. No stock moves yet. */
    @Transactional
    public StockAdjustment request(UUID inventoryId, AdjustmentReason reason, int qtyDelta,
                                   String note) {
        if (qtyDelta == 0) {
            throw new BadRequestException("An adjustment cannot be zero.");
        }
        Inventory inv = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new NotFoundException("No inventory " + inventoryId));

        StockAdjustment adjustment = adjustmentRepository.save(StockAdjustment.builder()
                .inventory(inv)
                .reason(reason)
                .qtyDelta(qtyDelta)
                .note(note)
                .build());
        log.info("Stock adjustment requested: {} on inventory {} ({}), pending approval",
                qtyDelta, inventoryId, reason);
        return adjustment;
    }

    /**
     * Approves a pending adjustment and moves the stock through the ledger.
     *
     * <p>Idempotent on the approval flag: approving an already-approved adjustment is a
     * no-op, so a double-click cannot apply the stock change twice.
     */
    @Transactional
    public StockAdjustment approve(UUID adjustmentId, User approver) {
        StockAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new NotFoundException("No adjustment " + adjustmentId));

        if (adjustment.isApproved()) {
            log.info("Adjustment {} already approved — no-op", adjustmentId);
            return adjustment;
        }

        Inventory inv = adjustment.getInventory();
        // Applies through InventoryService so the correction writes a ledger row and
        // re-evaluates the low-stock alert, exactly like any other stock movement.
        inventoryService.applyAdjustment(
                inv.getVariant().getId(), inv.getWarehouse().getId(),
                adjustment.getQtyDelta(), adjustment.getId(),
                adjustment.getReason() + (adjustment.getNote() == null ? "" : ": " + adjustment.getNote()));

        adjustment.setApprovedBy(approver);
        adjustment.setApprovedAt(Instant.now());
        log.info("Adjustment {} approved by {} and applied", adjustmentId,
                approver == null ? "system" : approver.getId());
        return adjustment;
    }
}
