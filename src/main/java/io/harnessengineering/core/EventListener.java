package io.harnessengineering.core;

/** Receives an event payload. */
@FunctionalInterface
public interface EventListener<T> {
    void onEvent(T value);
}
