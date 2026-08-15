package io.harnessengineering.session;

import java.util.Objects;

/** Model-visible message projected from a session event. */
public record Message(String role, String content) {
    public Message {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        if (role.isBlank()) {
            throw new IllegalArgumentException("message role must not be blank");
        }
    }
}
