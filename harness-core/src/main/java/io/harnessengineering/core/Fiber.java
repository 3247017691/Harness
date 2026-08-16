package io.harnessengineering.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Owns one plugin activation and all cleanup effects it produces.
 */
public final class Fiber implements AutoCloseable {
    private final Plugin plugin;
    private final Context context;
    private final List<Effect> effects = new ArrayList<>();
    private FiberState state = FiberState.PENDING;
    private Throwable failure;

    Fiber(Plugin plugin, Context context) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.context = Objects.requireNonNull(context, "context");
    }

    /** Re-evaluates required services and loads or unloads this fiber. */
    public synchronized void refresh() {
        if (state == FiberState.DISPOSED || state == FiberState.LOADING || state == FiberState.UNLOADING) {
            return;
        }
        boolean ready = context.services().containsAll(plugin.requires());
        if (ready && state == FiberState.PENDING) {
            load();
        } else if (!ready && state == FiberState.ACTIVE) {
            unload(FiberState.PENDING);
        }
    }

    /** Adds an additional owned cleanup effect while the fiber is active. */
    public synchronized void addEffect(Effect effect) {
        Objects.requireNonNull(effect, "effect");
        if (state != FiberState.ACTIVE) {
            throw new IllegalStateException("cannot add an effect when fiber is " + state);
        }
        effects.add(effect);
    }

    public synchronized FiberState state() {
        return state;
    }

    public synchronized Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }

    public Plugin plugin() {
        return plugin;
    }

    @Override
    public synchronized void close() {
        if (state == FiberState.DISPOSED) {
            return;
        }
        if (state == FiberState.ACTIVE) {
            unload(FiberState.DISPOSED);
        } else {
            state = FiberState.DISPOSED;
        }
    }

    private void load() {
        state = FiberState.LOADING;
        try {
            Effect effect = plugin.apply(context);
            effects.add(effect == null ? Effect.noop() : effect);
            failure = null;
            state = FiberState.ACTIVE;
        } catch (Throwable throwable) {
            failure = throwable;
            state = FiberState.FAILED;
        }
    }

    private void unload(FiberState targetState) {
        state = FiberState.UNLOADING;
        RuntimeException cleanupFailure = null;
        for (int index = effects.size() - 1; index >= 0; index--) {
            try {
                effects.get(index).close();
            } catch (RuntimeException exception) {
                if (cleanupFailure == null) {
                    cleanupFailure = exception;
                } else {
                    cleanupFailure.addSuppressed(exception);
                }
            }
        }
        effects.clear();
        state = targetState;
        if (cleanupFailure != null) {
            failure = cleanupFailure;
        }
    }
}
