package io.harnessengineering.projection;

/** Session-cumulative billed token usage, mirroring the projected token-usage keys. */
public record TokenUsage(long uncachedInputTokens, long cacheReadTokens, long cacheWriteTokens, long outputTokens) {
    public TokenUsage {
        if (uncachedInputTokens < 0 || cacheReadTokens < 0 || cacheWriteTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("usage figures must not be negative");
        }
    }

    /** @return prompt-side input summed across the three billing buckets */
    public long inputTokens() {
        return uncachedInputTokens + cacheReadTokens + cacheWriteTokens;
    }

    /** @return cache-read share of prompt-side input, or null when no input was billed */
    public Integer cacheHitPercent() {
        long input = inputTokens();
        if (input == 0) {
            return null;
        }
        return (int) Math.round(cacheReadTokens * 100.0 / input);
    }
}