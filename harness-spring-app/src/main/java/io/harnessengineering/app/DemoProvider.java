package io.harnessengineering.app;

import io.harnessengineering.llm.LlmProvider;
import io.harnessengineering.llm.LlmRequest;
import io.harnessengineering.llm.StreamChunk;
import io.harnessengineering.session.Message;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Demo provider that demonstrates the tool round-trip without a real model: the
 * first model step announces and calls {@code harness_current_time}; the follow-up
 * step, whose request carries the tool result as a model-visible tool message,
 * reports the value it received.
 */
public final class DemoProvider implements LlmProvider {
    public static final String TIME_TOOL = "harness_current_time";
    public static final String MODEL = "demo-model";

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public String providerId() {
        return "demo";
    }

    @Override
    public Stream<StreamChunk> stream(LlmRequest request) {
        List<Message> messages = request.messages();
        Message last = messages.isEmpty() ? null : messages.getLast();
        if (last != null && last.role().equals("tool")) {
            return Stream.of(StreamChunk.text("Current time: " + innerToolValue(last.content())));
        }
        boolean hasTimeTool = request.tools().stream().anyMatch(tool -> tool.name().equals(TIME_TOOL));
        if (hasTimeTool) {
            return Stream.of(
                    StreamChunk.text("I'll check the clock."),
                    StreamChunk.toolCall("demo-call-" + calls.incrementAndGet(), TIME_TOOL, "{}"));
        }
        return Stream.of(StreamChunk.text("Without a tool, my answer is: " + LocalTime.now().withNano(0)));
    }

    /** Strips the agent's "{call id: content}" envelope to the tool content. */
    static String innerToolValue(String content) {
        String value = content.strip();
        if (value.startsWith("{call ") && value.endsWith("}")) {
            value = value.substring(6, value.length() - 1);
            int colon = value.indexOf(':');
            if (colon >= 0) {
                value = value.substring(colon + 1).strip();
            }
        }
        return value;
    }
}