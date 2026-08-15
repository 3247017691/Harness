package io.harnessengineering.config;

import io.harnessengineering.core.Context;
import io.harnessengineering.core.Fiber;
import io.harnessengineering.core.FiberState;
import io.harnessengineering.core.InMemoryEventBus;
import io.harnessengineering.core.InMemoryServiceRegistry;
import io.harnessengineering.core.Plugin;
import io.harnessengineering.core.PluginManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies validated plugin configuration transactionally. Each configured group owns
 * an isolated context and plugin manager, so its service registrations do not leak.
 */
public final class ConfigurationRuntime implements AutoCloseable {
    private final Map<String, PluginFactory> factories;
    private RunningConfiguration current;
    private boolean closed;

    public ConfigurationRuntime(Map<String, PluginFactory> factories) {
        Objects.requireNonNull(factories, "factories");
        this.factories = Map.copyOf(factories);
    }

    /**
     * Builds a replacement configuration before closing the active one. A failed
     * candidate is closed and leaves the existing configuration untouched.
     *
     * @param configuration validated composition configuration
     */
    public synchronized void apply(HarnessConfiguration configuration) {
        ensureOpen();
        Objects.requireNonNull(configuration, "configuration");
        RunningConfiguration candidate = null;
        try {
            candidate = build(configuration);
        } catch (RuntimeException exception) {
            if (candidate != null) {
                candidate.close();
            }
            throw exception;
        }
        RunningConfiguration previous = current;
        current = candidate;
        if (previous != null) {
            previous.close();
        }
    }

    /** @return active fibers keyed by their configured plugin ID. */
    public synchronized Map<PluginId, Fiber> fibers() {
        return current == null ? Map.of() : current.fibers();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (current != null) {
            current.close();
            current = null;
        }
    }

    private RunningConfiguration build(HarnessConfiguration configuration) {
        Map<PluginId, Fiber> fibers = new LinkedHashMap<>();
        Scope root = buildScope("root", configuration.plugins(), configuration.groups(), fibers);
        return new RunningConfiguration(root, fibers);
    }

    private Scope buildScope(String id, List<PluginConfiguration> plugins,
                             List<PluginGroupConfiguration> groups, Map<PluginId, Fiber> fibers) {
        Context context = new Context(new InMemoryServiceRegistry(), new InMemoryEventBus());
        PluginManager manager = new PluginManager(context);
        List<Scope> children = new ArrayList<>();
        try {
            for (PluginConfiguration configuration : plugins) {
                if (!configuration.enabled()) {
                    continue;
                }
                if (fibers.containsKey(configuration.id())) {
                    throw new ConfigurationException("duplicate enabled plugin ID: " + configuration.id());
                }
                PluginFactory factory = factories.get(configuration.type());
                if (factory == null) {
                    throw new ConfigurationException("plugin " + configuration.id()
                            + " uses unknown type: " + configuration.type());
                }
                Plugin plugin;
                try {
                    plugin = Objects.requireNonNull(factory.create(configuration), "factory result");
                } catch (Exception exception) {
                    throw new ConfigurationException("plugin " + configuration.id() + " could not be created", exception);
                }
                Fiber fiber = manager.mount(plugin);
                if (fiber.state() == FiberState.FAILED) {
                    throw new ConfigurationException("plugin " + configuration.id() + " failed to start",
                            fiber.failure().orElseThrow());
                }
                if (fiber.state() != FiberState.ACTIVE) {
                    throw new ConfigurationException("plugin " + configuration.id()
                            + " has unresolved dependencies: " + plugin.requires());
                }
                fibers.put(configuration.id(), fiber);
            }
            for (PluginGroupConfiguration group : groups) {
                if (group.enabled()) {
                    children.add(buildScope(group.id(), group.plugins(), group.groups(), fibers));
                }
            }
            return new Scope(id, context, manager, children);
        } catch (RuntimeException exception) {
            for (int index = children.size() - 1; index >= 0; index--) {
                children.get(index).close();
            }
            manager.close();
            context.close();
            throw exception;
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("configuration runtime is closed");
        }
    }

    private record RunningConfiguration(Scope root, Map<PluginId, Fiber> fibers) implements AutoCloseable {
        private RunningConfiguration {
            fibers = Map.copyOf(fibers);
        }

        @Override
        public void close() {
            root.close();
        }
    }

    private record Scope(String id, Context context, PluginManager manager, List<Scope> children)
            implements AutoCloseable {
        private Scope {
            children = List.copyOf(children);
        }

        @Override
        public void close() {
            RuntimeException failure = null;
            for (int index = children.size() - 1; index >= 0; index--) {
                try {
                    children.get(index).close();
                } catch (RuntimeException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            try {
                manager.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
            context.close();
            if (failure != null) {
                throw failure;
            }
        }
    }
}
