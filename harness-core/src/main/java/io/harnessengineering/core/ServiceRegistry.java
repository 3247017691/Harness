package io.harnessengineering.core;

import java.util.Objects;
import java.util.Optional;

/**
 * Stores typed services and reports service availability changes.
 */
public interface ServiceRegistry extends AutoCloseable {
    /**
     * Registers a service value for a key.
     *
     * @param key service identifier
     * @param service service value
     * @param <T> service value type
     * @return an effect that removes this exact registration
     * @throws IllegalStateException if the key is already registered
     */
    <T> Effect register(ServiceKey<T> key, T service);

    /**
     * Returns the service for a key when available.
     *
     * @param key service identifier
     * @param <T> service value type
     * @return available service
     */
    <T> Optional<T> find(ServiceKey<T> key);

    /**
     * Registers a listener notified after a service is added or removed.
     *
     * @param listener service change listener
     * @return an effect that removes the listener
     */
    Effect onChange(ServiceChangeListener listener);

    /**
     * Returns whether every key is currently available.
     *
     * @param keys required keys
     * @return true if every key is present
     */
    default boolean containsAll(Iterable<ServiceKey<?>> keys) {
        for (ServiceKey<?> key : keys) {
            if (findUntyped(Objects.requireNonNull(key, "key")).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private Optional<?> findUntyped(ServiceKey<?> key) {
        return find(key);
    }

    /**
     * Closes the registry and removes all services and listeners.
     */
    @Override
    void close();
}
