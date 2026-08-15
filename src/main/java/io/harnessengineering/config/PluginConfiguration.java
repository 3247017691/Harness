package io.harnessengineering.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/** Declarative configuration for one plugin instance. */
public record PluginConfiguration(PluginId id, String type, boolean enabled, JsonNode options) {
    public PluginConfiguration {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(options, "options");
        if (type.isBlank()) {
            throw new IllegalArgumentException("plugin type must not be blank");
        }
        options = options.deepCopy();
    }
}
