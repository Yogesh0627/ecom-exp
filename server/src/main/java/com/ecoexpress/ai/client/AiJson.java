package com.ecoexpress.ai.client;

/**
 * Salvages a well-formed JSON value out of noisy model output.
 *
 * <p>Even in JSON mode, Gemini's OpenAI-compatibility surface occasionally wraps the object in
 * a markdown fence or appends a stray closing brace after the root value (observed:
 * {@code {...}\n}\n} with {@code gemini-flash-latest}). Jackson's {@code readTree} then rejects the
 * whole reply as malformed, wasting a call we already paid for. This extractor walks from the first
 * bracket to its matching close — respecting strings and escapes — and returns exactly that value,
 * discarding anything before or after. It is the one place that knows the provider emits dirty JSON.
 */
final class AiJson {

    private AiJson() {}

    /**
     * Returns the first balanced JSON object or array in {@code raw}, or {@code null} if the text
     * contains no bracketed value at all.
     */
    static String extractFirstJson(String raw) {
        if (raw == null) {
            return null;
        }
        String s = stripFence(raw.trim());

        int start = -1;
        char close = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '[') {
                start = i;
                close = c == '{' ? '}' : ']';
                break;
            }
        }
        if (start < 0) {
            return null;
        }

        char open = s.charAt(start);
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < s.length(); i++) {
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
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return s.substring(start, i + 1);
                }
            }
        }
        // Unbalanced (truncated reply): hand back the best effort and let the parser decide.
        return s.substring(start);
    }

    /** Strips a leading ```json / ``` fence and its trailing ``` if the model wrapped the reply. */
    private static String stripFence(String s) {
        if (!s.startsWith("```")) {
            return s;
        }
        int firstNewline = s.indexOf('\n');
        String body = firstNewline >= 0 ? s.substring(firstNewline + 1) : s.substring(3);
        int lastFence = body.lastIndexOf("```");
        if (lastFence >= 0) {
            body = body.substring(0, lastFence);
        }
        return body.trim();
    }
}
