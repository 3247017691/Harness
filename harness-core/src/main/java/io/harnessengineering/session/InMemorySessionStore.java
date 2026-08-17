package io.harnessengineering.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Thread-safe in-memory session event backend. */
public final class InMemorySessionStore implements SessionStore {
    private final Map<SessionId, List<SessionEvent>> events = new HashMap<>();

    @Override
    public synchronized void append(SessionId sessionId, SessionEvent event) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(event, "event");
        List<SessionEvent> sessionEvents = events.computeIfAbsent(sessionId, ignored -> new ArrayList<>());
        long expectedSequence = sessionEvents.size() + 1L;
        if (event.sequence() != expectedSequence) {
            throw new IllegalArgumentException("expected sequence " + expectedSequence + " but got " + event.sequence());
        }
        sessionEvents.add(copy(event));
    }

    @Override
    public synchronized List<SessionEvent> load(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return events.getOrDefault(sessionId, List.of()).stream().map(InMemorySessionStore::copy).toList();
    }

    @Override
    public synchronized List<SessionId> list() {
        return events.keySet().stream()
                .sorted(java.util.Comparator.comparing(SessionId::value))
                .toList();
    }

    private static SessionEvent copy(SessionEvent event) {
        return new SessionEvent(event.sequence(), event.time(), event.type(), event.data());
    }
}
