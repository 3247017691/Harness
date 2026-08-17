# Cookbook: adding a Tool

How to add a new capability the agent loop can call. A tool is a named, schema-advertised executable that the demo provider's tool round-trip can exercise.

## 1. Implement the tool

```java
public final class MyTool implements Tool {
    @Override
    public ToolDefinition definition() {
        ObjectNode parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("type", "object");
        parameters.putObject("properties");   // declare args here
        parameters.putArray("required");
        return new ToolDefinition("my_tool", "one-sentence description, no arguments.", parameters);
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext context) {
        // Parse call.arguments() with the object mapper, run, then return:
        return ToolResult.success(call.id(), "result text");
        // Failures: ToolResult.failure(call.id(), "error_code", "explanation");
    }
}
```

`ToolDefinition` requires a non-blank name, a description, and a deep-copied parameters node. `ToolResult` requires an error code on failure and forbids one on success.

## 2. Register it

The registry owning the tool decides its lifetime — registration returns an `Effect`:

```java
ToolRegistry registry = new ToolRegistry();
Effect registration = registry.register(new MyTool());
// On teardown: registration.close();
```

Schemas advertised to the model come from `ToolPipeline.definitions()` (the registry snapshot). In the Spring assembly, register tools where the agent is created (`AgentHost` builds the demo roster per session).

## 3. Exercise it deterministically in a test

Mirror `AgentTest.toolCallCreatesFollowupStepAndExecutesTool`: stub a provider that emits `StreamChunk.toolCall("call-1", "my_tool", "{}")` on the first request and text on the second; assert `tool/call` and `tool/result` events and that the result reached the follow-up request as a `tool` message.

## 4. When the UI should show it

`tool/call` and `tool/result` events render as a tool card in the Session Workbench automatically (`name`, `arguments`, success/content). No web change is needed for a new tool.

## Notes

- **Put policy in the pipeline, not the tool**: reorder, retry, and cancellation live in `ToolPipeline`/middleware; a tool executes one call.
- **Deterministic results**: a tool that reads the clock or filesystem should take an injectable `Clock`/path for tests.
- **Model-visible input**: arguments and results are logged as events; anything the model needs beyond that must also be an event or a folded `tool` message.