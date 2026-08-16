package io.harnessengineering.core;

/** Indicates that an event listener failed during synchronous dispatch. */
public final class EventDispatchException extends RuntimeException {
    public EventDispatchException(Exception cause) {
        super(cause);
    }
}
