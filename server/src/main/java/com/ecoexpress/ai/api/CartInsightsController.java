package com.ecoexpress.ai.api;

import com.ecoexpress.ai.service.CartInsightsService;
import com.ecoexpress.ai.service.CartInsightsService.BasketRec;
import com.ecoexpress.ai.service.CartInsightsService.RecipeSuggestion;
import com.ecoexpress.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cart Intelligence (PRD §5): "complete your basket" recommendations and the AI "turn my cart into
 * a meal" suggestion. Both are user-scoped (they read the caller's active cart), so they require
 * authentication — they sit under /api/v1/cart, which is not in the public allow-list.
 */
@Tag(name = "Cart intelligence")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartInsightsController {

    private final CartInsightsService cartInsightsService;

    @Operation(summary = "Complementary items for what's in the cart (in stock)")
    @GetMapping("/recommendations")
    public ResponseEntity<List<BasketRec>> recommendations(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(cartInsightsService.basketRecommendations(user.getId(), limit));
    }

    @Operation(summary = "AI: suggest a dish to cook from the cart, with addable ingredients")
    @PostMapping("/recipe-suggestion")
    public ResponseEntity<RecipeSuggestion> recipeSuggestion(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "true") boolean includePantry,
            @RequestParam(required = false) String exclude) {
        // exclude is a comma-separated list of already-shown dishes, so "Suggest another" varies.
        List<String> excluded = (exclude == null || exclude.isBlank())
                ? List.of()
                : java.util.Arrays.stream(exclude.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
        return ResponseEntity.ok(
                cartInsightsService.suggestRecipe(user.getId(), includePantry, excluded));
    }
}
