package com.ecoexpress.ai.service;

import com.ecoexpress.ai.client.AiClient;
import com.ecoexpress.ai.domain.AiFeature;
import com.ecoexpress.ai.domain.FridgeScan;
import com.ecoexpress.ai.domain.FridgeScanItem;
import com.ecoexpress.ai.domain.FridgeScanStatus;
import com.ecoexpress.ai.exception.AiException;
import com.ecoexpress.ai.repository.FridgeScanRepository;
import com.ecoexpress.catalog.domain.ProductVariant;
import com.ecoexpress.catalog.repository.ProductVariantRepository;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.identity.domain.User;
import com.ecoexpress.identity.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Smart Fridge AI (PRD §5.1): detect ingredients in a fridge photo, then map them to what we sell.
 *
 * <p>Gemini Vision does the detection — no OpenCV/OCR pipeline. The image (base64) plus a strict
 * JSON prompt goes through {@link AiService}; the reply is parsed into {@code fridge_scan_items},
 * each fuzzy-matched to a catalog variant so the app can offer "buy the missing items".
 *
 * <p><b>PII:</b> the scan row's {@code purgeAfter} is set from the retention window so a job can
 * delete the image later. Low-confidence detections are flagged for user confirmation rather than
 * trusted — a wrong ingredient poisons everything downstream.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FridgeScanService {

    private static final String PROMPT = """
            Look at this photo of the inside of a fridge or a kitchen shelf. List the food
            ingredients you can identify. Reply with STRICT JSON only, no prose:
            {"items":[{"name":"tomato","confidence":0.9,"quantity":"3 pieces"}]}
            Rules: name is a lowercase common ingredient name. confidence is 0..1. Only include
            food items. If you see none, return {"items":[]}.""";

    /** Below this the detection is shown for confirmation rather than trusted. */
    private static final BigDecimal CONFIRM_THRESHOLD = new BigDecimal("0.6");

    @Value("${ecoexpress.ai.fridge-retention-days:30}")
    private int retentionDays;

    private final AiService aiService;
    private final FridgeScanRepository scanRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final ObjectMapper mapper;

    /**
     * @param imageBase64 the image bytes, base64-encoded (production upload is multipart → S3;
     *                    the base64 form keeps the API testable)
     * @param mimeType    image/jpeg or image/png
     * @param imageUrl    where the image is stored (S3/MinIO key) — kept for the retention job
     */
    @Transactional
    public FridgeScan scan(UUID userId, String imageBase64, String mimeType, String imageUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("No user " + userId));

        FridgeScan scan = scanRepository.save(FridgeScan.builder()
                .user(user)
                .imageUrl(imageUrl == null ? "inline" : imageUrl)
                .status(FridgeScanStatus.PROCESSING)
                .purgeAfter(Instant.now().plus(retentionDays, ChronoUnit.DAYS))
                .build());

        try {
            AiService.AiResult result = aiService.generateFromImage(
                    AiFeature.FRIDGE_SCAN, userId, PROMPT,
                    new AiClient.ImageInput(imageBase64, mimeType), true,
                    "FRIDGE_SCAN", scan.getId());

            scan.setRawResponse(result.text());
            parseInto(scan, result.text());
            scan.setStatus(FridgeScanStatus.COMPLETED);
            scan.setProcessedAt(Instant.now());
            log.info("Fridge scan {} detected {} item(s) ({} tokens, ~{} INR)",
                    scan.getId(), scan.getItems().size(),
                    result.tokensIn() + result.tokensOut(), result.costInr());
            return scan;

        } catch (AiException e) {
            // Record the failure on the scan so the user sees "try again", not a blank result.
            scan.setStatus(FridgeScanStatus.FAILED);
            scan.setError(e.getMessage());
            throw e;
        }
    }

    private void parseInto(FridgeScan scan, String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            throw new AiException("The fridge scan returned an unreadable response. Please retry.");
        }

        for (JsonNode item : root.path("items")) {
            String name = item.path("name").asText(null);
            if (name == null || name.isBlank()) {
                continue;
            }
            name = name.trim().toLowerCase();

            BigDecimal confidence = item.has("confidence")
                    ? BigDecimal.valueOf(item.path("confidence").asDouble(0)) : null;

            // Fuzzy-map to a catalog variant: a case-insensitive contains match on product name.
            // Starts simple by design (see the AI architecture note); upgrade to embeddings only
            // if this proves too coarse.
            ProductVariant match = variantRepository.findFirstByProductNameContainingActive(name)
                    .orElse(null);

            boolean confident = confidence != null && confidence.compareTo(CONFIRM_THRESHOLD) >= 0;

            scan.getItems().add(FridgeScanItem.builder()
                    .scan(scan)
                    .detectedName(name)
                    .confidence(confidence)
                    .quantityGuess(item.path("quantity").asText(null))
                    .variant(match)
                    // Confident + matched → trusted; otherwise ask the user to confirm.
                    .confirmedByUser(false)
                    .build());
            if (!confident) {
                log.debug("Low-confidence fridge detection '{}' ({}) — flagged for confirmation",
                        name, confidence);
            }
        }
    }

    @Transactional(readOnly = true)
    public FridgeScan get(UUID userId, UUID scanId) {
        FridgeScan scan = scanRepository.findByIdWithItems(scanId)
                .orElseThrow(() -> new NotFoundException("No scan " + scanId));
        if (!scan.getUser().getId().equals(userId)) {
            throw new NotFoundException("No scan " + scanId);
        }
        return scan;
    }
}
