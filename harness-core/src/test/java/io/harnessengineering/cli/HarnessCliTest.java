package io.harnessengineering.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HarnessCliTest {
    @TempDir
    Path storeDirectory;

    @Test
    void appendAndReplayPersistedSessionEvents() {
        Invocation user = invoke("append", storeDirectory.toString(), "demo", "user", "hello");
        Invocation assistant = invoke("append", storeDirectory.toString(), "demo", "assistant", "hi");
        Invocation replay = invoke("replay", storeDirectory.toString(), "demo");

        assertEquals(0, user.exitCode());
        assertEquals("appended 1 to demo", user.output().trim());
        assertEquals(0, assistant.exitCode());
        assertEquals(0, replay.exitCode());
        assertEquals(2, replay.output().lines().count());
        assertTrue(replay.output().contains("user/message"));
        assertTrue(replay.output().contains("assistant/message"));
    }

    @Test
    void listsPersistedSessionsInStoreOrder() {
        invoke("append", storeDirectory.toString(), "beta", "user", "b");
        invoke("append", storeDirectory.toString(), "alpha", "user", "a");

        Invocation listed = invoke("list", storeDirectory.toString());

        assertEquals(0, listed.exitCode());
        assertEquals(java.util.List.of("alpha", "beta"),
                java.util.Arrays.stream(listed.output().split("\r?\n"))
                        .map(String::trim).filter(line -> !line.isEmpty()).toList());
    }

    @Test
    void invalidCommandsReturnUsageErrors() {
        Invocation unknown = invoke("unknown");
        Invocation malformed = invoke("append", "only", "three", "arguments");
        Invocation invalidRole = invoke("append", storeDirectory.toString(), "demo", "system", "hello");

        assertEquals(2, unknown.exitCode());
        assertTrue(unknown.error().contains("Unknown command"));
        assertEquals(2, malformed.exitCode());
        assertEquals(2, invalidRole.exitCode());
        assertTrue(invalidRole.error().contains("role must be user or assistant"));
    }

    private static Invocation invoke(String... arguments) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int exitCode = HarnessCli.execute(arguments, new PrintStream(output), new PrintStream(error));
        return new Invocation(exitCode, output.toString(), error.toString());
    }

    private record Invocation(int exitCode, String output, String error) { }
}
