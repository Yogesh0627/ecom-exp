package com.ecoexpress.catalog.service;

import com.ecoexpress.ai.domain.AiFeature;
import com.ecoexpress.ai.service.AiService;
import com.ecoexpress.catalog.domain.NutritionFacts;
import com.ecoexpress.catalog.domain.Product;
import com.ecoexpress.catalog.domain.ProductContent;
import com.ecoexpress.catalog.domain.ProductContentStatus;
import com.ecoexpress.catalog.domain.ProductVariant;
import com.ecoexpress.catalog.repository.ProductContentRepository;
import com.ecoexpress.catalog.repository.ProductRepository;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rich product content: AI-drafted, human-approved (PRD §5, product detail).
 *
 * <p><b>Two guardrails, by design.</b> First, the generator is grounded in everything we actually
 * know about the product (name, category, origin, organic status, existing description, measured
 * nutrition) — it is NOT guessing from a bare category. Second, nothing it writes goes live: it is
 * saved as a {@link ProductContentStatus#DRAFT} that only staff see, and a human must
 * {@link #publish} it before a shopper does. The system prompt further forbids disease
 * cure/prevention claims (FSSAI/ASCI) and fabricated nutrition numbers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductContentService {

    private static final String SYSTEM = """
            You are a careful food copywriter for an Indian certified-organic grocery store.
            You write accurate, non-hyperbolic product content grounded ONLY in the facts given.
            HARD RULES:
            - NEVER claim a food cures, treats, or prevents any disease or medical condition.
            - Use cautious framing: "is a good source of…", "may support…", "contributes to…".
            - State only widely-accepted nutrition facts for the named food. If a fact is uncertain,
              omit it. NEVER invent specific nutrition numbers.
            - Keep it concrete and India-relevant. No filler, no marketing clichés.
            - Reply with STRICT JSON only, no markdown, no prose outside the JSON.""";

    private final ProductRepository productRepository;
    private final ProductContentRepository contentRepository;
    private final AiService aiService;
    private final ObjectMapper mapper;

    /** Tolerant reader for salvaging truncated/noisy model output. */
    private ObjectMapper lenientMapper;

    @jakarta.annotation.PostConstruct
    void initLenientMapper() {
        this.lenientMapper = com.ecoexpress.common.json.LenientJson.lenientMapper(mapper);
    }

    /** The content row for admin editing (any status). Empty if none generated yet. */
    @Transactional(readOnly = true)
    public Optional<ProductContent> getForProduct(String slug) {
        Product product = requireProduct(slug);
        return contentRepository.findByProductId(product.getId());
    }

    /** The PUBLISHED content for the storefront, or empty. */
    @Transactional(readOnly = true)
    public Optional<ProductContent> getPublished(UUID productId) {
        return contentRepository.findPublishedByProductId(productId);
    }

    /**
     * Generates (or regenerates) an AI DRAFT for the product, grounded in its real fields. Never
     * publishes — an admin reviews and calls {@link #publish}. Regenerating overwrites an existing
     * DRAFT; a PUBLISHED row is left live and a fresh draft's fields overwrite it but the status is
     * reset to DRAFT so the new copy must be re-approved.
     */
    @Transactional
    public ProductContent generateDraft(String slug, UUID adminUserId) {
        Product product = requireProduct(slug);
        String prompt = buildPrompt(product);

        // The model occasionally returns prose instead of JSON. One retry turns a flaky call into a
        // reliable one for the admin, rather than surfacing an avoidable 503.
        JsonNode json = null;
        AiService.AiResult result = null;
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 2 && json == null; attempt++) {
            result = aiService.generateText(
                    AiFeature.PRODUCT_CONTENT, adminUserId, SYSTEM, prompt, true,
                    "PRODUCT_CONTENT", product.getId());
            try {
                json = parse(result.text());
            } catch (RuntimeException e) {
                last = e;
                log.warn("Product-content generation attempt {} failed to parse for '{}'", attempt, slug);
            }
        }
        if (json == null) {
            throw last;
        }

        ProductContent content = contentRepository.findByProductId(product.getId())
                .orElseGet(() -> ProductContent.builder().product(product).build());

        content.setOverview(text(json, "overview"));
        content.setAdvantages(text(json, "advantages"));
        content.setHealthBenefits(text(json, "healthBenefits"));
        content.setNutrientSupport(text(json, "nutrientSupport"));
        content.setWhyChoose(text(json, "whyChoose"));
        content.setStorageTips(text(json, "storageTips"));
        content.setGeneratedByAi(true);
        content.setAiModel(aiModelLabel());
        content.setGeneratedAt(Instant.now());
        // A freshly generated draft must be re-approved before shoppers see it.
        content.setStatus(ProductContentStatus.DRAFT);
        content.setPublishedAt(null);

        ProductContent saved = contentRepository.save(content);
        log.info("Product content DRAFT generated for '{}' ({} tokens, ~{} INR)",
                slug, result.tokensIn() + result.tokensOut(), result.costInr());
        return saved;
    }

    /** Admin edit of the content sections. Keeps the current status (editing a live row stays live). */
    @Transactional
    public ProductContent update(String slug, ProductContent edits) {
        Product product = requireProduct(slug);
        ProductContent content = contentRepository.findByProductId(product.getId())
                .orElseGet(() -> ProductContent.builder().product(product)
                        .status(ProductContentStatus.DRAFT).build());
        content.setOverview(edits.getOverview());
        content.setAdvantages(edits.getAdvantages());
        content.setHealthBenefits(edits.getHealthBenefits());
        content.setNutrientSupport(edits.getNutrientSupport());
        content.setWhyChoose(edits.getWhyChoose());
        content.setStorageTips(edits.getStorageTips());
        // Manually edited content is no longer purely AI-authored.
        content.setGeneratedByAi(false);
        return contentRepository.save(content);
    }

    @Transactional
    public ProductContent publish(String slug) {
        Product product = requireProduct(slug);
        ProductContent content = contentRepository.findByProductId(product.getId())
                .orElseThrow(() -> new NotFoundException("No content to publish for " + slug));
        content.setStatus(ProductContentStatus.PUBLISHED);
        content.setPublishedAt(Instant.now());
        log.info("Product content PUBLISHED for '{}'", slug);
        return content;
    }

    @Transactional
    public ProductContent unpublish(String slug) {
        Product product = requireProduct(slug);
        ProductContent content = contentRepository.findByProductId(product.getId())
                .orElseThrow(() -> new NotFoundException("No content for " + slug));
        content.setStatus(ProductContentStatus.DRAFT);
        content.setPublishedAt(null);
        return content;
    }

    // ---- helpers ----------------------------------------------------------------

    private Product requireProduct(String slug) {
        return productRepository.findBySlugWithDetails(slug)
                .orElseThrow(() -> new NotFoundException("No product " + slug));
    }

    /** Grounds the model in the product's real, known facts — never just its category. */
    private String buildPrompt(Product product) {
        StringBuilder facts = new StringBuilder();
        facts.append("Product name: ").append(product.getName()).append('\n');
        if (product.getCategory() != null) {
            facts.append("Category: ").append(product.getCategory().getName()).append('\n');
        }
        if (product.getBrand() != null) {
            facts.append("Brand: ").append(product.getBrand().getName()).append('\n');
        }
        if (product.getOrigin() != null && !product.getOrigin().isBlank()) {
            facts.append("Origin: ").append(product.getOrigin()).append('\n');
        }
        facts.append("Certified organic: ").append(Boolean.TRUE.equals(product.getIsOrganic())).append('\n');
        if (product.getDescription() != null && !product.getDescription().isBlank()) {
            facts.append("Existing description: ").append(product.getDescription()).append('\n');
        }
        facts.append(nutritionFacts(product));

        return """
                Write rich product-detail content for this product, using ONLY these facts and
                widely-accepted nutrition knowledge about the named food:

                %s
                Reply with STRICT JSON in exactly this shape (each value a short paragraph or a few
                newline-separated bullet lines; use "" if you genuinely have nothing accurate to say):
                {
                  "overview": "a warm, accurate 2-3 sentence description",
                  "advantages": "why this product is good, as 3-5 short newline-separated points",
                  "healthBenefits": "nutrition-backed benefits, cautiously framed, 3-5 points",
                  "nutrientSupport": "which nutrients it provides and what they support in the body (e.g. 'iron supports healthy blood'), 2-4 points, NO disease claims",
                  "whyChoose": "why choose this / organic over conventional, 2-3 points",
                  "storageTips": "how to store it to stay fresh, 1-3 short points"
                }""".formatted(facts);
    }

    /** Only include measured nutrients (non-null) from the default variant, if any. */
    private String nutritionFacts(Product product) {
        ProductVariant def = product.defaultVariant();
        if (def == null || def.getNutritionFacts() == null) {
            return "Measured nutrition: not available (do not invent numbers).\n";
        }
        NutritionFacts n = def.getNutritionFacts();
        List<String> parts = new ArrayList<>();
        addNutrient(parts, "calories", n.getCaloriesKcal(), "kcal");
        addNutrient(parts, "protein", n.getProteinG(), "g");
        addNutrient(parts, "fat", n.getFatG(), "g");
        addNutrient(parts, "carbohydrates", n.getCarbohydratesG(), "g");
        addNutrient(parts, "fibre", n.getFiberG(), "g");
        addNutrient(parts, "sugar", n.getSugarG(), "g");
        addNutrient(parts, "iron", n.getIronMg(), "mg");
        addNutrient(parts, "vitamin A", n.getVitaminAMcg(), "mcg");
        addNutrient(parts, "vitamin C", n.getVitaminCMg(), "mg");
        addNutrient(parts, "vitamin D", n.getVitaminDMcg(), "mcg");
        addNutrient(parts, "potassium", n.getPotassiumMg(), "mg");
        addNutrient(parts, "sodium", n.getSodiumMg(), "mg");
        if (parts.isEmpty()) {
            return "Measured nutrition: not available (do not invent numbers).\n";
        }
        return "Measured nutrition per 100g: " + String.join(", ", parts) + ".\n";
    }

    private void addNutrient(List<String> parts, String label, java.math.BigDecimal value, String unit) {
        if (value != null) {
            parts.add(label + " " + value.stripTrailingZeros().toPlainString() + unit);
        }
    }

    private JsonNode parse(String text) {
        try {
            // Salvages fenced/prose-wrapped and TRUNCATED replies (Gemini truncates under load).
            return com.ecoexpress.common.json.LenientJson.parse(lenientMapper, text);
        } catch (Exception e) {
            log.warn("Product-content AI returned unparseable JSON: {}", e.getMessage());
            throw new com.ecoexpress.ai.exception.AiException(
                    "The AI returned malformed content. Please try again.");
        }
    }

    private String text(JsonNode json, String field) {
        JsonNode v = json.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private String aiModelLabel() {
        return aiService.isEnabled() ? "gemini" : "unknown";
    }
}
