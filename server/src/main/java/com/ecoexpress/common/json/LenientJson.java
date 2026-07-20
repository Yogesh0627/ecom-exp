package com.ecoexpress.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Salvages usable JSON from noisy or TRUNCATED model output.
 *
 * <p>Under load, Gemini's OpenAI-compatibility surface both wraps the object in prose/fences and,
 * when rate-limited or length-capped, truncates the reply so the closing brackets are missing. The
 * data is all there; only the closers were cut. This repairs that (balances open strings/brackets)
 * so a truncated recipe/product-content reply still parses instead of failing the whole call.
 */
public final class LenientJson {

    private LenientJson() {}

    /** A reader that tolerates trailing commas (from a repaired tail) and stray control chars. */
    public static ObjectMapper lenientMapper(ObjectMapper base) {
        return base.copy()
                .configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true)
                .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
    }

    /**
     * Parses possibly-noisy or truncated model JSON: extract the object, then repair-and-retry if
     * the raw slice won't parse. Throws only when even the repaired form is unusable.
     */
    public static JsonNode parse(ObjectMapper lenient, String raw) throws JsonProcessingException {
        String json = extractJson(raw);
        try {
            return lenient.readTree(json);
        } catch (JsonProcessingException first) {
            return lenient.readTree(balanceBrackets(json));
        }
    }

    /** Strips markdown fences and prose; keeps from the first '{' onward (may still be truncated). */
    public static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            }
            int closingFence = s.lastIndexOf("```");
            if (closingFence >= 0) {
                s = s.substring(0, closingFence);
            }
        }
        int start = s.indexOf('{');
        if (start < 0) {
            return "{}";
        }
        int end = s.lastIndexOf('}');
        return (end > start) ? s.substring(start, end + 1) : s.substring(start);
    }

    /** Closes any strings/arrays/objects the model left open when the reply was cut off. */
    public static String balanceBrackets(String s) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                stack.push(c);
            } else if (c == '}' || c == ']') {
                if (!stack.isEmpty()) {
                    stack.pop();
                }
            }
        }
        StringBuilder sb = new StringBuilder(s.stripTrailing());
        if (inString) {
            sb.append('"'); // close a string cut mid-value
        }
        // Drop a dangling comma/whitespace before appending closers.
        while (sb.length() > 0) {
            char last = sb.charAt(sb.length() - 1);
            if (last == ',' || Character.isWhitespace(last)) {
                sb.deleteCharAt(sb.length() - 1);
            } else {
                break;
            }
        }
        while (!stack.isEmpty()) {
            sb.append(stack.pop() == '{' ? '}' : ']');
        }
        return sb.toString();
    }
}
