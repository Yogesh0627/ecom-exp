package com.ecoexpress.inventory.api;

import com.ecoexpress.common.security.AuthenticatedUser;
import com.ecoexpress.identity.repository.UserRepository;
import com.ecoexpress.inventory.domain.AdjustmentReason;
import com.ecoexpress.inventory.domain.LowStockAlert;
import com.ecoexpress.inventory.domain.PurchaseOrder;
import com.ecoexpress.inventory.domain.StockAdjustment;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.inventory.domain.PurchaseOrderStatus;
import com.ecoexpress.inventory.repository.LowStockAlertRepository;
import com.ecoexpress.inventory.repository.PurchaseOrderRepository;
import com.ecoexpress.inventory.service.PurchaseOrderService;
import com.ecoexpress.inventory.service.PurchaseOrderService.LineRequest;
import com.ecoexpress.inventory.service.PurchaseOrderService.ReceiptLine;
import com.ecoexpress.inventory.service.StockAdjustmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory admin (PRD §9): purchase orders, reason-coded stock adjustments, low-stock
 * alerts. All operations require inventory permissions — nothing here is customer-facing.
 */
@Tag(name = "Inventory Admin")
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService poService;
    private final StockAdjustmentService adjustmentService;
    private final LowStockAlertRepository lowStockAlertRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final UserRepository userRepository;

    // ---------- requests ----------

    public record PoLineDto(@NotNull UUID variantId, int qty, @NotNull BigDecimal unitCost) {}

    public record CreatePoRequest(
            @NotNull UUID supplierId,
            @NotNull UUID warehouseId,
            LocalDate expectedAt,
            String notes,
            @NotEmpty @Valid List<PoLineDto> lines) {}

    public record ReceiptLineDto(@NotNull UUID poItemId, int qtyReceived,
                                 String lotNo, LocalDate expiryDate) {}

    public record ReceivePoRequest(@NotEmpty @Valid List<ReceiptLineDto> receipts) {}

    public record CancelPoRequest(String reason) {}

    public record AdjustmentRequest(
            @NotNull UUID inventoryId,
            @NotNull AdjustmentReason reason,
            int qtyDelta,
            String note) {}

    // ---------- purchase orders ----------

    @Operation(summary = "List purchase orders, optionally by status")
    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('inventory:read')")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> listPos(
            @RequestParam(required = false) PurchaseOrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        var page = status == null
                ? purchaseOrderRepository.findAllByOrderByCreatedAtDesc(pageable)
                : purchaseOrderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        List<Map<String, Object>> content = page.getContent().stream().map(po -> Map.<String, Object>of(
                "id", po.getId(),
                "poNumber", po.getPoNumber(),
                "status", po.getStatus().name(),
                "supplierName", po.getSupplier().getName(),
                "warehouseCode", po.getWarehouse().getCode(),
                "grandTotal", po.getGrandTotal(),
                "lineCount", po.getItems().size(),
                "expectedAt", po.getExpectedAt() == null ? "" : po.getExpectedAt().toString())).toList();
        return ResponseEntity.ok(Map.of(
                "content", content, "page", page.getNumber(), "size", page.getSize(),
                "totalElements", page.getTotalElements(), "totalPages", page.getTotalPages(),
                "first", page.isFirst(), "last", page.isLast()));
    }

    @Operation(summary = "Get one purchase order with its lines")
    @GetMapping("/purchase-orders/{id}")
    @PreAuthorize("hasAuthority('inventory:read')")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getPo(@PathVariable UUID id) {
        var po = purchaseOrderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new NotFoundException("No purchase order " + id));
        return ResponseEntity.ok(poView(po));
    }

    @Operation(summary = "Create a draft purchase order")
    @PostMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<Map<String, Object>> createPo(@Valid @RequestBody CreatePoRequest r) {
        List<LineRequest> lines = r.lines().stream()
                .map(l -> new LineRequest(l.variantId(), l.qty(), l.unitCost())).toList();
        PurchaseOrder po = poService.createDraft(r.supplierId(), r.warehouseId(),
                r.expectedAt(), r.notes(), lines);
        return ResponseEntity.status(HttpStatus.CREATED).body(poView(po));
    }

    @Operation(summary = "Submit a draft PO to the supplier")
    @PostMapping("/purchase-orders/{id}/submit")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<Map<String, Object>> submitPo(@PathVariable UUID id) {
        return ResponseEntity.ok(poView(poService.submit(id)));
    }

    @Operation(summary = "Receive stock against a PO (full or partial)")
    @PostMapping("/purchase-orders/{id}/receive")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<Map<String, Object>> receivePo(
            @PathVariable UUID id, @Valid @RequestBody ReceivePoRequest r) {
        List<ReceiptLine> receipts = r.receipts().stream()
                .map(x -> new ReceiptLine(x.poItemId(), x.qtyReceived(), x.lotNo(), x.expiryDate()))
                .toList();
        return ResponseEntity.ok(poView(poService.receive(id, receipts)));
    }

    @Operation(summary = "Cancel a PO")
    @PostMapping("/purchase-orders/{id}/cancel")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<Map<String, Object>> cancelPo(
            @PathVariable UUID id, @RequestBody(required = false) CancelPoRequest r) {
        return ResponseEntity.ok(poView(poService.cancel(id, r == null ? null : r.reason())));
    }

    // ---------- stock adjustments ----------

    @Operation(summary = "Request a reason-coded stock adjustment (no stock moves until approved)")
    @PostMapping("/adjustments")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<Map<String, Object>> requestAdjustment(
            @Valid @RequestBody AdjustmentRequest r) {
        StockAdjustment a = adjustmentService.request(r.inventoryId(), r.reason(), r.qtyDelta(), r.note());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", a.getId(), "status", "PENDING", "qtyDelta", a.getQtyDelta()));
    }

    @Operation(summary = "Approve a pending adjustment and apply the stock change")
    @PostMapping("/adjustments/{id}/approve")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<Map<String, Object>> approveAdjustment(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        var user = userRepository.findById(actor.getId()).orElse(null);
        StockAdjustment a = adjustmentService.approve(id, user);
        return ResponseEntity.ok(Map.of(
                "id", a.getId(), "status", "APPROVED", "approvedAt", a.getApprovedAt()));
    }

    // ---------- low-stock alerts ----------

    @Operation(summary = "All open low-stock alerts")
    @GetMapping("/low-stock-alerts")
    @PreAuthorize("hasAuthority('inventory:read')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> openAlerts() {
        List<Map<String, Object>> alerts = lowStockAlertRepository.findAllOpen().stream()
                .map(this::alertView).toList();
        return ResponseEntity.ok(alerts);
    }

    private Map<String, Object> poView(PurchaseOrder po) {
        return Map.of(
                "id", po.getId(),
                "poNumber", po.getPoNumber(),
                "status", po.getStatus().name(),
                "grandTotal", po.getGrandTotal(),
                "lines", po.getItems().stream().map(i -> Map.of(
                        "poItemId", i.getId(),
                        "sku", i.getVariant().getSku(),
                        "qtyOrdered", i.getQtyOrdered(),
                        "qtyReceived", i.getQtyReceived(),
                        "outstanding", i.outstanding())).toList());
    }

    private Map<String, Object> alertView(LowStockAlert a) {
        return Map.of(
                "id", a.getId(),
                "sku", a.getInventory().getVariant().getSku(),
                "warehouse", a.getInventory().getWarehouse().getCode(),
                "qtyAtTrigger", a.getQtyAtTrigger(),
                "reorderPoint", a.getReorderPoint(),
                "triggeredAt", a.getTriggeredAt());
    }
}
