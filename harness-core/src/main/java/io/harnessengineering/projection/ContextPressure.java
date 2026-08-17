package io.harnessengineering.projection;

/**
 * Occupancy of the session context window. The numerator prefers the projected
 * figure and falls back to the bare sample; both are last-wins projection
 * records, so this is a reference figure, not an exact measurement.
 */
public record ContextPressure(long contextWindow, long projectedTokens, long pressureTokens, int percent) {
    public ContextPressure {
        if (contextWindow <= 0) {
            throw new IllegalArgumentException("context window must be positive");
        }
        if (projectedTokens < 0 || pressureTokens < 0) {
            throw new IllegalArgumentException("token figures must not be negative");
        }
    }

    /** @return the occupied numerator, preferring the projected figure */
    public long usedTokens() {
        return projectedTokens;
    }
}