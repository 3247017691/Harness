package io.harnessengineering.session;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Objects;

/** Immutable committed entry in a session event log. */
public record SessionEvent(long sequence, Instant time, String type, JsonNode data) {
    public SessionEvent {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(data, "data");
        if (type.isBlank()) {
            throw new IllegalArgumentException("event type must not be blank");
        }
        data = data.deepCopy();
    }
}
