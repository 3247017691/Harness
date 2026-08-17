package io.harnessengineering.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlSessionStoreTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path directory;

    @Test
    void persistsEventsAndReplaysThemFromAnotherStoreInstance() throws IOException {
        SessionId id = new SessionId("persisted-session");
        EventLogSession original = new EventLogSession(id, new JsonlSessionStore(directory));
        original.append(SessionEventTypes.USER_MESSAGE, message("user", "question"));
        original.append(SessionEventTypes.ASSISTANT_MESSAGE, message("assistant", "answer"));

        EventLogSession replayed = new EventLogSession(id, new JsonlSessionStore(directory));
        assertEquals(original.events(), replayed.events());
        assertEquals(original.deriveMessages(), replayed.deriveMessages());
        assertEquals(2, Files.readAllLines(directory.resolve("persisted-session.jsonl")).size());
    }

    @Test
    void rejectsNonSequentialEventsWithoutChangingExistingLog() {
        JsonlSessionStore store = new JsonlSessionStore(directory);
        SessionId id = new SessionId("sequence-session");
        EventLogSession session = new EventLogSession(id, store);
        session.append(SessionEventTypes.USER_MESSAGE, message("user", "first"));

        assertThrows(IllegalArgumentException.class, () -> store.append(id,
                new SessionEvent(3, java.time.Instant.now(), SessionEventTypes.USER_MESSAGE, message("user", "skip"))));
        assertEquals(1, store.load(id).size());
    }

    @Test
    void rejectsCorruptJsonlLog() throws IOException {
        Files.writeString(directory.resolve("broken.jsonl"), "not-json\n", StandardCharsets.UTF_8);
        JsonlSessionStore store = new JsonlSessionStore(directory);

        assertThrows(SessionPersistenceException.class, () -> store.load(new SessionId("broken")));
    }

    @Test
    void listsPersistedSessionsInNameOrder() {
        JsonlSessionStore store = new JsonlSessionStore(directory);
        new EventLogSession(new SessionId("zeta"), store).append(
                SessionEventTypes.USER_MESSAGE, message("user", "z"));
        new EventLogSession(new SessionId("alpha"), store).append(
                SessionEventTypes.USER_MESSAGE, message("user", "a"));
        new EventLogSession(new SessionId("session-with-dots.v2"), store).append(
                SessionEventTypes.USER_MESSAGE, message("user", "dots"));

        assertEquals(List.of(new SessionId("alpha"), new SessionId("session-with-dots.v2"),
                        new SessionId("zeta")),
                store.list());
    }

    @Test
    void listsNothingForMissingDirectory() {
        Path missing = directory.resolve("does-not-exist");
        assertEquals(List.of(), new JsonlSessionStore(missing).list());
    }

    @Test
    void sessionDoesNotAdvanceWhenStoreAppendFails() {
        Path blockingFile = directory.resolve("not-a-directory");
        try {
            Files.writeString(blockingFile, "file", StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        EventLogSession session = new EventLogSession(new SessionId("blocked"), new JsonlSessionStore(blockingFile));

        assertThrows(SessionPersistenceException.class,
                () -> session.append(SessionEventTypes.USER_MESSAGE, message("user", "cannot persist")));
        assertFalse(session.events().iterator().hasNext());
    }

    private ObjectNode message(String role, String content) {
        return mapper.createObjectNode().put("role", role).put("content", content);
    }
}
