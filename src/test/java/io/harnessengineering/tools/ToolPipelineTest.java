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

    @Test
    void parallelExecutionRunsAllCallsAndRecordsEventsInRequestOrder() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        AtomicInteger executed = new AtomicInteger();
        registry.register(tool("alpha", call -> {
            started.countDown();
            release.await();
            executed.incrementAndGet();
            return ToolResult.success(call.id(), "alpha");
        }));
        registry.register(tool("beta", call -> {
            started.countDown();
            release.await();
            executed.incrementAndGet();
            return ToolResult.success(call.id(), "beta");
        }));
        EventLogSession session = new EventLogSession(new SessionId("parallel"), new InMemorySessionStore());

        java.util.List<ToolResult> results = new java.util.ArrayList<>();
        Thread caller = Thread.ofVirtual().start(() -> results.addAll(new ToolPipeline(registry)
                .executeParallel(List.of(new ToolCall("1", "alpha", "{}"), new ToolCall("2", "beta", "{}")),
                        session)));

        assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
        release.countDown();
        caller.join(java.time.Duration.ofSeconds(2));

        assertEquals(2, executed.get());
        assertEquals(List.of("alpha", "beta"), results.stream().map(ToolResult::content).toList());
        assertEquals(List.of("tool/call", "tool/call", "tool/result", "tool/result"),
                session.events().stream().map(event -> event.type()).toList());
    }

    @Test
    void cancelledTokenPreventsDispatchEntirely() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        AtomicInteger executed = new AtomicInteger();
        registry.register(tool("alpha", call -> {
            executed.incrementAndGet();
            return ToolResult.success(call.id(), "alpha");
        }));
        registry.register(tool("beta", call -> {
            executed.incrementAndGet();
            return ToolResult.success(call.id(), "beta");
        }));
        EventLogSession session = new EventLogSession(new SessionId("cancel-pre"), new InMemorySessionStore());
        io.harnessengineering.core.CancellationToken token = new io.harnessengineering.core.CancellationToken();
        token.cancel();

        List<ToolResult> results = new ToolPipeline(registry).executeParallel(List.of(
                new ToolCall("1", "alpha", "{}"), new ToolCall("2", "beta", "{}")), session, token);

        assertEquals(0, executed.get());
        assertEquals(List.of("cancelled", "cancelled"), results.stream().map(ToolResult::errorCode).toList());
        assertEquals(List.of("tool/call", "tool/call", "tool/result", "tool/result"),
                session.events().stream().map(event -> event.type()).toList());
    }

    @Test
    void cancellationConvergesInFlightCalls() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        AtomicInteger executed = new AtomicInteger();
        registry.register(tool("alpha", call -> {
            started.countDown();
            release.await();
            executed.incrementAndGet();
            return ToolResult.success(call.id(), "alpha");
        }));
        registry.register(tool("beta", call -> {
            started.countDown();
            release.await();
            executed.incrementAndGet();
            return ToolResult.success(call.id(), "beta");
        }));
        EventLogSession session = new EventLogSession(new SessionId("cancel-converge"), new InMemorySessionStore());
        io.harnessengineering.core.CancellationToken token = new io.harnessengineering.core.CancellationToken();

        java.util.List<ToolResult> results = new java.util.ArrayList<>();
        Thread caller = Thread.ofVirtual().start(() -> results.addAll(new ToolPipeline(registry)
                .executeParallel(List.of(new ToolCall("1", "alpha", "{}"), new ToolCall("2", "beta", "{}")),
                        session, token)));

        assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
        token.cancel();
        release.countDown();
        caller.join(java.time.Duration.ofSeconds(2));

        assertEquals(2, executed.get());
        assertEquals(List.of("alpha", "beta"), results.stream().map(ToolResult::content).toList());
        assertEquals(4, session.events().size());
    }

    @Test
    void retrySucceedsAfterTransientFailures() {
        ToolRegistry registry = new ToolRegistry();
        AtomicInteger attempts = new AtomicInteger();
        registry.register(tool("flaky", call -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("transient");
            }
            return ToolResult.success(call.id(), "recovered");
        }));
        EventLogSession session = new EventLogSession(new SessionId("retry"), new InMemorySessionStore());
        ToolPipeline pipeline = new ToolPipeline(registry).retry(RetryPolicy.of(3, java.time.Duration.ZERO));

        ToolResult result = pipeline.execute(List.of(new ToolCall("1", "flaky", "{}")), session).getFirst();

        assertEquals(true, result.success());
        assertEquals("recovered", result.content());
        assertEquals(3, attempts.get());
    }

    @Test
    void retryExhaustsAttemptsAndReturnsFailure() {
        ToolRegistry registry = new ToolRegistry();
        AtomicInteger attempts = new AtomicInteger();
        registry.register(tool("always-fails", call -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("still broken");
        }));
        EventLogSession session = new EventLogSession(new SessionId("retry-exhausted"), new InMemorySessionStore());
        ToolPipeline pipeline = new ToolPipeline(registry).retry(RetryPolicy.of(3, java.time.Duration.ZERO));

        ToolResult result = pipeline.execute(List.of(new ToolCall("1", "always-fails", "{}")), session).getFirst();

        assertEquals(false, result.success());
        assertEquals("execution_failed", result.errorCode());
        assertEquals(3, attempts.get());
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
