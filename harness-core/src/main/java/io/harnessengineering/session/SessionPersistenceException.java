package io.harnessengineering.session;

/** Indicates a durable session store could not read or write a valid event log. */
public final class SessionPersistenceException extends RuntimeException {
    public SessionPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public SessionPersistenceException(String message) {
        super(message);
    }
}
