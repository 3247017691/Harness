package io.harnessengineering.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.harnessengineering.session.EventLogSession;
import io.harnessengineering.session.InMemorySessionStore;
import io.harnessengineering.session.SessionEventTypes;
import io.harnessengineering.session.SessionId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionProjectionTest {
    private final SessionProjection projection = new SessionProjection(1_000L, 100L);

    @Test
    void emptySessionYieldsBaselinePressureAndZeroUsage() {
        EventLogSession session = session("empty");

        SessionProjection.Result result = projection.project(session.events());

        assertEquals(100, result.breakdown().systemTokens());
        assertEquals(0, result.breakdown().toolsTokens());
        assertEquals(0, result.breakdown().messageTokens());
        assertEquals(10, result.pressure().percent());
        assertEquals(100, result.pressure().usedTokens());
        assertEquals(0, result.usage().inputTokens());
        assertEquals(0, result.usage().outputTokens());
        assertNull(result.usage().cacheHitPercent());
        assertEquals(List.of(), result.requests());
    }

    @Test
    void foldsMessagesAndToolTrafficIntoBreakdownAndUsage() {
        EventLogSession session = session("usage");
        session.append(SessionEventTypes.TURN_START, marker("turn", 1));
        session.append(SessionEventTypes.STEP_START, marker("step", 1));
        session.append(SessionEventTypes.USER_MESSAGE, message("user", "hello"));
        session.append(SessionEventTypes.ASSISTANT_MESSAGE,
                message("assistant", "ok").put("providerId", "demo").put("model", "demo-model"));
        session.append(SessionEventTypes.TOOL_CALL, call("call-1", "weather", "{\"city\":\"Paris\"}"));
        session.append(SessionEventTypes.TOOL_RESULT,
                result("call-1", "sunny"));

        SessionProjection.Result projected = projection.project(session.events());

        assertEquals(3, projected.breakdown().messageTokens()); // hello(2) + ok(1)
        assertEquals(10, projected.breakdown().toolsTokens());  // args(4)+envelope(4) + result(2)
        assertEquals(1, projected.usage().outputTokens());
        assertEquals(2, projected.usage().inputTokens());       // prompt side before the reply
        assertEquals(1, projected.requests().size());
        assertEquals(1, projected.requests().getFirst().turn());
        assertEquals(1, projected.requests().getFirst().step());
        assertEquals("demo", projected.requests().getFirst().providerId());
        assertEquals(2, projected.requests().getFirst().input());
        assertEquals(1, projected.requests().getFirst().output());
    }

    @Test
    void cacheHitPercentIsZeroWhenInputIsBilledWithoutCacheReads() {
        EventLogSession session = session("cache");
        session.append(SessionEventTypes.USER_MESSAGE, message("user", "hi"));
        session.append(SessionEventTypes.ASSISTANT_MESSAGE, message("assistant", "yo"));

        SessionProjection.Result projected = projection.project(session.events());
        assertEquals(Integer.valueOf(0), projected.usage().cacheHitPercent());
    }

    private static EventLogSession session(String id) {
        return new EventLogSession(new SessionId(id), new InMemorySessionStore());
    }

    private static ObjectNode marker(String field, Object value) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        if (value instanceof Integer integer) {
            node.put(field, integer);
        } else {
            node.put(field, String.valueOf(value));
        }
        return node;
    }

    private static ObjectNode message(String role, String content) {
        return JsonNodeFactory.instance.objectNode().put("role", role).put("content", content);
    }

    private static ObjectNode call(String id, String name, String arguments) {
        return JsonNodeFactory.instance.objectNode()
                .put("id", id).put("name", name).put("arguments", arguments);
    }

    private static ObjectNode result(String callId, String content) {
        return JsonNodeFactory.instance.objectNode()
                .put("toolCallId", callId).put("success", true).put("content", content);
    }
}