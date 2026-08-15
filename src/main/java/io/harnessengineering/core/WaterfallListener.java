package io.harnessengineering.core;

/** A middleware listener that may continue a waterfall with a new value. */
@FunctionalInterface
public interface WaterfallListener<T> {
    T onEvent(T value, Next<T> next) throws Exception;

    @FunctionalInterface
    interface Next<T> {
        T apply(T value) throws Exception;
    }
}
