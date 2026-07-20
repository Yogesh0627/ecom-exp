package com.ecoexpress.ai.api;

import com.ecoexpress.ai.domain.PantryItem;
import com.ecoexpress.ai.domain.PantryUnit;
import com.ecoexpress.ai.service.PantryService;
import com.ecoexpress.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/** The AI Pantry (PRD §5.5). Scoped to the signed-in user. */
@Tag(name = "Pantry")
@RestController
@RequestMapping("/api/v1/pantry")
@RequiredArgsConstructor
public class PantryController {

    private final PantryService pantryService;

    public record AddRequest(
            @NotBlank String ingredientName,
            BigDecimal qty,
            PantryUnit unit,
            LocalDate expiryDate) {}

    public record PantryItemView(
            UUID id, String ingredientName, BigDecimal qty, String unit,
            LocalDate expiryDate, boolean expiringSoon) {}

    @Operation(summary = "My pantry")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<PantryItemView>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        List<PantryItemView> items = pantryService.list(user.getId()).stream()
                .map(this::view).toList();
        return ResponseEntity.ok(items);
    }

    @Operation(summary = "Add a pantry item")
    @PostMapping("/items")
    public ResponseEntity<PantryItemView> add(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddRequest r) {
        PantryItem item = pantryService.add(user.getId(), r.ingredientName(), r.qty(),
                r.unit(), r.expiryDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(item));
    }

    @Operation(summary = "Mark an item used up")
    @PostMapping("/items/{id}/consume")
    public ResponseEntity<Void> consume(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        pantryService.consume(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove an item")
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        pantryService.remove(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    private PantryItemView view(PantryItem i) {
        return new PantryItemView(i.getId(), i.getIngredientName(), i.getQty(),
                i.getUnit().name(), i.getExpiryDate(), i.isExpiringWithin(3));
    }
}
