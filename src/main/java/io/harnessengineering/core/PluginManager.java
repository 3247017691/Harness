package io.harnessengineering.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mounts plugins and refreshes only affected fibers after service changes. */
public final class PluginManager implements AutoCloseable {
    private final Context context;
    private final List<Fiber> fibers = new ArrayList<>();
    private final Effect registrySubscription;
    private boolean closed;

    public PluginManager(Context context) {
        this.context = Objects.requireNonNull(context, "context");
        registrySubscription = context.services().onChange(this::refreshAffected);
    }

    /**
     * Mounts a plugin and evaluates its current service dependencies.
     *
     * @param plugin plugin to mount
     * @return the plugin's lifecycle fiber
     */
    public synchronized Fiber mount(Plugin plugin) {
        if (closed) {
            throw new IllegalStateException("plugin manager is closed");
        }
        Fiber fiber = new Fiber(plugin, context);
        fibers.add(fiber);
        fiber.refresh();
        return fiber;
    }

    @Override
    public void close() {
        List<Fiber> snapshot;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            snapshot = List.copyOf(fibers);
            fibers.clear();
        }
        registrySubscription.close();
        RuntimeException failure = null;
        for (int index = snapshot.size() - 1; index >= 0; index--) {
            try {
                snapshot.get(index).close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void refreshAffected(ServiceKey<?> changedKey, boolean available) {
        List<Fiber> snapshot;
        synchronized (this) {
            if (closed) {
                return;
            }
            snapshot = List.copyOf(fibers);
        }
        for (Fiber fiber : snapshot) {
            if (fiber.plugin().requires().contains(changedKey)) {
                fiber.refresh();
            }
        }
    }
}
