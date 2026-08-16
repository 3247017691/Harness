package io.harnessengineering.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.harnessengineering.core.Effect;
import io.harnessengineering.session.EventLogSession;
import io.harnessengineering.session.InMemorySessionStore;
import io.harnessengineering.session.SessionId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ToolPipelineTest {
    @Test
    void executesCallsSeriallyAndRecordsResults() {
        ToolRegistry registry = new ToolRegistry();
        AtomicInteger order = new AtomicInteger();
        registry.register(tool("first", call -> {
            assertEquals(0, order.getAndIncrement());
            return ToolResult.success(call.id(), "one");
        }));
        registry.register(tool("second", call -> {
            assertEquals(1, order.getAndIncrement());
            return ToolResult.success(call.id(), "two");
        }));
        EventLogSession session = new EventLogSession(new SessionId("tools"), new InMemorySessionStore());

        List<ToolResult> results = new ToolPipeline(registry).execute(List.of(
                new ToolCall("1", "first", "{}"), new ToolCall("2", "second", "{}")), session);

        assertEquals(List.of("one", "two"), results.stream().map(ToolResult::content).toList());
        assertEquals(List.of("tool/call", "tool/result", "tool/call", "tool/result"),
                session.events().stream().map(event -> event.type()).toList());
    }

    @Test
    void convertsMissingAndThrownToolsToStructuredFailures() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(tool("broken", call -> { throw new IllegalStateException("boom"); }));
        EventLogSession session = new EventLogSession(new SessionId("failures"), new InMemorySessionStore());

        List<ToolResult> results = new ToolPipeline(registry).execute(List.of(
                new ToolCall("missing-call", "missing", "{}"),
                new ToolCall("broken-call", "broken", "{}")), session);

        assertEquals(List.of("tool_not_found", "execution_failed"), results.stream().map(ToolResult::errorCode).toList());
        assertTrue(results.stream().noneMatch(ToolResult::success));
    }

    @Test
    void middlewareCanInterceptAndIsReversible() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(tool("real", call -> ToolResult.success(call.id(), "real")));
        ToolPipeline pipeline = new ToolPipeline(registry);
        Effect middleware = pipeline.use((call, context, next) -> ToolResult.success(call.id(), "intercepted"));
        EventLogSession session = new EventLogSession(new SessionId("middleware"), new InMemorySessionStore());

        assertEquals("intercepted", pipeline.execute(List.of(new ToolCall("1", "real", "{}")), session)
                .getFirst().content());
        middleware.close();
        assertEquals("real", pipeline.execute(List.of(new ToolCall("2", "real", "{}")), session)
                .getFirst().content());
    }

    @Test
    void toolRegistrationDisappearsAfterEffectCloses() {
        ToolRegistry registry = new ToolRegistry();
        Effect registration = registry.register(tool("temporary", call -> ToolResult.success(call.id(), "ok")));
        assertEquals(1, registry.definitions().size());
        registration.close();
        registration.close();
        assertEquals(0, registry.definitions().size());
    }

    private static Tool tool(String name, Executor executor) {
        return new Tool() {
            @Override public ToolDefinition definition() {
                return new ToolDefinition(name, "test tool", JsonNodeFactory.instance.objectNode());
            }
            @Override public ToolResult execute(ToolCall call, ToolContext context) throws Exception {
                return executor.execute(call);
            }
        };
    }

    @FunctionalInterface
    private interface Executor {
        ToolResult execute(ToolCall call) throws Exception;
    }
}
