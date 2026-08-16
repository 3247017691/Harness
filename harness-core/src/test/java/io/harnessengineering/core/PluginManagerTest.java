package io.harnessengineering.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PluginManagerTest {
    private static final ServiceKey<String> GREETING = new ServiceKey<>("greeting", String.class);

    @Test
    void pluginWaitsForDependenciesThenReloadsWhenServiceIsReplaced() {
        InMemoryServiceRegistry services = new InMemoryServiceRegistry();
        PluginManager manager = new PluginManager(new Context(services, new InMemoryEventBus()));
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger cleanups = new AtomicInteger();

        Fiber fiber = manager.mount(plugin("dependent", List.of(GREETING), loads, cleanups));
        assertEquals(FiberState.PENDING, fiber.state());
        assertEquals(0, loads.get());

        Effect first = services.register(GREETING, "hello");
        assertEquals(FiberState.ACTIVE, fiber.state());
        assertEquals(1, loads.get());

        first.close();
        assertEquals(FiberState.PENDING, fiber.state());
        assertEquals(1, cleanups.get());

        Effect second = services.register(GREETING, "hola");
        assertEquals(FiberState.ACTIVE, fiber.state());
        assertEquals(2, loads.get());

        second.close();
        manager.close();
        assertEquals(2, cleanups.get());
    }

    @Test
    void effectsCloseInReverseOrderAndOnlyOnce() {
        InMemoryServiceRegistry services = new InMemoryServiceRegistry();
        PluginManager manager = new PluginManager(new Context(services, new InMemoryEventBus()));
        StringBuilder cleaned = new StringBuilder();

        Fiber fiber = manager.mount(new Plugin() {
            @Override public String name() { return "effect-order"; }
            @Override public List<ServiceKey<?>> requires() { return List.of(); }
            @Override public Effect apply(Context context) { return () -> cleaned.append('A'); }
        });
        fiber.addEffect(() -> cleaned.append('B'));

        fiber.close();
        fiber.close();
        assertEquals("BA", cleaned.toString());
        assertEquals(FiberState.DISPOSED, fiber.state());
    }

    @Test
    void failedPluginKeepsOriginalFailure() {
        PluginManager manager = new PluginManager(new Context(new InMemoryServiceRegistry(), new InMemoryEventBus()));
        IllegalStateException expected = new IllegalStateException("broken plugin");
        Fiber fiber = manager.mount(new Plugin() {
            @Override public String name() { return "failing"; }
            @Override public List<ServiceKey<?>> requires() { return List.of(); }
            @Override public Effect apply(Context context) { throw expected; }
        });

        assertEquals(FiberState.FAILED, fiber.state());
        assertInstanceOf(IllegalStateException.class, fiber.failure().orElseThrow());
        assertEquals("broken plugin", fiber.failure().orElseThrow().getMessage());
    }

    @Test
    void managerCloseRemovesPluginEventListeners() {
        InMemoryEventBus events = new InMemoryEventBus();
        PluginManager manager = new PluginManager(new Context(new InMemoryServiceRegistry(), events));
        AtomicInteger calls = new AtomicInteger();
        manager.mount(new Plugin() {
            @Override public String name() { return "listener"; }
            @Override public List<ServiceKey<?>> requires() { return List.of(); }
            @Override public Effect apply(Context context) { return context.events().on("ping", ignored -> calls.incrementAndGet()); }
        });

        events.emit("ping", "before");
        manager.close();
        events.emit("ping", "after");
        assertEquals(1, calls.get());
        assertTrue(true);
    }

    private static Plugin plugin(String name, List<ServiceKey<?>> requires, AtomicInteger loads, AtomicInteger cleanups) {
        return new Plugin() {
            @Override public String name() { return name; }
            @Override public List<ServiceKey<?>> requires() { return requires; }
            @Override public Effect apply(Context context) {
                loads.incrementAndGet();
                return cleanups::incrementAndGet;
            }
        };
    }
}
