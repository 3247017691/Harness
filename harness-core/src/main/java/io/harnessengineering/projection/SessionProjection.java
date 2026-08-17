package io.harnessengineering.projection;

import io.harnessengineering.session.SessionEvent;
import io.harnessengineering.session.SessionEventTypes;
import java.util.ArrayList;
import java.util.List;

/**
 * Framework-free fold of a session event log into the projection records the
 * browser surface reads: context pressure, heuristic breakdown, cumulative
 * usage, and a per-request ledger. All figures are reference estimates derived
 * from event payload text — no provider tokenizer runs here.
 */
public final class SessionProjection {
    /** Default context-window capacity in estimated tokens. */
    public static final long DEFAULT_CONTEXT_WINDOW = 128_000L;
    /** Estimated system-prompt cost injected into the breakdown. */
    public static final long DEFAULT_SYSTEM_TOKENS = 800L;

    private final long contextWindow;
    private final long systemTokens;

    public SessionProjection() {
        this(DEFAULT_CONTEXT_WINDOW, DEFAULT_SYSTEM_TOKENS);
    }

    /**
     * Creates a projection fold over a configured capacity baseline.
     *
     * @param contextWindow context-window capacity in estimated tokens
     * @param systemTokens estimated system-prompt tokens
     */
    public SessionProjection(long contextWindow, long systemTokens) {
        if (contextWindow <= 0 || systemTokens < 0) {
            throw new IllegalArgumentException("invalid projection baseline");
        }
        this.contextWindow = contextWindow;
        this.systemTokens = systemTokens;
    }

    private static final class Accumulator {
        long messageTokens;
        long toolsTokens;
        long uncachedInput;
        long cacheRead;
        long cacheWrite;
        long output;
        int turn;
        int step;
        final List<RequestUsage> requests = new ArrayList<>();
    }

    /**
     * Projects a session event snapshot.
     *
     * @param events committed events in sequence order
     * @return projection record
     */
    public Result project(List<SessionEvent> events) {
        Accumulator state = new Accumulator();
        for (SessionEvent event : events) {
            switch (event.type()) {
                case SessionEventTypes.TURN_START -> state.turn = event.data().path("turn").asInt(state.turn);
                case SessionEventTypes.STEP_START -> state.step = event.data().path("step").asInt(state.step);
                case SessionEventTypes.USER_MESSAGE, SessionEventTypes.ASSISTANT_MESSAGE -> {
                    String content = event.data().path("content").asText("");
                    long tokens = TokenEstimator.estimate(content);
                    if (event.type().equals(SessionEventTypes.ASSISTANT_MESSAGE)) {
                        long input = state.messageTokens; // prompt side before this reply
                        state.output += tokens;
                        state.requests.add(new RequestUsage(
                                event.sequence(), state.turn, state.step,
                                event.data().path("providerId").asText(null),
                                event.data().path("model").asText(null),
                                input, tokens, 0L, event.time().toEpochMilli()));
                    } else {
                        state.uncachedInput += tokens;
                    }
                    state.messageTokens += tokens;
                }
                case SessionEventTypes.TOOL_CALL -> {
                    String args = event.data().path("arguments").asText("");
                    state.toolsTokens += TokenEstimator.estimate(args) + 4; // name + envelope
                }
                case SessionEventTypes.TOOL_RESULT -> {
                    String content = event.data().path("content").asText("");
                    state.toolsTokens += TokenEstimator.estimate(content);
                }
                default -> { }
            }
        }
        long used = state.messageTokens + state.toolsTokens + systemTokens;
        int percent = contextWindow <= 0 ? 0 : (int) Math.min(100, used * 100 / contextWindow);
        ContextPressure pressure = new ContextPressure(contextWindow, used, state.messageTokens, percent);
        ContextBreakdown breakdown = new ContextBreakdown(systemTokens, state.toolsTokens, state.messageTokens);
        TokenUsage usage = new TokenUsage(state.uncachedInput, state.cacheRead, state.cacheWrite, state.output);
        return new Result(pressure, breakdown, usage, List.copyOf(state.requests));
    }

    /** One model request's token record for the ledger table. */
    public record RequestUsage(
            long seq, int turn, int step, String providerId, String model,
            long input, long output, long reasoning, long time) {
        public RequestUsage {
            if (seq < 1 || turn < 0 || step < 0 || input < 0 || output < 0 || reasoning < 0) {
                throw new IllegalArgumentException("invalid request usage record");
            }
        }
    }

    /** Complete projection result for one session snapshot. */
    public record Result(ContextPressure pressure, ContextBreakdown breakdown, TokenUsage usage,
                         List<RequestUsage> requests) {
        public Result {
            java.util.Objects.requireNonNull(pressure, "pressure");
            java.util.Objects.requireNonNull(breakdown, "breakdown");
            java.util.Objects.requireNonNull(usage, "usage");
            requests = List.copyOf(requests);
        }
    }
}