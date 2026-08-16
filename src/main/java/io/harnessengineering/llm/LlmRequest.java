package io.harnessengineering.llm;

import io.harnessengineering.session.Message;
import io.harnessengineering.tools.ToolDefinition;
import java.util.List;
import java.util.Objects;

/** Immutable request sent to an LLM provider. */
public record LlmRequest(String provider, String model, List<Message> messages, List<ToolDefinition> tools) {
    public LlmRequest {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(tools, "tools");
        if (provider.isBlank() || model.isBlank()) {
            throw new IllegalArgumentException("provider and model must not be blank");
        }
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }
}
