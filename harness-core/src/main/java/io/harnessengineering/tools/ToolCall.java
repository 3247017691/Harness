package io.harnessengineering.tools;

import java.util.Objects;

/** Structured model request to invoke one registered tool. */
public record ToolCall(String id, String name, String arguments) {
    public ToolCall {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(arguments, "arguments");
        if (id.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("tool call ID and name must not be blank");
        }
    }
}
