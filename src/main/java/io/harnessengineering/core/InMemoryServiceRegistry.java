package io.harnessengineering.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Thread-safe in-memory implementation of {@link ServiceRegistry}.
 */
public final class InMemoryServiceRegistry implements ServiceRegistry {
    private final Map<ServiceKey<?>, Object> services = new LinkedHashMap<>();
    private final List<ServiceChangeListener> listeners = new ArrayList<>();
    private boolean closed;

    @Override
    public <T> Effect register(ServiceKey<T> key, T service) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(service, "service");
        if (!key.type().isInstance(service)) {
            throw new IllegalArgumentException("service does not match key type: " + key.name());
        }

        synchronized (this) {
            ensureOpen();
            if (services.containsKey(key)) {
                throw new IllegalStateException("service already registered: " + key.name());
            }
            services.put(key, service);
        }
        notifyListeners(key, true);
        return new Registration(key, service);
    }

    @Override
    public synchronized <T> Optional<T> find(ServiceKey<T> key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(services.get(key)).map(key.type()::cast);
    }

    @Override
    public synchronized Effect onChange(ServiceChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        ensureOpen();
        listeners.add(listener);
        return new ListenerRegistration(listener);
    }

    @Override
    public void close() {
        List<ServiceKey<?>> keys;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            keys = List.copyOf(services.keySet());
            services.clear();
            listeners.clear();
        }
        // Listeners are deliberately gone before close to prevent new lifecycle work.
        keys.forEach(key -> { });
    }

    private void remove(ServiceKey<?> key, Object expectedValue) {
        boolean removed;
        synchronized (this) {
            removed = services.get(key) == expectedValue;
            if (removed) {
                services.remove(key);
            }
        }
        if (removed) {
            notifyListeners(key, false);
        }
    }

    private void removeListener(ServiceChangeListener listener) {
        synchronized (this) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners(ServiceKey<?> key, boolean available) {
        List<ServiceChangeListener> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(listeners);
        }
        RuntimeException failure = null;
        for (ServiceChangeListener listener : snapshot) {
            try {
                listener.onServiceChanged(key, available);
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("service registry is closed");
        }
    }

    private final class Registration implements Effect {
        private final ServiceKey<?> key;
        private final Object service;
        private boolean closed;

        private Registration(ServiceKey<?> key, Object service) {
            this.key = key;
            this.service = service;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            remove(key, service);
        }
    }

    private final class ListenerRegistration implements Effect {
        private final ServiceChangeListener listener;
        private boolean closed;

        private ListenerRegistration(ServiceChangeListener listener) {
            this.listener = listener;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            removeListener(listener);
        }
    }
}
