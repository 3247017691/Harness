package io.harnessengineering.projection;

/** Heuristic token composition of the context window: system prompt, tools, messages. */
public record ContextBreakdown(long systemTokens, long toolsTokens, long messageTokens) {
    public ContextBreakdown {
        if (systemTokens < 0 || toolsTokens < 0 || messageTokens < 0) {
            throw new IllegalArgumentException("breakdown figures must not be negative");
        }
    }

    /** @return the summed composition estimate */
    public long total() {
        return systemTokens + toolsTokens + messageTokens;
    }
}