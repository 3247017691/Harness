package io.harnessengineering.llm;

import java.util.Objects;

/** One incremental model output chunk. */
public record StreamChunk(String text, String toolCallId, String toolName, String toolArguments, boolean done) {
    public StreamChunk {
        Objects.requireNonNull(text, "text");
        if (done && toolCallId != null && (toolName == null || toolArguments == null)) {
            throw new IllegalArgumentException("completed tool chunk requires name and arguments");
        }
    }

    public static StreamChunk text(String text) {
        return new StreamChunk(text, null, null, null, false);
    }

    public static StreamChunk completed() {
        return new StreamChunk("", null, null, null, true);
    }

    public static StreamChunk toolCall(String id, String name, String arguments) {
        return new StreamChunk("", Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(name, "name"), Objects.requireNonNull(arguments, "arguments"), true);
    }
}
