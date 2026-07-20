package com.ecoexpress.ai.client;

/**
 * A provider-agnostic AI client. The rest of the app talks to this interface, never to Gemini
 * directly — so the provider can be swapped, stubbed in tests, or disabled when no key is set,
 * without touching a single feature.
 */
public interface AiClient {

    /** A model call and its result. */
    record AiTextResult(String text, int tokensIn, int tokensOut) {}

    /**
     * An image supplied to a vision call.
     *
     * @param base64Data the image bytes, base64-encoded
     * @param mimeType   e.g. image/jpeg, image/png
     */
    record ImageInput(String base64Data, String mimeType) {}

    /** True when the client can actually make calls (a key is configured). */
    boolean isAvailable();

    /**
     * Generates text from a prompt.
     *
     * @param systemInstruction optional steering ("You are a nutritionist…"); may be null
     * @param prompt            the user prompt
     * @param jsonOutput        when true, asks the model for JSON (features that parse structure)
     */
    AiTextResult generateText(String systemInstruction, String prompt, boolean jsonOutput);

    /** Generates text from a prompt plus an image (Gemini Vision) — e.g. the fridge scan. */
    AiTextResult generateFromImage(String prompt, ImageInput image, boolean jsonOutput);
}
