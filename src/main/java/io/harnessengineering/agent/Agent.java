package io.harnessengineering.agent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.harnessengineering.core.CancellationToken;
import io.harnessengineering.llm.LlmProvider;
import io.harnessengineering.llm.LlmRequest;
import io.harnessengineering.llm.LlmSessionWriter;
import io.harnessengineering.session.EventLogSession;
import io.harnessengineering.session.Message;
import io.harnessengineering.session.SessionEventTypes;
import io.harnessengineering.tools.ToolPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * Single-owner virtual-thread agent that turns inbox messages into model steps and
 * tool calls executed with cancellation-aware parallelism. Every visible transition
 * is recorded in the session log.
 */
public final class Agent implements AutoCloseable {
    private final EventLogSession session;
    private final LlmProvider provider;
    private final String model;
    private final ToolPipeline tools;
    private final Inbox inbox = new Inbox();
    private final CountDownLatch terminated = new CountDownLatch(1);
    private volatile AgentState state = AgentState.NEW;
    private volatile CancellationToken currentToken;
    private Thread worker;

    public Agent(EventLogSession session, LlmProvider provider, String model, ToolPipeline tools) {
        this.session = Objects.requireNonNull(session, "session");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.model = Objects.requireNonNull(model, "model");
        this.tools = Objects.requireNonNull(tools, "tools");
        if (model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
    }

    /** Starts the agent's virtual-thread loop once. */
    public synchronized void start() {
        if (state != AgentState.NEW) {
            throw new IllegalStateException("agent is already started");
        }
        state = AgentState.IDLE;
        worker = Thread.ofVirtual().name("agent-" + session.id().value()).start(this::runLoop);
    }

    public Inbox inbox() {
        return inbox;
    }

    public AgentState state() {
        return state;
    }

    /** Cancels only the currently running turn; later inbox items remain available. */
    public void cancelCurrentTurn() {
        CancellationToken token = currentToken;
        if (token != null) {
            token.cancel();
        }
    }

    @Override
    public void close() {
        Thread thread;
        synchronized (this) {
            if (state == AgentState.CLOSED) {
                return;
            }
            inbox.close();
            cancelCurrentTurn();
            thread = worker;
            if (thread == null) {
                state = AgentState.CLOSED;
                terminated.countDown();
                return;
            }
        }
        try {
            terminated.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for agent close", exception);
        }
    }

    private void runLoop() {
        try {
            while (true) {
                state = AgentState.IDLE;
                Message input = inbox.claimNextTurn();
                if (input == null) {
                    return;
                }
                session.append(SessionEventTypes.USER_MESSAGE, JsonNodeFactory.instance.objectNode()
                        .put("role", input.role()).put("content", input.content()));
                runTurn(input);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            state = AgentState.CLOSED;
            terminated.countDown();
        }
    }

    private void runTurn(Message initialInput) {
        CancellationToken token = new CancellationToken();
        currentToken = token;
        state = AgentState.RUNNING;
        int turn = (int) session.events().stream().filter(event -> event.type().equals(SessionEventTypes.TURN_START)).count() + 1;
        session.append(SessionEventTypes.TURN_START, marker("turn", turn));
        String outcome = "completed";
        try {
            List<Message> input = new ArrayList<>();
            input.add(initialInput);
            while (true) {
                token.throwIfCancelled();
                int step = (int) session.events().stream().filter(event -> event.type().equals(SessionEventTypes.STEP_START)).count() + 1;
                session.append(SessionEventTypes.STEP_START, marker("step", step));
                LlmRequest request = new LlmRequest(provider.providerId(), model, mergeHistory(input), tools.definitions());
                LlmSessionWriter.AssistantResponse response = new LlmSessionWriter(session).stream(provider, request);
                token.throwIfCancelled();
                if (response.toolCalls().isEmpty()) {
                    session.append(SessionEventTypes.STEP_END, marker("status", "completed"));
                    return;
                }
                tools.executeParallel(response.toolCalls(), session, token);
                session.append(SessionEventTypes.STEP_END, marker("status", "tool-executed"));
                input = new ArrayList<>(inbox.claimNextStep());
            }
        } catch (java.util.concurrent.CancellationException exception) {
            outcome = "cancelled";
        } catch (RuntimeException exception) {
            outcome = "failed";
            throw exception;
        } finally {
            currentToken = null;
            session.append(SessionEventTypes.TURN_END, marker("status", outcome));
        }
    }

    private List<Message> mergeHistory(List<Message> input) {
        List<Message> messages = new ArrayList<>(session.deriveMessages());
        messages.addAll(input);
        return List.copyOf(messages);
    }


    private static ObjectNode marker(String field, Object value) {
        ObjectNode marker = JsonNodeFactory.instance.objectNode();
        if (value instanceof Integer integer) {
            marker.put(field, integer);
        } else {
            marker.put(field, String.valueOf(value));
        }
        return marker;
    }
}
