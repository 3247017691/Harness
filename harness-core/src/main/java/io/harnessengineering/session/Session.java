package io.harnessengineering.session;

import com.fasterxml.jackson.databind.JsonNode;
import io.harnessengineering.core.Effect;
import java.util.List;

/** Append-only session log with deterministic model-message projection. */
public interface Session {
    SessionId id();

    /**
     * Validates, persists, and then publishes an event.
     *
     * @param type event type
     * @param data event payload
     * @return committed immutable event
     */
    SessionEvent append(String type, JsonNode data);

    /** @return committed event snapshot in sequence order */
    List<SessionEvent> events();

    /** @return model-visible messages projected from committed events */
    List<Message> deriveMessages();

    /**
     * Adds a listener notified only after an event has committed successfully.
     *
     * @param listener event listener
     * @return reversible registration
     */
    Effect onEvent(SessionEventListener listener);
}
