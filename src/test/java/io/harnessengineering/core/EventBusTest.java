package io.harnessengineering.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EventBusTest {
    @Test
    void emitAndSerialPreserveRegistrationOrder() {
        InMemoryEventBus bus = new InMemoryEventBus();
        List<String> calls = new ArrayList<>();
        bus.on("event", value -> calls.add("first:" + value));
        bus.on("event", value -> calls.add("second:" + value));

        bus.emit("event", "x");
        assertEquals(List.of("first:x", "second:x"), calls);

        bus.onMapped("mapped", value -> "one:" + value);
        bus.onMapped("mapped", value -> "two:" + value);
        assertEquals(List.of("one:x", "two:x"), bus.serial("mapped", "x"));
    }

    @Test
    void bailStopsAtFirstNonNullResult() {
        InMemoryEventBus bus = new InMemoryEventBus();
        bus.onMapped("lookup", ignored -> null);
        bus.onMapped("lookup", ignored -> "found");
        bus.onMapped("lookup", ignored -> "unreachable");

        Optional<String> result = bus.bail("lookup", "key");
        assertEquals(Optional.of("found"), result);
    }

    @Test
    void waterfallCanTransformOrIntercept() {
        InMemoryEventBus bus = new InMemoryEventBus();
        bus.onWaterfall("transform", (value, next) -> next.apply(value + "-one"));
        bus.onWaterfall("transform", (value, next) -> next.apply(value + "-two"));
        assertEquals("start-one-two-terminal", bus.waterfall("transform", "start", value -> value + "-terminal"));

        InMemoryEventBus intercepted = new InMemoryEventBus();
        intercepted.onWaterfall("transform", (value, next) -> "blocked");
        assertEquals("blocked", intercepted.waterfall("transform", "start", value -> "terminal"));
    }

    @Test
    void subscriptionIsReversible() {
        InMemoryEventBus bus = new InMemoryEventBus();
        List<String> calls = new ArrayList<>();
        Effect subscription = bus.<String>on("event", calls::add);
        subscription.close();
        subscription.close();
        bus.emit("event", "ignored");
        assertTrue(calls.isEmpty());
        assertFalse(calls.contains("ignored"));
    }
}
