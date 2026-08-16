package io.harnessengineering.app;

import io.harnessengineering.agent.Agent;
import io.harnessengineering.llm.LlmProvider;
import io.harnessengineering.llm.LlmRequest;
import io.harnessengineering.llm.StreamChunk;
import io.harnessengineering.session.Message;
import java.util.stream.Stream;

/** Demo provider that echoes the latest user message so the app works without a real model. */
public final class EchoProvider implements LlmProvider {
    @Override
    public String providerId() {
        return "echo";
    }

    @Override
    public Stream<StreamChunk> stream(LlmRequest request) {
        Message last = request.messages().isEmpty() ? new Message("user", "") : request.messages().getLast();
        return Stream.of(StreamChunk.text("Echo: " + last.content()));
    }
}
