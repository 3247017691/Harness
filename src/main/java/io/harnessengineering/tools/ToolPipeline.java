package io.harnessengineering.tools;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.harnessengineering.core.Effect;
import io.harnessengineering.session.Session;
import io.harnessengineering.session.SessionEventTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Serial tool executor with reversible middleware and structured failure results. */
public final class ToolPipeline {
    private final ToolRegistry registry;
    private final List<ToolMiddleware> middleware = new ArrayList<>();

    public ToolPipeline(ToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /** @return the tool schemas currently advertised by this pipeline. */
    public List<ToolDefinition> definitions() {
        return registry.definitions();
    }

    public synchronized Effect use(ToolMiddleware entry) {
        Objects.requireNonNull(entry, "entry");
        middleware.add(entry);
        return new MiddlewareEffect(entry);
    }

    /** Executes calls serially, records every call and result, and never drops failures. */
    public List<ToolResult> execute(List<ToolCall> calls, Session session) {
        Objects.requireNonNull(calls, "calls");
        Objects.requireNonNull(session, "session");
        List<ToolResult> results = new ArrayList<>();
        ToolContext context = new ToolContext(session);
        for (ToolCall call : calls) {
            session.append(SessionEventTypes.TOOL_CALL, callEvent(call));
            ToolResult result = executeOne(call, context);
            session.append(SessionEventTypes.TOOL_RESULT, resultEvent(result));
            results.add(result);
        }
        return List.copyOf(results);
    }

    private ToolResult executeOne(ToolCall call, ToolContext context) {
        List<ToolMiddleware> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(middleware);
        }
        try {
            return invoke(snapshot, 0, call, context);
        } catch (Exception exception) {
            return ToolResult.failure(call.id(), "execution_failed", exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage());
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
