package io.harnessengineering.session;

import java.util.List;

/** Durable backend for committed session events. */
public interface SessionStore {
    /**
     * Appends a committed event to durable storage.
     *
     * @param sessionId session identifier
     * @param event event to persist
     * @throws RuntimeException when persistence fails
     */
    void append(SessionId sessionId, SessionEvent event);

    /**
     * Loads every event in commit order.
     *
     * @param sessionId session identifier
     * @return event snapshot
     */
    List<SessionEvent> load(SessionId sessionId);

    /**
     * Lists every session id currently known to this store.
     *
     * @return session ids, in store-defined order
     */
    List<SessionId> list();
}
