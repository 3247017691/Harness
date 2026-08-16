package io.harnessengineering.llm;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.harnessengineering.session.EventLogSession;
import io.harnessengineering.session.Message;
import io.harnessengineering.session.SessionEventTypes;
import io.harnessengineering.tools.ToolCall;
import java.util.Objects;
import java.util.stream.Stream;

/** Consumes a provider stream, recording chunks and its assembled response in a Session. */
public final class LlmSessionWriter {
    private final EventLogSession session;

    public LlmSessionWriter(EventLogSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /**
     * Streams and records one assistant response.
     *
     * @param provider provider to invoke
     * @param request request sent to provider
     * @return assembled response, including a tool call when emitted
     */
    public AssistantResponse stream(LlmProvider provider, LlmRequest request) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(request, "request");
        StringBuilder text = new StringBuilder();
        ToolCall toolCall = null;
        try (Stream<StreamChunk> chunks = provider.stream(request)) {
            for (StreamChunk chunk : chunks.toList()) {
                ObjectNode event = JsonNodeFactory.instance.objectNode()
                        .put("text", chunk.text())
                        .put("done", chunk.done());
                if (chunk.toolCallId() != null) {
                    event.put("toolCallId", chunk.toolCallId());
                    event.put("toolName", chunk.toolName());
                    event.put("arguments", chunk.toolArguments());
                    toolCall = new ToolCall(chunk.toolCallId(), chunk.toolName(), chunk.toolArguments());
                }
                session.append(SessionEventTypes.ASSISTANT_CHUNK, event);
                text.append(chunk.text());
            }
        }
        ObjectNode message = JsonNodeFactory.instance.objectNode()
                .put("role", "assistant")
                .put("content", text.toString());
        session.append(SessionEventTypes.ASSISTANT_MESSAGE, message);
        return new AssistantResponse(new Message("assistant", text.toString()), toolCall);
    }

    public record AssistantResponse(Message message, ToolCall toolCall) {
        public AssistantResponse {
            Objects.requireNonNull(message, "message");
        }
    }
}
