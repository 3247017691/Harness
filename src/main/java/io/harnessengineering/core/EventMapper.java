package io.harnessengineering.core;

/** Maps an event payload to a result. */
@FunctionalInterface
public interface EventMapper<T, R> {
    R apply(T value) throws Exception;
}
