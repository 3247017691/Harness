package io.harnessengineering.core;

import java.util.List;
import java.util.Optional;

/** Synchronous event dispatch API with middleware support. */
public interface EventBus extends AutoCloseable {
    <T> Effect on(String event, EventListener<T> listener);

    <T, R> Effect onMapped(String event, EventMapper<T, R> listener);

    <T> Effect onWaterfall(String event, WaterfallListener<T> listener);

    <T> void emit(String event, T value);

    <T, R> List<R> parallel(String event, T value);

    <T, R> List<R> serial(String event, T value);

    <T, R> Optional<R> bail(String event, T value);

    <T> T waterfall(String event, T value, EventMapper<T, T> terminal);

    @Override
    void close();
}
