package io.harnessengineering.session;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** Validates built-in session event payloads before they can be committed. */
public final class SessionEventValidator {
    private static final Set<String> MESSAGE_TYPES = Set.of(
            SessionEventTypes.USER_MESSAGE, SessionEventTypes.ASSISTANT_MESSAGE);

    /**
     * Validates an event type and a snapshot-safe payload shape.
     *
     * @param type event type
     * @param data event payload
     */
    public void validate(String type, JsonNode data) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("event type must not be blank");
        }
        if (data == null || !data.isObject()) {
            throw new IllegalArgumentException("event data must be an object");
        }
        if (MESSAGE_TYPES.contains(type)) {
            JsonNode role = data.get("role");
            JsonNode content = data.get("content");
            if (role == null || !role.isTextual() || role.textValue().isBlank()) {
                throw new IllegalArgumentException(type + " requires a non-blank string role");
            }
            if (content == null || !content.isTextual()) {
                throw new IllegalArgumentException(type + " requires string content");
            }
            if (!data.path("role").asText().equals(type.substring(0, type.indexOf('/')))) {
                throw new IllegalArgumentException(type + " role must match event type");
            }
        }
    }
}
