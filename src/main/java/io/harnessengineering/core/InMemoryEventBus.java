package io.harnessengineering.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Default synchronous event bus implementation. */
public final class InMemoryEventBus implements EventBus {
    private final Map<String, List<Handler>> handlers = new LinkedHashMap<>();
    private boolean closed;

    @Override
    public synchronized <T> Effect on(String event, EventListener<T> listener) {
        Objects.requireNonNull(listener, "listener");
        return add(event, (value, next) -> {
            listener.onEvent(cast(value));
            return null;
        });
    }

    @Override
    public synchronized <T, R> Effect onMapped(String event, EventMapper<T, R> listener) {
        Objects.requireNonNull(listener, "listener");
        return add(event, (value, next) -> listener.apply(cast(value)));
    }

    @Override
    public synchronized <T> Effect onWaterfall(String event, WaterfallListener<T> listener) {
        return add(event, (value, next) -> listener.onEvent((T) value, nextValue -> (T) next.apply(nextValue)));
    }

    @Override
    public <T> void emit(String event, T value) {
        handlersFor(event).forEach(handler -> invoke(handler, value));
    }

    @Override
    public <T, R> List<R> parallel(String event, T value) {
        return serial(event, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> List<R> serial(String event, T value) {
        List<R> results = new ArrayList<>();
        for (Handler handler : handlersFor(event)) {
            results.add((R) invoke(handler, value));
        }
        return List.copyOf(results);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> Optional<R> bail(String event, T value) {
        for (Handler handler : handlersFor(event)) {
            Object result = invoke(handler, value);
            if (result != null) {
                return Optional.of((R) result);
            }
        }
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T waterfall(String event, T value, EventMapper<T, T> terminal) {
        return (T) callWaterfall(handlersFor(event), 0, value, terminal);
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            handlers.clear();
        }
    }

    private synchronized Effect add(String event, Handler handler) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(handler, "handler");
        if (closed) {
            throw new IllegalStateException("event bus is closed");
        }
        handlers.computeIfAbsent(event, ignored -> new ArrayList<>()).add(handler);
        return new Subscription(event, handler);
    }

    private synchronized List<Handler> handlersFor(String event) {
        return List.copyOf(handlers.getOrDefault(event, List.of()));
    }

    private Object callWaterfall(List<Handler> snapshot, int index, Object value, EventMapper<?, ?> terminal) {
        if (index == snapshot.size()) {
            return invokeMapper(terminal, value);
        }
        return invoke(snapshot.get(index), value,
                next -> callWaterfall(snapshot, index + 1, next, terminal));
    }

    private Object invoke(Handler handler, Object value) {
        return invoke(handler, value, ignored -> null);
    }

    private Object invoke(Handler handler, Object value,
                          java.util.function.Function<Object, Object> next) {
        try {
            return handler.apply(value, next);
        } catch (Exception exception) {
            throw new EventDispatchException(exception);
        }
    }

    private Object invokeMapper(EventMapper<?, ?> mapper, Object value) {
        try {
            return invokeUntypedMapper(mapper, value);
        } catch (Exception exception) {
            throw new EventDispatchException(exception);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object invokeUntypedMapper(EventMapper mapper, Object value) throws Exception {
        return mapper.apply(value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    @FunctionalInterface
    private interface Handler {
        Object apply(Object value, java.util.function.Function<Object, Object> next) throws Exception;
    }

    private final class Subscription implements Effect {
        private final String event;
        private final Handler handler;
        private boolean closed;

        private Subscription(String event, Handler handler) {
            this.event = event;
            this.handler = handler;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            synchronized (InMemoryEventBus.this) {
                List<Handler> registered = handlers.get(event);
                if (registered != null) {
                    registered.remove(handler);
                    if (registered.isEmpty()) {
                        handlers.remove(event);
                    }
                }
            }
        }
    }
}
