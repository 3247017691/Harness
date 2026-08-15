package io.harnessengineering.session;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for an append-only session event log. */
public record SessionId(String value) {
    public SessionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("session ID must not be blank");
        }
    }

    /** @return a randomly generated session identifier */
    public static SessionId random() {
        return new SessionId(UUID.randomUUID().toString());
    }
}
