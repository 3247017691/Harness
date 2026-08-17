package io.harnessengineering.projection;

/**
 * Reference token estimate used by the session projections. Real token meters
 * depend on the provider tokenizer; this deterministic approximation keeps the
 * Java core framework-free and offline. Figures derived from it are references,
 * not exact measurements of one request.
 */
public final class TokenEstimator {
    /** Characters per estimated token, a rough CJK-aware middle ground. */
    public static final int CHARS_PER_TOKEN = 4;

    private TokenEstimator() { }

    /**
     * Estimates the token count of a UTF-16 string.
     *
     * @param text text to estimate
     * @return estimated tokens, zero for empty input
     */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN);
    }
}