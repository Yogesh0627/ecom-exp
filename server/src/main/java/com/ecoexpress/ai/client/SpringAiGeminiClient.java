package com.ecoexpress.ai.client;

import com.ecoexpress.ai.config.AiProperties;
import com.ecoexpress.ai.exception.AiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The {@link AiClient} implementation, backed by Spring AI's OpenAI-compatible chat model pointed
 * at Gemini's OpenAI endpoint.
 *
 * <p>This is the ONLY class that knows about the AI provider. Because everything else depends on
 * the {@link AiClient} interface, moving from a hand-rolled HTTP client to Spring AI touched this
 * file alone — {@code AiService}, its budget/cost logic, and all five features are untouched. That
 * is the payoff of the interface: the provider is a swappable adapter.
 *
 * <p>JSON mode uses OpenAI's {@code response_format: json_object}, which Gemini's compatibility
 * surface honours — so structured features do not have to strip prose from the reply.
 */
@Slf4j
@Component
public class SpringAiGeminiClient implements AiClient {

    private final ChatModel chatModel;
    private final AiProperties props;

    /**
     * {@code ChatModel} is injected via ObjectProvider so the app still boots when no key is set —
     * Spring AI's OpenAI auto-config only creates the bean when a key is present. With no key, the
     * client simply reports unavailable and features return a clean 503.
     */
    public SpringAiGeminiClient(ObjectProvider<ChatModel> chatModel, AiProperties props) {
        this.chatModel = chatModel.getIfAvailable();
        this.props = props;
    }

    @Override
    public boolean isAvailable() {
        return props.isEnabled() && chatModel != null;
    }

    @Override
    public AiTextResult generateText(String systemInstruction, String prompt, boolean jsonOutput) {
        List<Message> messages = new ArrayList<>();
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            messages.add(new SystemMessage(systemInstruction));
        }
        messages.add(new UserMessage(prompt));
        return call(messages, jsonOutput);
    }

    @Override
    public AiTextResult generateFromImage(String prompt, ImageInput image, boolean jsonOutput) {
        byte[] bytes = Base64.getDecoder().decode(image.base64Data());
        Media media = Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(image.mimeType()))
                .data(new ByteArrayResource(bytes))
                .build();
        UserMessage userMessage = UserMessage.builder().text(prompt).media(media).build();
        return call(List.of(userMessage), jsonOutput);
    }

    private AiTextResult call(List<Message> messages, boolean jsonOutput) {
        if (!isAvailable()) {
            throw new AiException("AI is not configured (no API key).");
        }
        OpenAiChatOptions.Builder opts = OpenAiChatOptions.builder()
                .model(props.model())
                .temperature(0.4);
        if (jsonOutput) {
            opts.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build());
        }

        try {
            ChatResponse resp = chatModel.call(new Prompt(messages, opts.build()));
            String text = resp.getResult().getOutput().getText();
            if (text == null || text.isBlank()) {
                throw new AiException("The model returned no text.");
            }
            // Gemini's JSON mode sometimes fences the object or appends a stray brace; salvage the
            // real value here so every feature's parser receives clean JSON (see AiJson).
            if (jsonOutput) {
                String extracted = AiJson.extractFirstJson(text);
                if (extracted == null) {
                    throw new AiException("The model did not return JSON.");
                }
                text = extracted;
            }
            Usage usage = resp.getMetadata().getUsage();
            int in = usage == null ? 0 : safeInt(usage.getPromptTokens());
            int out = usage == null ? 0 : safeInt(usage.getCompletionTokens());
            return new AiTextResult(text, in, out);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            // Do not leak the provider's raw error (it can echo the prompt); classify by message.
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            boolean rateLimited = msg.contains("429") || msg.contains("rate")
                    || msg.contains("quota") || msg.contains("resource_exhausted")
                    || msg.contains("exhausted");
            log.warn("Gemini (Spring AI) call failed: {} (rateLimited={})",
                    e.getClass().getSimpleName(), rateLimited);
            if (rateLimited) {
                throw new AiException(
                        "AI features are over their usage limit right now. Please try again later.",
                        429, parseRetryAfterSeconds(msg));
            }
            throw new AiException("The AI service is unavailable right now.", 0);
        }
    }

    /** Reads a "please retry in 29.79s" hint from the provider error, if present. */
    private int parseRetryAfterSeconds(String message) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("retry in ([0-9.]+)s").matcher(message);
        if (m.find()) {
            try {
                return (int) Math.ceil(Double.parseDouble(m.group(1)));
            } catch (NumberFormatException ignore) {
                // fall through
            }
        }
        return 0;
    }

    private int safeInt(Integer v) {
        return v == null ? 0 : v;
    }
}
