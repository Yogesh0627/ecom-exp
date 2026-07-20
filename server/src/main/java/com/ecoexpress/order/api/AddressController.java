package com.ecoexpress.order.api;

import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.common.security.AuthenticatedUser;
import com.ecoexpress.identity.repository.UserRepository;
import com.ecoexpress.order.domain.Address;
import com.ecoexpress.order.domain.AddressType;
import com.ecoexpress.order.dto.OrderDtos.AddressResponse;
import com.ecoexpress.order.dto.OrderDtos.CreateAddressRequest;
import com.ecoexpress.order.mapper.OrderMapper;
import com.ecoexpress.order.repository.AddressRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The signed-in user's saved addresses. Every route is scoped to their own id. */
@Tag(name = "Addresses")
@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final OrderMapper mapper;

    @Operation(summary = "List my addresses")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<AddressResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(addressRepository.findByUserId(user.getId()).stream()
                .map(mapper::toAddress).toList());
    }

    @Operation(summary = "Add an address")
    @PostMapping
    @Transactional
    public ResponseEntity<AddressResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateAddressRequest request) {

        var user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new NotFoundException("No user " + principal.getId()));

        boolean first = addressRepository.findByUserId(principal.getId()).isEmpty();
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault()) || first;

        // Clear the old default first: addresses_one_default_per_user is a unique index,
        // so setting the new one first would collide with the existing row.
        if (makeDefault) {
            addressRepository.clearDefaultForUser(principal.getId());
        }

        Address address = addressRepository.save(Address.builder()
                .user(user)
                .label(request.label())
                .recipientName(request.recipientName())
                .phone(request.phone())
                .line1(request.line1())
                .line2(request.line2())
                .landmark(request.landmark())
                .city(request.city())
                .state(request.state())
                .pincode(request.pincode())
                .country("IN")
                .type(request.type() == null ? AddressType.HOME : request.type())
                .isDefault(makeDefault)
                .build());

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toAddress(address));
    }

    @Operation(summary = "Update an address")
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<AddressResponse> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody CreateAddressRequest request) {
        Address address = addressRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("No such address."));

        address.setLabel(request.label());
        address.setRecipientName(request.recipientName());
        address.setPhone(request.phone());
        address.setLine1(request.line1());
        address.setLine2(request.line2());
        address.setLandmark(request.landmark());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPincode(request.pincode());
        if (request.type() != null) {
            address.setType(request.type());
        }
        // Promoting to default here goes through the same clear-then-set as create/set-default.
        // clearDefaultForUser has clearAutomatically=true, which DETACHES `address`; re-save (merge)
        // so both the field edits above and the default flag are persisted.
        if (Boolean.TRUE.equals(request.isDefault()) && !Boolean.TRUE.equals(address.getIsDefault())) {
            addressRepository.clearDefaultForUser(user.getId());
            address.setIsDefault(true);
        }
        return ResponseEntity.ok(mapper.toAddress(addressRepository.save(address)));
    }

    @Operation(summary = "Make this my default delivery address")
    @PostMapping("/{id}/set-default")
    @Transactional
    public ResponseEntity<AddressResponse> setDefault(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        Address address = addressRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("No such address."));
        // Clear the old default first — addresses_one_default_per_user is a unique index. The clear
        // has clearAutomatically=true (detaches `address`), so re-save to persist the new default.
        addressRepository.clearDefaultForUser(user.getId());
        address.setIsDefault(true);
        return ResponseEntity.ok(mapper.toAddress(addressRepository.save(address)));
    }

    /**
     * Soft delete. Orders snapshot the address rather than referencing it, so removing
     * one never affects a past order's record of where it went. If the deleted address was the
     * default, promote the most recently added remaining one so the user always has a default.
     */
    @Operation(summary = "Delete an address")
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        Address address = addressRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("No such address."));
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        address.setDeletedAt(Instant.now());
        address.setIsDefault(false);

        if (wasDefault) {
            // @SQLRestriction filters out the just-deleted row, so this is the remaining set.
            addressRepository.findByUserId(user.getId()).stream()
                    .max(Comparator.comparing(Address::getCreatedAt))
                    .ifPresent(a -> a.setIsDefault(true));
        }
        return ResponseEntity.noContent().build();
    }
}
