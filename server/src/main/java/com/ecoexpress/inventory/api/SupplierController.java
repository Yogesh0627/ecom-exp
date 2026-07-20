package com.ecoexpress.inventory.api;

import com.ecoexpress.common.exception.ApiExceptions.ConflictException;
import com.ecoexpress.inventory.domain.Supplier;
import com.ecoexpress.inventory.repository.SupplierRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Suppliers for purchase orders (PRD §9). A PO must name a supplier, so this is the prerequisite
 * for the PO flow. Gated on inventory:write — the same people who raise purchase orders.
 */
@Tag(name = "Suppliers")
@RestController
@RequestMapping("/api/v1/inventory/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierRepository supplierRepository;

    public record SupplierRow(UUID id, String code, String name, String contactName,
                              String contactPhone, String gstin, String fssaiLicense,
                              String city, String state, boolean isActive) {}

    public record CreateSupplierRequest(
            @NotBlank String code, @NotBlank String name, String contactName,
            String contactEmail, String contactPhone, String gstin, String fssaiLicense,
            String addressLine, String city, String state, String pincode) {}

    @Operation(summary = "List suppliers")
    @GetMapping
    @PreAuthorize("hasAuthority('inventory:read')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<SupplierRow>> list() {
        return ResponseEntity.ok(supplierRepository.findAllByOrderByNameAsc().stream()
                .map(this::row).toList());
    }

    @Operation(summary = "Create a supplier")
    @PostMapping
    @PreAuthorize("hasAuthority('inventory:write')")
    @Transactional
    public ResponseEntity<SupplierRow> create(@Valid @RequestBody CreateSupplierRequest r) {
        if (supplierRepository.existsByCode(r.code())) {
            throw new ConflictException("A supplier with code '" + r.code() + "' exists.");
        }
        Supplier saved = supplierRepository.save(Supplier.builder()
                .code(r.code()).name(r.name()).contactName(r.contactName())
                .contactEmail(r.contactEmail()).contactPhone(r.contactPhone())
                .gstin(r.gstin()).fssaiLicense(r.fssaiLicense())
                .addressLine(r.addressLine()).city(r.city()).state(r.state()).pincode(r.pincode())
                .isActive(true).build());
        return ResponseEntity.status(HttpStatus.CREATED).body(row(saved));
    }

    private SupplierRow row(Supplier s) {
        return new SupplierRow(s.getId(), s.getCode(), s.getName(), s.getContactName(),
                s.getContactPhone(), s.getGstin(), s.getFssaiLicense(), s.getCity(), s.getState(),
                Boolean.TRUE.equals(s.getIsActive()));
    }
}
