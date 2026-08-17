# Cookbook: adding an LLM adapter

How to connect a real (or fake) model to the harness. The core is provider-neutral: an adapter is one class implementing `LlmProvider`.

## 1. Implement the provider

```java
public final class MyProvider implements LlmProvider {
    @Override
    public String providerId() {
        return "my-provider";
    }

    @Override
    public Stream<StreamChunk> stream(LlmRequest request) {
        // request: provider id, model, List<Message>, List<ToolDefinition>
        // Stream chunks in order:
        //   StreamChunk.text("partial")           -> assistant/chunk
        //   StreamChunk.toolCall(id, name, args)  -> a tool call (finishes the stream)
        //   StreamChunk.completed()               -> explicit end marker
        return Stream.of(StreamChunk.text("answer"));
    }
}
```

Rules:

- `stream` returns a **finite** stream; the writer materializes it with `chunks.toList()`.
- A `toolCall` chunk is itself the end of the response — the agent loop executes the collected calls afterwards.
- Provider streams run on the agent's virtual thread and are polled by `LlmSessionWriter`; honor `CancellationToken` only via the pipeline (the current seam has no token in `stream`).

## 2. Record provider identity

`LlmSessionWriter` stamps the assembled `assistant/message` event with `providerId` and `model` from the request, and the projection request ledger reads them. Anything else the UI should show must be added to the event data in the same change.

## 3. Wire it

In the Spring assembly, use your provider where agents are created:

```java
Agent agent = new Agent(session, new MyProvider(), "my-model", new ToolPipeline(tools));
```

To keep every session chat-able, replace the `DemoProvider` in `AgentHost` or construct your own host.

## 4. Test it

- **Writer test** (`LlmSessionWriterTest` style): assert `assistant/chunk` events per chunk, the assembled `assistant/message` with provider/model, and extraction of tool calls in stream order.
- **Agent round-trip test** (`AgentTest` style): a provider emitting a tool call then a final text produces `tool/call` → `tool/result` → follow-up step; the result reaches the model as a `tool` message.
- **Projection test**: with a stubbed session, the request ledger records `providerId`/`model`/input/output.

## Notes

- Streaming through the browser: the Session Workbench renders `assistant/chunk` events live, so chunk-granular providers give the streaming UX with no web change.
- Keep the core free of HTTP/SDK dependencies: put transport in the adapter; the core only sees `LlmRequest`/`StreamChunk`.