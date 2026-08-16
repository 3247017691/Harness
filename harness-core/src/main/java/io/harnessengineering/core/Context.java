package io.harnessengineering.core;

import java.util.Objects;

/** Runtime services shared by plugins. */
public final class Context implements AutoCloseable {
    private final ServiceRegistry services;
    private final EventBus events;

    public Context(ServiceRegistry services, EventBus events) {
        this.services = Objects.requireNonNull(services, "services");
        this.events = Objects.requireNonNull(events, "events");
    }

    public ServiceRegistry services() {
        return services;
    }

    public EventBus events() {
        return events;
    }

    public <T> java.util.Optional<T> get(ServiceKey<T> key) {
        return services.find(key);
    }

    @Override
    public void close() {
        events.close();
        services.close();
    }
}
