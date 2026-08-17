package io.harnessengineering.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** File-backed JSONL session event store with atomic append replacement. */
public final class JsonlSessionStore implements SessionStore {
    private final Path directory;
    private final ObjectMapper mapper;
    private final Object lock = new Object();

    /**
     * Creates a JSONL store rooted at a directory.
     *
     * @param directory storage directory
     */
    public JsonlSessionStore(Path directory) {
        this(directory, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    JsonlSessionStore(Path directory, ObjectMapper mapper) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public void append(SessionId sessionId, SessionEvent event) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(event, "event");
        synchronized (lock) {
            try {
                Files.createDirectories(directory);
                List<String> lines = readLines(sessionId);
                long expected = lines.size() + 1L;
                if (event.sequence() != expected) {
                    throw new IllegalArgumentException("expected sequence " + expected + " but got " + event.sequence());
                }
                lines.add(mapper.writeValueAsString(event));
                replaceAtomically(fileFor(sessionId), lines);
            } catch (IOException exception) {
                throw new SessionPersistenceException("cannot append session " + sessionId, exception);
            }
        }
    }

    @Override
    public List<SessionEvent> load(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        synchronized (lock) {
            try {
                List<SessionEvent> events = new ArrayList<>();
                List<String> lines = readLines(sessionId);
                for (int index = 0; index < lines.size(); index++) {
                    String line = lines.get(index);
                    try {
                        SessionEvent event = mapper.readValue(line, SessionEvent.class);
                        if (event.sequence() != index + 1L) {
                            throw new SessionPersistenceException("non-sequential event at line " + (index + 1));
                        }
                        events.add(event);
                    } catch (IOException | IllegalArgumentException exception) {
                        throw new SessionPersistenceException(
                                "invalid event at line " + (index + 1) + " for session " + sessionId, exception);
                    }
                }
                return events.stream().map(JsonlSessionStore::copy).toList();
            } catch (IOException exception) {
                throw new SessionPersistenceException("cannot load session " + sessionId, exception);
            }
        }
    }

    @Override
    public List<SessionId> list() {
        synchronized (lock) {
            if (!Files.isDirectory(directory)) {
                return List.of();
            }
            try (var files = Files.list(directory)) {
                return files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                        .map(path -> {
                            String name = path.getFileName().toString();
                            return new SessionId(name.substring(0, name.length() - ".jsonl".length()));
                        })
                        .sorted(java.util.Comparator.comparing(SessionId::value))
                        .toList();
            } catch (IOException exception) {
                throw new SessionPersistenceException("cannot list sessions in " + directory, exception);
            }
        }
    }

    private List<String> readLines(SessionId sessionId) throws IOException {
        Path file = fileFor(sessionId);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    private void replaceAtomically(Path target, List<String> lines) throws IOException {
        Path temporary = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path fileFor(SessionId sessionId) {
        String safeName = sessionId.value().replaceAll("[^a-zA-Z0-9._-]", "_");
        return directory.resolve(safeName + ".jsonl");
    }

    private static SessionEvent copy(SessionEvent event) {
        JsonNode data = event.data().deepCopy();
        return new SessionEvent(event.sequence(), event.time(), event.type(), data);
    }
}
