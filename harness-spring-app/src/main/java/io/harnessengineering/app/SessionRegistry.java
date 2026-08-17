package io.harnessengineering.app;

import io.harnessengineering.session.EventLogSession;
import io.harnessengineering.session.SessionId;
import io.harnessengineering.session.SessionStore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Shared in-JVM view of Sessions over the backing store. Controllers and agents use
 * the same instance so events appended by an Agent reach SSE subscribers immediately.
 */
@Component
public final class SessionRegistry {
    private final SessionStore store;
    private final Map<SessionId, EventLogSession> sessions = new ConcurrentHashMap<>();

    public SessionRegistry(SessionStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public EventLogSession session(SessionId id) {
        Objects.requireNonNull(id, "id");
        return sessions.computeIfAbsent(id, ignored -> new EventLogSession(id, store));
    }

    /** Every known session id: persisted ids plus ids opened in this JVM, sorted. */
    public List<SessionId> list() {
        return sessions.keySet().stream()
                .sorted(Comparator.comparing(SessionId::value))
                .toList();
    }

    /** @return a fresh random session id, already open in this registry */
    public SessionId create() {
        SessionId id = SessionId.random();
        session(id);
        return id;
    }
}
