package io.harnessengineering.config;

import java.util.Objects;

/** Stable identifier for a configured plugin instance. */
public record PluginId(String value) {
    public PluginId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("plugin ID must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
