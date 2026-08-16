package io.harnessengineering.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/** Provider-neutral tool schema exposed to a model. */
public record ToolDefinition(String name, String description, JsonNode parameters) {
    public ToolDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(parameters, "parameters");
        if (name.isBlank()) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
        parameters = parameters.deepCopy();
    }
}
