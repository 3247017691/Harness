package io.harnessengineering.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventLogSessionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void appendsSequentialEventsAndProjectsMessages() {
        EventLogSession session = new EventLogSession(new SessionId("session-1"), new InMemorySessionStore());

        SessionEvent user = session.append(SessionEventTypes.USER_MESSAGE, message("user", "hello"));
        SessionEvent assistant = session.append(SessionEventTypes.ASSISTANT_MESSAGE, message("assistant", "hi"));
        session.append(SessionEventTypes.TOOL_RESULT, mapper.createObjectNode().put("toolCallId", "call-1"));

        assertEquals(1, user.sequence());
        assertEquals(2, assistant.sequence());
        assertEquals(List.of(new Message("user", "hello"), new Message("assistant", "hi")), session.deriveMessages());
    }

    @Test
    void snapshotsInputAndReturnedEvents() {
        EventLogSession session = new EventLogSession(new SessionId("session-2"), new InMemorySessionStore());
        ObjectNode input = message("user", "original");
        SessionEvent event = session.append(SessionEventTypes.USER_MESSAGE, input);

        input.put("content", "mutated input");
        ((ObjectNode) event.data()).put("content", "mutated event");

        assertEquals("original", session.events().getFirst().data().path("content").asText());
        assertEquals("original", session.deriveMessages().getFirst().content());
    }

    @Test
    void replayProducesSameMessages() {
        InMemorySessionStore store = new InMemorySessionStore();
        SessionId id = new SessionId("session-3");
        EventLogSession original = new EventLogSession(id, store);
        original.append(SessionEventTypes.USER_MESSAGE, message("user", "question"));
        original.append(SessionEventTypes.ASSISTANT_MESSAGE, message("assistant", "answer"));

        EventLogSession replayed = new EventLogSession(id, store);
        assertEquals(original.events(), replayed.events());
        assertEquals(original.deriveMessages(), replayed.deriveMessages());
    }

    @Test
    void rejectsInvalidEventsBeforePersistence() {
        InMemorySessionStore store = new InMemorySessionStore();
        EventLogSession session = new EventLogSession(new SessionId("session-4"), store);

        assertThrows(IllegalArgumentException.class,
                () -> session.append(SessionEventTypes.USER_MESSAGE, mapper.createObjectNode().put("role", "assistant")));
        assertEquals(List.of(), store.load(session.id()));
    }

    @Test
    void persistenceFailureDoesNotPublishOrAdvanceSession() {
        SessionStore failingStore = new SessionStore() {
            @Override public void append(SessionId id, SessionEvent event) { throw new IllegalStateException("disk unavailable"); }
            @Override public List<SessionEvent> load(SessionId id) { return List.of(); }
            @Override public List<SessionId> list() { return List.of(); }
        };
        EventLogSession session = new EventLogSession(new SessionId("session-5"), failingStore);
        List<SessionEvent> received = new ArrayList<>();
        session.onEvent(received::add);

        assertThrows(IllegalStateException.class,
                () -> session.append(SessionEventTypes.USER_MESSAGE, message("user", "not saved")));
        assertFalse(session.events().iterator().hasNext());
        assertFalse(received.iterator().hasNext());
    }

    @Test
    void publishesOnlyCommittedEvents() {
        EventLogSession session = new EventLogSession(new SessionId("session-6"), new InMemorySessionStore());
        List<SessionEvent> received = new ArrayList<>();
        session.onEvent(received::add);

        session.append(SessionEventTypes.USER_MESSAGE, message("user", "committed"));
        assertEquals(1, received.size());
        assertEquals(1, received.getFirst().sequence());
    }

    private ObjectNode message(String role, String content) {
        return mapper.createObjectNode().put("role", role).put("content", content);
    }
}
