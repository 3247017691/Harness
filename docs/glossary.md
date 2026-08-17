# Glossary

Terms used across the HarnessEngineering codebase, adapted from DeepSeek Harness vocabulary where semantics match.

## Core

- **Context** — the runtime envelope holding service registry, configuration, and lifecycle; the seam through which plugins contribute and read services.
- **ServiceKey\<T\>** — a typed, name-scoped service identifier; lookups are type-safe, never bare strings.
- **ServiceRegistry** — owns registered services; `provide`/`remove` notify subscribers so dependent fibers refresh.
- **Plugin** — a named contribution unit declaring `requires()` and an `apply(Context)` that registers and returns an `Effect`.
- **Fiber** — the lifecycle shell around one plugin instance (`PENDING → LOADING → ACTIVE → UNLOADING → PENDING`, plus `FAILED`/`DISPOSED` terminal states).
- **PluginManager** — mounts plugins, subscribes to registry changes, and refreshes each fiber independently.
- **Effect** — an idempotent disposer returned by every registration; fibers run disposers in reverse registration order.
- **EventBus** — dispatch with five semantics: `emit`, `parallel`, `serial`, `bail`, `waterfall` (listeners wrap the chain via `next()`).
- **CancellationToken** — cooperative cancellation flag; long operations check it at safe points and abort dispatch before it fires.

## Session

- **SessionId** — a stable identifier for one append-only event log.
- **SessionEvent** — immutable committed entry `(sequence, time, type, data)`; sequence starts at 1 and is strictly increasing.
- **Message** — a projected `(role, content)` record derived from committed events; the model-visible conversation.
- **SessionStore** — durable backend; `append(sessionId, event)` (with sequence check), `load(sessionId)`, `list()`.
- **EventLogSession** — default `Session` impl: validate → persist → notify listeners; replays on construction.
- **Projection** — a framework-free fold over events (`SessionProjection`) producing pressure/breakdown/usage records. All token figures are reference estimates.

## LLM / Tools

- **LlmProvider** — a provider-neutral source of streamed model output; returns a finite `Stream<StreamChunk>`.
- **StreamChunk** — one incremental output: text, a completed marker, or a tool call.
- **LlmSessionWriter** — consumes a provider stream, appends `assistant/chunk` events per chunk, then one `assistant/message` event carrying provider/model.
- **Tool** — an executable capability with a `ToolDefinition` (schema) and `execute(ToolCall, ToolContext)`.
- **ToolRegistry** — owns tools; contributions disappear when their registration effect closes.
- **ToolPipeline** — executes calls (serial or parallel), with middleware, retry, and cancellation-aware dispatch; records `tool/call` and `tool/result` events deterministically.
- **RetryPolicy** — `(maxAttempts, delay)` schedule for failed tool executions; `none()` disables retry.

## Agent

- **Agent** — one virtual thread owning one Session; turns inbox messages into model steps and tool calls.
- **Inbox** — separates next-turn inputs (`followup`) from current-turn step inputs (`steer`/`inject`).
- **Turn / Step** — a turn is one user-requested agent run (`turn/start`…`turn/end`); a step is one model round including optional tool execution (`step/start`…`step/end`).
- **Tool result feeding** — executed `ToolResult`s are folded into the next step's model-visible input as `Message("tool", …)`, keeping every model input derivable from the log.

## Web / App

- **AgentHost** — Spring component lazily creating and owning one `Agent` per session.
- **SseEmitter** — Spring MVC SSE channel: replays the session log on connect, then follows live events.
- **Session Workbench** — the browser client (`index.html`): session sidebar, conversation header (Context / Session log), composer with context meter, and live SSE updates.