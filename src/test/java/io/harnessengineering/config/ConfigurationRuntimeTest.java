package io.harnessengineering.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.harnessengineering.core.Effect;
import io.harnessengineering.core.Plugin;
import io.harnessengineering.core.ServiceKey;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConfigurationRuntimeTest {
    private final ConfigurationLoader loader = new ConfigurationLoader();

    @Test
    void disabledPluginsAreNotCreatedAndNestedGroupsAreApplied() {
        AtomicInteger created = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        ConfigurationRuntime runtime = new ConfigurationRuntime(Map.of("counter", configuration -> {
            created.incrementAndGet();
            return simplePlugin(configuration.id().value(), closed);
        }));

        runtime.apply(loader.parse("""
                plugins:
                  - {id: active, type: counter}
                  - {id: disabled, type: counter, enabled: false}
                groups:
                  - id: nested
                    plugins:
                      - {id: nested-active, type: counter}
                """));

        assertEquals(2, created.get());
        assertEquals(2, runtime.fibers().size());
        assertTrue(runtime.fibers().containsKey(new PluginId("active")));
        assertTrue(runtime.fibers().containsKey(new PluginId("nested-active")));
        assertFalse(runtime.fibers().containsKey(new PluginId("disabled")));

        runtime.close();
        assertEquals(2, closed.get());
    }

    @Test
    void failedReplacementKeepsExistingConfigurationActive() {
        AtomicInteger closed = new AtomicInteger();
        ConfigurationRuntime runtime = new ConfigurationRuntime(Map.of(
                "counter", configuration -> simplePlugin(configuration.id().value(), closed),
                "broken", configuration -> new Plugin() {
                    @Override public String name() { return "broken"; }
                    @Override public List<ServiceKey<?>> requires() { return List.of(); }
                    @Override public Effect apply(io.harnessengineering.core.Context context) {
                        throw new IllegalStateException("cannot start");
                    }
                }));
        PluginId stable = new PluginId("stable");
        runtime.apply(loader.parse("plugins: [{id: stable, type: counter}]"));

        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> runtime.apply(loader.parse("plugins: [{id: replacement, type: broken}]")));

        assertEquals("plugin replacement failed to start", failure.getMessage());
        assertTrue(runtime.fibers().containsKey(stable));
        assertEquals(0, closed.get());

        runtime.close();
        assertEquals(1, closed.get());
    }

    @Test
    void unknownFactoryReportsPluginId() {
        ConfigurationRuntime runtime = new ConfigurationRuntime(Map.of());
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> runtime.apply(loader.parse("plugins: [{id: missing, type: absent}]")));
        assertEquals("plugin missing uses unknown type: absent", failure.getMessage());
    }

    private static Plugin simplePlugin(String name, AtomicInteger closed) {
        return new Plugin() {
            @Override public String name() { return name; }
            @Override public List<ServiceKey<?>> requires() { return List.of(); }
            @Override public Effect apply(io.harnessengineering.core.Context context) {
                return closed::incrementAndGet;
            }
        };
    }
}
