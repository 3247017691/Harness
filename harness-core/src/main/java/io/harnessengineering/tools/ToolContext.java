package io.harnessengineering.tools;

import io.harnessengineering.session.Session;
import java.util.Objects;

/** Execution context supplied to a tool invocation. */
public record ToolContext(Session session) {
    public ToolContext {
        Objects.requireNonNull(session, "session");
    }
}
