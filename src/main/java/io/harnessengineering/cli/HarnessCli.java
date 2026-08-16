package io.harnessengineering.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.harnessengineering.session.EventLogSession;
import io.harnessengineering.session.JsonlSessionStore;
import io.harnessengineering.session.SessionEvent;
import io.harnessengineering.session.SessionEventTypes;
import io.harnessengineering.session.SessionId;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Command-line entry point for creating and replaying persisted session logs. */
public final class HarnessCli {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private HarnessCli() { }

    public static void main(String[] arguments) {
        int exitCode = execute(arguments, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Executes a CLI command without terminating the JVM, which makes the command
     * surface directly testable.
     *
     * @param arguments command arguments
     * @param output normal output stream
     * @param error error output stream
     * @return process-compatible exit code
     */
    public static int execute(String[] arguments, PrintStream output, PrintStream error) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(error, "error");
        if (arguments.length == 0 || isHelp(arguments[0])) {
            usage(output);
            return arguments.length == 0 ? 2 : 0;
        }
        try {
            return switch (arguments[0]) {
                case "append" -> append(arguments, output, error);
                case "replay" -> replay(arguments, output, error);
                default -> {
                    error.println("Unknown command: " + arguments[0]);
                    usage(error);
                    yield 2;
                }
            };
        } catch (IllegalArgumentException exception) {
            error.println("Invalid arguments: " + exception.getMessage());
            return 2;
        } catch (RuntimeException exception) {
            error.println("Command failed: " + exception.getMessage());
            return 1;
        }
    }

    private static int append(String[] arguments, PrintStream output, PrintStream error) {
        if (arguments.length != 5) {
            error.println("append requires: <store-dir> <session-id> <user|assistant> <content>");
            return 2;
        }
        String role = arguments[3];
        String eventType = switch (role) {
            case "user" -> SessionEventTypes.USER_MESSAGE;
            case "assistant" -> SessionEventTypes.ASSISTANT_MESSAGE;
            default -> throw new IllegalArgumentException("role must be user or assistant");
        };
        EventLogSession session = session(arguments[1], arguments[2]);
        ObjectNode data = JSON.createObjectNode().put("role", role).put("content", arguments[4]);
        SessionEvent event = session.append(eventType, data);
        output.println("appended " + event.sequence() + " to " + session.id().value());
        return 0;
    }

    private static int replay(String[] arguments, PrintStream output, PrintStream error) {
        if (arguments.length != 3) {
            error.println("replay requires: <store-dir> <session-id>");
            return 2;
        }
        EventLogSession session = session(arguments[1], arguments[2]);
        List<SessionEvent> events = session.events();
        try {
            for (SessionEvent event : events) {
                output.println(JSON.writeValueAsString(event));
            }
            return 0;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("cannot encode session event", exception);
        }
    }

    private static EventLogSession session(String storeDirectory, String sessionId) {
        if (storeDirectory.isBlank()) {
            throw new IllegalArgumentException("store directory must not be blank");
        }
        return new EventLogSession(new SessionId(sessionId), new JsonlSessionStore(Path.of(storeDirectory)));
    }

    private static boolean isHelp(String argument) {
        return argument.equals("help") || argument.equals("--help") || argument.equals("-h");
    }

    private static void usage(PrintStream stream) {
        stream.println("Usage:");
        stream.println("  harness append <store-dir> <session-id> <user|assistant> <content>");
        stream.println("  harness replay <store-dir> <session-id>");
    }
}
