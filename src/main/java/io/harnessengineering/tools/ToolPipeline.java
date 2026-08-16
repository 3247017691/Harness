package io.harnessengineering.tools;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.harnessengineering.core.CancellationToken;
import io.harnessengineering.core.Effect;
import io.harnessengineering.session.Session;
import io.harnessengineering.session.SessionEventTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tool executor with reversible middleware, optional retry, and cancellation-aware
 * parallel dispatch. Session events are recorded deterministically: call events in
 * request order before dispatch, result events in request order after convergence.
 */
public final class ToolPipeline {
    private final ToolRegistry registry;
    private final List<ToolMiddleware> middleware = new ArrayList<>();
    private volatile RetryPolicy retry = RetryPolicy.none();

    public ToolPipeline(ToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /** @return the tool schemas currently advertised by this pipeline. */
    public List<ToolDefinition> definitions() {
        return registry.definitions();
    }

    /** Configures the retry schedule for failed executions. */
    public ToolPipeline retry(RetryPolicy policy) {
        this.retry = Objects.requireNonNull(policy, "policy");
        return this;
    }

    public synchronized Effect use(ToolMiddleware entry) {
        Objects.requireNonNull(entry, "entry");
        middleware.add(entry);
        return new MiddlewareEffect(entry);
    }

    /** Executes calls serially, recording call and result events per call. */
    public List<ToolResult> execute(List<ToolCall> calls, Session session) {
        Objects.requireNonNull(calls, "calls");
        Objects.requireNonNull(session, "session");
        List<ToolResult> results = new ArrayList<>();
        ToolContext context = new ToolContext(session);
        for (ToolCall call : calls) {
            session.append(SessionEventTypes.TOOL_CALL, callEvent(call));
            ToolResult result = executeOne(call, context, null);
            session.append(SessionEventTypes.TOOL_RESULT, resultEvent(result));
            results.add(result);
        }
        return List.copyOf(results);
    }

    /**
     * Executes calls concurrently, records call events first and result events in
     * request order once every call has converged, and never dispatches a call after
     * the token is cancelled.
     *
     * @param calls calls to execute
     * @param session event log
     * @param token cooperative cancellation, may be null
     * @return results aligned with {@code calls}
     */
    public List<ToolResult> executeParallel(List<ToolCall> calls, Session session, CancellationToken token) {
        Objects.requireNonNull(calls, "calls");
        Objects.requireNonNull(session, "session");
        ToolResult[] results = new ToolResult[calls.size()];
        ToolContext context = new ToolContext(session);
        for (ToolCall call : calls) {
            session.append(SessionEventTypes.TOOL_CALL, callEvent(call));
        }
        List<Thread> workers = new ArrayList<>(calls.size());
        for (int index = 0; index < calls.size(); index++) {
            ToolCall call = calls.get(index);
            int callIndex = index;
            Thread worker = Thread.ofVirtual().name("tool-" + call.name()).start(() -> {
                results[callIndex] = executeOne(call, context, token);
            });
            workers.add(worker);
        }
        joinAll(workers);
        for (ToolResult result : results) {
            session.append(SessionEventTypes.TOOL_RESULT, resultEvent(result));
        }
        return List.of(results);
    }

    /** Executes calls concurrently without a cancellation token. */
    public List<ToolResult> executeParallel(List<ToolCall> calls, Session session) {
        return executeParallel(calls, session, null);
    }

    private ToolResult executeOne(ToolCall call, ToolContext context, CancellationToken token) {
        List<ToolMiddleware> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(middleware);
        }
        RetryPolicy policy = retry;
        for (int attempt = 1; ; attempt++) {
            if (token != null && token.isCancelled()) {
                return ToolResult.failure(call.id(), "cancelled", "cancelled before dispatch");
            }
            ToolResult result;
            try {
                result = invoke(snapshot, 0, call, context);
            } catch (Exception exception) {
                result = ToolResult.failure(call.id(), "execution_failed", exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage());
            }
            if (result.success() || attempt >= policy.maxAttempts()) {
                return result;
            }
            if (!sleep(policy.delay())) {
                return ToolResult.failure(call.id(), "cancelled", "cancelled during retry wait");
            }
        }
    }

    private ToolResult invoke(List<ToolMiddleware> entries, int index, ToolCall call, ToolContext context)
            throws Exception {
        if (index == entries.size()) {
            Tool tool = registry.find(call.name());
            if (tool == null) {
                return ToolResult.failure(call.id(), "tool_not_found", "No tool named " + call.name());
            }
            return tool.execute(call, context);
        }
        return entries.get(index).execute(call, context,
                (nextCall, nextContext) -> invoke(entries, index + 1, nextCall, nextContext));
    }

    private static boolean sleep(java.time.Duration delay) {
        if (delay.isZero()) {
            return true;
        }
        try {
            Thread.sleep(delay.toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void joinAll(List<Thread> workers) {
        InterruptedException failure = null;
        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for tool calls", failure);
        }
    }

    private static ObjectNode callEvent(ToolCall call) {
        return JsonNodeFactory.instance.objectNode()
                .put("id", call.id())
                .put("name", call.name())
                .put("arguments", call.arguments());
    }

    private static ObjectNode resultEvent(ToolResult result) {
        ObjectNode event = JsonNodeFactory.instance.objectNode()
                .put("toolCallId", result.toolCallId())
                .put("success", result.success())
                .put("content", result.content());
        if (result.errorCode() != null) {
            event.put("errorCode", result.errorCode());
        }
        return event;
    }

    private final class MiddlewareEffect implements Effect {
        private final ToolMiddleware entry;
        private boolean closed;

        private MiddlewareEffect(ToolMiddleware entry) {
            this.entry = entry;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                synchronized (ToolPipeline.this) {
                    middleware.remove(entry);
                }
            }
        }
    }
}
