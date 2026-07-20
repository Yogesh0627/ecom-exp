package com.ecoexpress.inventory.api;

import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.inventory.domain.Inventory;
import com.ecoexpress.inventory.domain.StockTransactionType;
import com.ecoexpress.inventory.domain.Warehouse;
import com.ecoexpress.inventory.repository.InventoryRepository;
import com.ecoexpress.inventory.repository.WarehouseRepository;
import com.ecoexpress.inventory.service.InventoryService;
import com.ecoexpress.catalog.repository.ProductVariantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Inventory operations (PRD §6 — admin: inventory & batch tracking).
 *
 * <p>Availability is public — the storefront must show "in stock". Everything that moves
 * stock needs {@code inventory:write}.
 */
@Tag(name = "Inventory")
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductVariantRepository variantRepository;

    public record StockRow(UUID inventoryId, String warehouseCode, String sku,
                           int onHand, int reserved, int available, boolean belowReorderPoint) {}

    public record ReceiveRequest(
            @NotNull UUID variantId,
            @NotNull UUID warehouseId,
            @NotBlank String lotNo,
            @Min(1) int qty,
            @NotNull BigDecimal costPrice,
            LocalDate expiryDate) {}

    public record ReserveRequest(
            @NotNull UUID variantId,
            @NotNull UUID warehouseId,
            @Min(1) int qty,
            UUID orderRef) {}

    public record WriteOffRequest(
            @NotNull UUID variantId,
            @NotNull UUID warehouseId,
            @NotNull UUID batchId,
            @Min(1) int qty,
            @NotNull StockTransactionType type,
            String note) {}

    public record CreateWarehouseRequest(
            @NotBlank String code, @NotBlank String name,
            String city, String state, String pincode) {}

    public record WarehouseRow(UUID id, String code, String name, String city, String state) {}

    @Operation(summary = "Available units of a variant across all warehouses")
    @GetMapping("/availability/{variantId}")
    public ResponseEntity<Integer> availability(@PathVariable UUID variantId) {
        return ResponseEntity.ok(inventoryService.availableFor(variantId));
    }

    @Operation(summary = "Stock rows for a variant")
    @GetMapping("/variant/{variantId}")
    @PreAuthorize("hasAuthority('inventory:read')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<StockRow>> byVariant(@PathVariable UUID variantId) {
        return ResponseEntity.ok(inventoryRepository.findByVariant(variantId).stream()
                .map(this::toRow).toList());
    }

    @Operation(summary = "Stock at or below its reorder point")
    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('inventory:read')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<StockRow>> lowStock() {
        return ResponseEntity.ok(inventoryRepository.findBelowReorderPoint().stream()
                .map(this::toRow).toList());
    }

    /**
     * Rows where the cached on-hand disagrees with the replayed ledger. Always expected
     * to be empty — this endpoint exists so "inventory accuracy" can be checked rather
     * than assumed.
     */
    @Operation(summary = "Audit: cached stock vs the ledger")
    @GetMapping("/ledger-drift")
    @PreAuthorize("hasAuthority('inventory:read')")
    public ResponseEntity<List<Object[]>> drift() {
        return ResponseEntity.ok(inventoryService.findLedgerDrift());
    }

    @Operation(summary = "Receive stock from a supplier")
    @PostMapping("/receive")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<UUID> receive(@Valid @RequestBody ReceiveRequest r) {
        var batch = inventoryService.receiveStock(r.variantId(), r.warehouseId(), r.lotNo(),
                r.qty(), r.costPrice(), r.expiryDate(), null);
        return ResponseEntity.ok(batch.getId());
    }

    @Operation(summary = "Reserve stock")
    @PostMapping("/reserve")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<Void> reserve(@Valid @RequestBody ReserveRequest r) {
        inventoryService.reserve(r.variantId(), r.warehouseId(), r.qty(), r.orderRef());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Release a reservation")
    @PostMapping("/release")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<Void> release(@Valid @RequestBody ReserveRequest r) {
        inventoryService.release(r.variantId(), r.warehouseId(), r.qty(), r.orderRef());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ship reserved stock (FEFO)")
    @PostMapping("/ship")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<Void> ship(@Valid @RequestBody ReserveRequest r) {
        inventoryService.commitSale(r.variantId(), r.warehouseId(), r.qty(), r.orderRef());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Write off damaged or expired stock")
    @PostMapping("/write-off")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<Void> writeOff(@Valid @RequestBody WriteOffRequest r) {
        inventoryService.writeOff(r.variantId(), r.warehouseId(), r.batchId(), r.qty(),
                r.type(), r.note());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List active warehouses")
    @GetMapping("/warehouses")
    @PreAuthorize("hasAuthority('inventory:read')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<WarehouseRow>> warehouses() {
        return ResponseEntity.ok(warehouseRepository.findByIsActiveTrue().stream()
                .map(w -> new WarehouseRow(w.getId(), w.getCode(), w.getName(), w.getCity(), w.getState()))
                .toList());
    }

    @Operation(summary = "Create a warehouse")
    @PostMapping("/warehouses")
    @PreAuthorize("hasAuthority('warehouse:write')")
    @Transactional
    public ResponseEntity<UUID> createWarehouse(@Valid @RequestBody CreateWarehouseRequest r) {
        Warehouse w = warehouseRepository.save(Warehouse.builder()
                .code(r.code()).name(r.name()).city(r.city())
                .state(r.state()).pincode(r.pincode()).isActive(true).build());
        return ResponseEntity.ok(w.getId());
    }

    /**
     * Creates the stock row for a variant at a warehouse, starting at zero.
     * Idempotent: calling it again returns the existing row rather than colliding with
     * inventory_warehouse_variant_uq.
     */
    @Operation(summary = "Stock a variant at a warehouse")
    @PostMapping("/stock-item")
    @PreAuthorize("hasAuthority('inventory:write')")
    @Transactional
    public ResponseEntity<UUID> stockItem(@Valid @RequestBody ReserveRequest r) {
        var existing = inventoryRepository.find(r.variantId(), r.warehouseId());
        if (existing.isPresent()) {
            return ResponseEntity.ok(existing.get().getId());
        }

        var variant = variantRepository.findById(r.variantId())
                .orElseThrow(() -> new NotFoundException("No variant " + r.variantId()));
        var warehouse = warehouseRepository.findById(r.warehouseId())
                .orElseThrow(() -> new NotFoundException("No warehouse " + r.warehouseId()));

        Inventory created = inventoryRepository.save(Inventory.builder()
                .variant(variant)
                .warehouse(warehouse)
                .qtyOnHand(0)
                .qtyReserved(0)
                // The request's qty doubles as the reorder point when stocking an item.
                .reorderPoint(r.qty())
                .build());
        return ResponseEntity.ok(created.getId());
    }

    private StockRow toRow(Inventory i) {
        return new StockRow(i.getId(), i.getWarehouse().getCode(), i.getVariant().getSku(),
                i.getQtyOnHand(), i.getQtyReserved(), i.available(), i.isBelowReorderPoint());
    }
}
