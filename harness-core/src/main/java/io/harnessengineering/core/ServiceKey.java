package io.harnessengineering.core;

import java.util.Objects;

/**
 * A typed identifier for a service registered in a {@link ServiceRegistry}.
 *
 * @param name stable service name within a registry
 * @param type runtime type of the service value
 * @param <T> service value type
 */
public record ServiceKey<T>(String name, Class<T> type) {
    public ServiceKey {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
