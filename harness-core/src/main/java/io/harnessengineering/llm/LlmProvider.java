package io.harnessengineering.llm;

import java.util.stream.Stream;

/** Provider-neutral source of streamed model output. */
public interface LlmProvider {
    String providerId();

    /**
     * Streams model response chunks in provider order.
     *
     * @param request immutable model request
     * @return finite response stream
     */
    Stream<StreamChunk> stream(LlmRequest request);
}
