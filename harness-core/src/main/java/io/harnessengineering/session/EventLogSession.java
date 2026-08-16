package io.harnessengineering.session;

import com.fasterxml.jackson.databind.JsonNode;
import io.harnessengineering.core.Effect;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Default session implementation backed by an append-only {@link SessionStore}. */
public final class EventLogSession implements Session {
    private final SessionId id;
    private final SessionStore store;
    private final SessionEventValidator validator;
    private final Clock clock;
    private final List<SessionEvent> events;
    private final List<SessionEventListener> listeners = new ArrayList<>();

    /** Creates a session by replaying every event currently stored for its ID. */
    public EventLogSession(SessionId id, SessionStore store) {
        this(id, store, new SessionEventValidator(), Clock.systemUTC());
    }

    EventLogSession(SessionId id, SessionStore store, SessionEventValidator validator, Clock clock) {
        this.id = Objects.requireNonNull(id, "id");
        this.store = Objects.requireNonNull(store, "store");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.events = new ArrayList<>(store.load(id));
        validateReplay();
    }

    @Override
    public SessionId id() {
        return id;
    }

    @Override
    public SessionEvent append(String type, JsonNode data) {
        SessionEvent event;
        synchronized (this) {
            validator.validate(type, data);
            event = new SessionEvent(events.size() + 1L, Instant.now(clock), type, data);
            store.append(id, event);
            events.add(event);
        }
        notifyListeners(event);
        return copy(event);
    }

    @Override
    public synchronized List<SessionEvent> events() {
        return events.stream().map(EventLogSession::copy).toList();
    }

    @Override
    public synchronized List<Message> deriveMessages() {
        return events.stream()
                .filter(event -> event.type().equals(SessionEventTypes.USER_MESSAGE)
                        || event.type().equals(SessionEventTypes.ASSISTANT_MESSAGE))
                .map(event -> new Message(event.data().path("role").asText(), event.data().path("content").asText()))
                .toList();
    }

    @Override
    public synchronized Effect onEvent(SessionEventListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return new ListenerEffect(listener);
    }

    private void validateReplay() {
        for (int index = 0; index < events.size(); index++) {
            SessionEvent event = events.get(index);
            if (event.sequence() != index + 1L) {
                throw new IllegalStateException("stored session events are not sequential for " + id);
            }
            validator.validate(event.type(), event.data());
        }
    }

    private void notifyListeners(SessionEvent event) {
        List<SessionEventListener> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(listeners);
        }
        RuntimeException failure = null;
        for (SessionEventListener listener : snapshot) {
            try {
                listener.onEvent(copy(event));
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

    private synchronized void removeListener(SessionEventListener listener) {
        listeners.remove(listener);
    }

    private static SessionEvent copy(SessionEvent event) {
        return new SessionEvent(event.sequence(), event.time(), event.type(), event.data());
    }

    private final class ListenerEffect implements Effect {
        private final SessionEventListener listener;
        private boolean closed;

        private ListenerEffect(SessionEventListener listener) {
            this.listener = listener;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                removeListener(listener);
            }
        }
    }
}
