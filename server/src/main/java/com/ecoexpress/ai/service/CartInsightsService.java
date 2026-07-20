package com.ecoexpress.ai.service;

import com.ecoexpress.ai.domain.AiFeature;
import com.ecoexpress.ai.domain.PantryItem;
import com.ecoexpress.ai.repository.PantryItemRepository;
import com.ecoexpress.cart.domain.Cart;
import com.ecoexpress.cart.domain.CartItem;
import com.ecoexpress.cart.domain.CartStatus;
import com.ecoexpress.cart.repository.CartRepository;
import com.ecoexpress.catalog.domain.Product;
import com.ecoexpress.catalog.domain.ProductImage;
import com.ecoexpress.catalog.domain.ProductStatus;
import com.ecoexpress.catalog.domain.ProductVariant;
import com.ecoexpress.catalog.repository.ProductRepository;
import com.ecoexpress.catalog.repository.ProductVariantRepository;
import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.inventory.service.InventoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cart Intelligence (PRD §5): "complete your basket" recommendations, and an AI "turn my cart into
 * a meal" recipe suggestion.
 *
 * <p><b>Nothing here can recommend a product we don't stock.</b> Basket recs are drawn from the
 * catalog and stock-filtered. The recipe suggester lets the AI invent a <i>recipe</i> (safe general
 * knowledge) but every "add this ingredient" is resolved against real, in-stock SKUs by name match;
 * ingredients we can't match or that are out of stock are shown as a plain "you'll also need" note,
 * never an addable product.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartInsightsService {

    private static final String RECIPE_SYSTEM = """
            You are a practical Indian home-cooking assistant. Given ingredients a shopper already
            has, you suggest ONE simple, popular dish they can cook, and list the ingredients it
            needs. You always reply with STRICT JSON and nothing else.""";

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final PantryItemRepository pantryRepository;
    private final InventoryService inventoryService;
    private final AiService aiService;
    private final ObjectMapper mapper;

    /** A tolerant reader for salvaging model output (trailing commas from a repaired truncation). */
    private ObjectMapper lenientMapper;

    @jakarta.annotation.PostConstruct
    void initLenientMapper() {
        this.lenientMapper = com.ecoexpress.common.json.LenientJson.lenientMapper(mapper);
    }

    // ---- "Complete your basket" ------------------------------------------------

    public record BasketRec(UUID variantId, String sku, String productName, String productSlug,
                            BigDecimal price, String imageUrl, String reason) {}

    /**
     * Complementary items for what's in the cart: same-category, active, in stock, not already in
     * the cart. Deterministic and free (no AI). Ranked organic-first, then by discount.
     */
    @Transactional(readOnly = true)
    public List<BasketRec> basketRecommendations(UUID userId, int limit) {
        Cart cart = activeCart(userId);
        if (cart == null || cart.getItems().isEmpty()) {
            return List.of();
        }

        Set<UUID> cartVariantIds = new HashSet<>();
        Set<UUID> cartProductIds = new HashSet<>();
        List<UUID> orderedCategoryIds = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            ProductVariant v = item.getVariant();
            cartVariantIds.add(v.getId());
            Product p = v.getProduct();
            cartProductIds.add(p.getId());
            if (p.getCategory() != null && !orderedCategoryIds.contains(p.getCategory().getId())) {
                orderedCategoryIds.add(p.getCategory().getId());
            }
        }

        // Candidates from every category represented in the cart, excluding cart products.
        Map<UUID, BasketRec> byVariant = new LinkedHashMap<>();
        for (UUID categoryId : orderedCategoryIds) {
            List<Product> candidates = productRepository.findActiveInCategoryExcluding(
                    categoryId, cartProductIds.iterator().next(), PageRequest.of(0, limit * 3));
            for (Product p : candidates) {
                if (cartProductIds.contains(p.getId())) {
                    continue;
                }
                ProductVariant def = p.defaultVariant();
                if (def == null || cartVariantIds.contains(def.getId())
                        || !Boolean.TRUE.equals(def.getIsActive())) {
                    continue;
                }
                if (inventoryService.availableFor(def.getId()) <= 0) {
                    continue;
                }
                byVariant.putIfAbsent(def.getId(), new BasketRec(
                        def.getId(), def.getSku(), p.getName(), p.getSlug(), def.getPrice(),
                        imageUrl(def), "Goes well with your basket"));
            }
        }
        return byVariant.values().stream().limit(limit).toList();
    }

    // ---- "Turn my cart into a meal" (AI) ---------------------------------------

    public record RecipeItem(String ingredient, UUID variantId, String sku, String productName,
                             String productSlug, BigDecimal price) {}

    public record RecipeSuggestion(String dish, String description, List<String> usingFromCart,
                                   List<RecipeItem> addToCart, List<String> alsoNeed) {}

    /**
     * Asks the AI for a dish the shopper can cook from their cart (optionally + pantry), then
     * resolves each needed ingredient to a real, in-stock SKU. Ingredients we don't carry come back
     * as {@code alsoNeed} strings, never as addable products.
     */
    @Transactional(readOnly = true)
    public RecipeSuggestion suggestRecipe(UUID userId, boolean includePantry, List<String> exclude) {
        Cart cart = activeCart(userId);
        if (cart == null || cart.getItems().isEmpty()) {
            throw new BadRequestException("Your cart is empty — add a few items first.");
        }

        List<String> cartItems = cart.getItems().stream()
                .map(i -> i.getVariant().getProduct().getName())
                .distinct()
                .toList();
        Set<UUID> cartProductIds = cart.getItems().stream()
                .map(i -> i.getVariant().getProduct().getId())
                .collect(Collectors.toSet());

        List<String> pantryItems = List.of();
        if (includePantry) {
            pantryItems = pantryRepository.findActiveForUser(userId).stream()
                    .map(PantryItem::getIngredientName).distinct().toList();
        }

        String prompt = buildRecipePrompt(cartItems, pantryItems, exclude);

        // The model occasionally answers with prose/markdown instead of JSON. Retry a couple of
        // times before surfacing a 503 — turns a flaky call into a reliable one.
        JsonNode json = null;
        AiService.AiResult result = null;
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3 && json == null; attempt++) {
            result = aiService.generateText(
                    AiFeature.RECIPE_COMPLETION, userId, RECIPE_SYSTEM, prompt, true,
                    "CART", cart.getId());
            try {
                json = parse(result.text());
            } catch (RuntimeException e) {
                last = e;
                String raw = result.text();
                log.warn("Recipe suggestion attempt {} unparseable ({} chars): {}",
                        attempt, raw == null ? 0 : raw.length(),
                        raw == null ? "null" : raw.substring(0, Math.min(300, raw.length())));
            }
        }
        if (json == null) {
            throw last;
        }

        String dish = textField(json, "dish", "A dish from your cart");
        String description = textField(json, "description", "");

        List<String> needed = stringArray(json.get("needIngredients"));
        List<RecipeItem> addToCart = new ArrayList<>();
        List<String> alsoNeed = new ArrayList<>();
        Set<UUID> addedVariants = new HashSet<>();

        for (String ingredient : needed) {
            if (ingredient == null || ingredient.isBlank()) {
                continue;
            }
            ProductVariant match = matchInStock(ingredient, cartProductIds, addedVariants);
            if (match != null) {
                addedVariants.add(match.getId());
                addToCart.add(new RecipeItem(ingredient, match.getId(), match.getSku(),
                        match.getProduct().getName(), match.getProduct().getSlug(), match.getPrice()));
            } else {
                // We don't sell it (or it's out of stock) — tell them, don't fake an add button.
                alsoNeed.add(ingredient);
            }
        }

        log.info("Recipe suggestion '{}' for user {}: {} addable, {} also-need ({} tokens)",
                dish, userId, addToCart.size(), alsoNeed.size(),
                result.tokensIn() + result.tokensOut());
        return new RecipeSuggestion(dish, description, cartItems, addToCart, alsoNeed);
    }

    // ---- helpers ---------------------------------------------------------------

    private Cart activeCart(UUID userId) {
        return cartRepository.findByUserAndStatus(userId, CartStatus.ACTIVE).orElse(null);
    }

    /** Best active, in-stock variant matching an ingredient name that isn't already in the cart. */
    private ProductVariant matchInStock(String ingredient, Set<UUID> cartProductIds,
                                        Set<UUID> alreadyAdded) {
        List<ProductVariant> matches = variantRepository.findByProductNameMatch(
                ingredient.trim(), PageRequest.of(0, 5));
        for (ProductVariant v : matches) {
            Product p = v.getProduct();
            if (cartProductIds.contains(p.getId()) || alreadyAdded.contains(v.getId())) {
                continue;
            }
            if (p.getStatus() != ProductStatus.ACTIVE || !Boolean.TRUE.equals(v.getIsActive())) {
                continue;
            }
            if (inventoryService.availableFor(v.getId()) > 0) {
                return v;
            }
        }
        return null;
    }

    private String buildRecipePrompt(List<String> cartItems, List<String> pantryItems,
                                     List<String> exclude) {
        String pantry = pantryItems.isEmpty() ? "(none)" : String.join(", ", pantryItems);
        String avoid = (exclude == null || exclude.isEmpty()) ? ""
                : "Do NOT suggest any of these dishes (already shown): " + String.join(", ", exclude)
                        + ". Pick a genuinely different dish.\n";
        return """
                The shopper has these items in their cart: %s.
                They also already have at home: %s.

                Suggest ONE simple, popular Indian dish they can cook mostly from the cart items.
                %sThen list the ingredients the dish needs that are NOT already covered above.

                Reply with STRICT JSON only — no prose, no markdown, no code fences — in exactly
                this shape (the dish name is an EXAMPLE, choose your own):
                {
                  "dish": "<the dish name>",
                  "description": "one short sentence about the dish",
                  "needIngredients": ["ingredient one", "ingredient two"]
                }
                Keep needIngredients to common grocery items (max 8), each a short name a store would
                stock. Do not repeat items the shopper already has.""".formatted(
                String.join(", ", cartItems), pantry, avoid);
    }

    private String imageUrl(ProductVariant v) {
        ProductImage img = v.primaryImage();
        return img == null ? null : img.getUrl();
    }

    /**
     * Parses the model's JSON, tolerating the two things Gemini does under load: wrapping the object
     * in prose/fences, and TRUNCATING the reply (rate-limited or length-capped) so the closing
     * brackets are missing. We salvage a truncated-but-complete-enough object by balancing the open
     * strings/brackets — the recipe data is all there; only the closers were cut.
     */
    private JsonNode parse(String text) {
        try {
            return com.ecoexpress.common.json.LenientJson.parse(lenientMapper, text);
        } catch (Exception e) {
            log.warn("Recipe AI JSON unrecoverable: {}", e.getMessage());
            throw new com.ecoexpress.ai.exception.AiException(
                    "The AI returned a malformed suggestion. Please try again.");
        }
    }

    private String textField(JsonNode json, String field, String fallback) {
        JsonNode v = json.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) {
            return fallback;
        }
        return v.asText().trim();
    }

    private List<String> stringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        node.forEach(n -> {
            if (n != null && !n.isNull()) {
                out.add(n.asText().trim());
            }
        });
        return out;
    }
}
