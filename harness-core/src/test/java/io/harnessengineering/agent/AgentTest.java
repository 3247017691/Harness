package io.harnessengineering.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.harnessengineering.llm.LlmProvider;
import io.harnessengineering.llm.LlmRequest;
import io.harnessengineering.llm.StreamChunk;
import io.harnessengineering.session.EventLogSession;
import io.harnessengineering.session.InMemorySessionStore;
import io.harnessengineering.session.Message;
import io.harnessengineering.session.SessionEventTypes;
import io.harnessengineering.session.SessionId;
import io.harnessengineering.tools.Tool;
import io.harnessengineering.tools.ToolCall;
import io.harnessengineering.tools.ToolContext;
import io.harnessengineering.tools.ToolDefinition;
import io.harnessengineering.tools.ToolPipeline;
import io.harnessengineering.tools.ToolRegistry;
import io.harnessengineering.tools.ToolResult;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AgentTest {
    @Test
    void followupCreatesTurnAndRecordsModelVisibleHistory() throws Exception {
        EventLogSession session = session("followup");
        Agent agent = new Agent(session, provider(request -> Stream.of(StreamChunk.text("answer"))), "test",
                new ToolPipeline(new ToolRegistry()));
        agent.start();
        CountDownLatch turnEnded = awaitEvent(session, SessionEventTypes.TURN_END);
        agent.inbox().followup(new Message("user", "question"));

        assertTrue(turnEnded.await(2, TimeUnit.SECONDS));
        agent.close();

        assertEquals(List.of(new Message("user", "question"), new Message("assistant", "answer")),
                session.deriveMessages());
        assertEquals(1, count(session, SessionEventTypes.TURN_START));
        assertEquals(1, count(session, SessionEventTypes.STEP_START));
        assertEquals("completed", last(session, SessionEventTypes.TURN_END).data().path("status").asText());
    }

    @Test
    void toolCallCreatesFollowupStepAndExecutesTool() throws Exception {
        EventLogSession session = session("tools");
        AtomicInteger requests = new AtomicInteger();
        LlmProvider provider = provider(request -> requests.getAndIncrement() == 0
                ? Stream.of(StreamChunk.toolCall("call-1", "echo", "{}"))
                : Stream.of(StreamChunk.text("finished")));
        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool());
        Agent agent = new Agent(session, provider, "test", new ToolPipeline(registry));
        agent.start();
        CountDownLatch turnEnded = awaitEvent(session, SessionEventTypes.TURN_END);
        agent.inbox().followup(new Message("user", "run tool"));

        assertTrue(turnEnded.await(2, TimeUnit.SECONDS));
        agent.close();

        assertEquals(2, count(session, SessionEventTypes.STEP_START));
        assertEquals(1, count(session, SessionEventTypes.TOOL_CALL));
        assertEquals(1, count(session, SessionEventTypes.TOOL_RESULT));
        assertEquals(2, requests.get());
    }

    @Test
    void cancellationPreventsToolDispatchAndCloseWaitsForProvider() throws Exception {
        EventLogSession session = session("cancel");
        CountDownLatch streamStarted = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        LlmProvider provider = provider(request -> {
            streamStarted.countDown();
            try {
                releaseStream.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return Stream.of(StreamChunk.toolCall("call-1", "echo", "{}"));
        });
        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool(calls));
        Agent agent = new Agent(session, provider, "test", new ToolPipeline(registry));
        agent.start();
        agent.inbox().followup(new Message("user", "cancel"));
        assertTrue(streamStarted.await(2, TimeUnit.SECONDS));

        CountDownLatch turnEnded = awaitEvent(session, SessionEventTypes.TURN_END);
        agent.cancelCurrentTurn();
        releaseStream.countDown();
        assertTrue(turnEnded.await(2, TimeUnit.SECONDS));
        agent.close();

        assertEquals(0, calls.get());
        assertEquals("cancelled", last(session, SessionEventTypes.TURN_END).data().path("status").asText());
        assertEquals(AgentState.CLOSED, agent.state());
    }

    private static EventLogSession session(String id) {
        return new EventLogSession(new SessionId(id), new InMemorySessionStore());
    }

    private static LlmProvider provider(java.util.function.Function<LlmRequest, Stream<StreamChunk>> stream) {
        return new LlmProvider() {
            @Override public String providerId() { return "fake"; }
            @Override public Stream<StreamChunk> stream(LlmRequest request) { return stream.apply(request); }
        };
    }

    private static Tool echoTool() {
        return echoTool(new AtomicInteger());
    }

    private static Tool echoTool(AtomicInteger calls) {
        return new Tool() {
            @Override public ToolDefinition definition() {
                return new ToolDefinition("echo", "echoes", JsonNodeFactory.instance.objectNode());
            }
            @Override public ToolResult execute(ToolCall call, ToolContext context) {
                calls.incrementAndGet();
                return ToolResult.success(call.id(), "ok");
            }
        };
    }

    private static CountDownLatch awaitEvent(EventLogSession session, String type) {
        CountDownLatch latch = new CountDownLatch(1);
        session.onEvent(event -> {
            if (event.type().equals(type)) {
                latch.countDown();
            }
        });
        return latch;
    }

    private static long count(EventLogSession session, String type) {
        return session.events().stream().filter(event -> event.type().equals(type)).count();
    }

    private static io.harnessengineering.session.SessionEvent last(EventLogSession session, String type) {
        return session.events().stream().filter(event -> event.type().equals(type)).reduce((first, second) -> second)
                .orElseThrow();
    }
}
