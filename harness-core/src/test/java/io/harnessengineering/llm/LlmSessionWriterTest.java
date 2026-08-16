package io.harnessengineering.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.harnessengineering.session.EventLogSession;
import io.harnessengineering.session.InMemorySessionStore;
import io.harnessengineering.session.Message;
import io.harnessengineering.session.SessionEventTypes;
import io.harnessengineering.session.SessionId;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LlmSessionWriterTest {
    @Test
    void recordsChunksAndAssembledAssistantMessage() {
        EventLogSession session = new EventLogSession(new SessionId("llm-session"), new InMemorySessionStore());
        LlmProvider provider = new LlmProvider() {
            @Override public String providerId() { return "fake"; }
            @Override public Stream<StreamChunk> stream(LlmRequest request) {
                return Stream.of(StreamChunk.text("hello "), StreamChunk.text("world"), StreamChunk.completed());
            }
        };
        LlmRequest request = new LlmRequest("fake", "test", List.of(new Message("user", "hi")), List.of());

        LlmSessionWriter.AssistantResponse response = new LlmSessionWriter(session).stream(provider, request);

        assertEquals(new Message("assistant", "hello world"), response.message());
        assertTrue(response.toolCalls().isEmpty());
        assertEquals(List.of(SessionEventTypes.ASSISTANT_CHUNK, SessionEventTypes.ASSISTANT_CHUNK,
                        SessionEventTypes.ASSISTANT_CHUNK, SessionEventTypes.ASSISTANT_MESSAGE),
                session.events().stream().map(event -> event.type()).toList());
        assertEquals(List.of(new Message("assistant", "hello world")), session.deriveMessages());
    }

    @Test
    void extractsAndRecordsToolCallChunk() {
        EventLogSession session = new EventLogSession(new SessionId("tool-call-session"), new InMemorySessionStore());
        LlmProvider provider = new LlmProvider() {
            @Override public String providerId() { return "fake"; }
            @Override public Stream<StreamChunk> stream(LlmRequest request) {
                return Stream.of(StreamChunk.toolCall("call-1", "weather", "{\"city\":\"Paris\"}"));
            }
        };

        LlmSessionWriter.AssistantResponse response = new LlmSessionWriter(session).stream(provider,
                new LlmRequest("fake", "test", List.of(), List.of()));

        assertEquals("weather", response.toolCalls().getFirst().name());
        assertEquals("call-1", session.events().getFirst().data().path("toolCallId").asText());
    }

    @Test
    void collectsMultipleToolCallsInStreamOrder() {
        EventLogSession session = new EventLogSession(new SessionId("multi-tool-session"), new InMemorySessionStore());
        LlmProvider provider = new LlmProvider() {
            @Override public String providerId() { return "fake"; }
            @Override public Stream<StreamChunk> stream(LlmRequest request) {
                return Stream.of(
                        StreamChunk.toolCall("call-1", "alpha", "{}"),
                        StreamChunk.toolCall("call-2", "beta", "{}"));
            }
        };

        LlmSessionWriter.AssistantResponse response = new LlmSessionWriter(session).stream(provider,
                new LlmRequest("fake", "test", List.of(), List.of()));

        assertEquals(2, response.toolCalls().size());
        assertEquals(List.of("alpha", "beta"), response.toolCalls().stream().map(call -> call.name()).toList());
    }
}
