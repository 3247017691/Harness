# Architecture

HarnessEngineering is a Java 21 agent runtime whose behavior follows DeepSeek Harness: contribution-through-registration, reversible side effects, session-forwarded state, and an agent loop driven by a single-owner virtual thread. This page describes the shipped source (adapted from [PLAN.md](../PLAN.md) and [GUIDE.md](../GUIDE.md)).

## Modules

```
harness-engineering/        multi-module parent (io.harnessengineering:0.1.0-SNAPSHOT)
├── harness-core/           framework-free runtime; no Spring/Web dependency
│   └── io.harnessengineering/
│       ├── core/           ServiceKey, Context, Plugin, Fiber, PluginManager, Effect, EventBus, CancellationToken
│       ├── session/        SessionId, SessionEvent (seq/time/type/data), Message, projection,
│       │                   SessionStore (InMemory + Jsonl), EventLogSession, event validator
│       ├── llm/            LlmProvider, LlmRequest, StreamChunk, LlmSessionWriter
│       ├── tools/          Tool, ToolDefinition, ToolCall, ToolResult, ToolRegistry, ToolPipeline, RetryPolicy
│       ├── agent/          Agent, AgentState, Inbox (followup/steer/inject)
│       ├── config/         YAML configuration model, loader, plugin factory, nested groups
│       ├── projection/     SessionProjection: context pressure / breakdown / token usage / request ledger
│       ├── http/           HarnessHttpServer: framework-free read-only HTTP/SSE over JDK HttpServer
│       └── cli/            HarnessCli: append / replay / list
└── harness-spring-app/     Spring Boot assembly (Tomcat + Spring MVC + SseEmitter)
    └── io.harnessengineering.app/
        ├── HarnessApplication, HarnessProperties (harness.session-dir)
        ├── HarnessRuntimeConfiguration → SessionStore bean
        ├── SessionRegistry            → shared in-JVM EventLogSession cache
        ├── AgentHost                  → one Agent per Session, created on first use
        ├── DemoProvider               → streaming demo provider with a tool round-trip
        ├── CurrentTimeTool            → demo tool harness_current_time
        └── SessionApiController       → session list/create/send/projection + SSE + browser page
```

Dependency direction is one-way: `harness-spring-app` depends on `harness-core`; the core never depends on Spring or the web layer.

## Core mechanisms

### Contribution is an effect

Every contribution — a service, an event listener, a tool, a plugin — is registered through an API that returns a disposer (`Effect`). Closing a plugin fiber runs its disposers in reverse order, so teardown is deterministic and idempotent. `ServiceRegistry` keeps contributions keyed by `ServiceKey<T>` so dependency resolution is type-safe.

### Plugin and Fiber lifecycle

| State | Meaning |
|---|---|
| `PENDING` | dependencies not satisfied; `apply` not run |
| `LOADING` | dependencies satisfied; running `apply` once |
| `ACTIVE` | successfully applied |
| `UNLOADING` | a dependency disappeared; running disposers reverse-order, then back to `PENDING` |
| `FAILED` | `apply` threw; original exception retained |
| `DISPOSED` | explicitly closed; never reactivable |

`PluginManager` subscribes to registry changes and refreshes each fiber independently — a service swap unloads and reloads only the fibers that depend on it.

### Event bus

`InMemoryEventBus` provides five semantics: `emit` (fire-and-forget), `parallel`, `serial`, `bail` (stop at first non-null), and `waterfall` (listeners wrap the remaining chain via `next()`; not calling `next()` short-circuits).

### Session event log

A `Session` is an append-only event log. `EventLogSession.append` validates, persists, then notifies listeners — a persistence failure never publishes a success. `SessionEvent` is an immutable record `(sequence, time, type, data)` with monotonically increasing sequence. `deriveMessages()` projects the model-visible conversation from committed events; the JSONL store appends atomically (temp file + atomic move).

### Model-visible ⟺ logged

Anything that reaches a model request must be reconstructable from the session log. The agent loop therefore writes `user/message` and `assistant/message` events, and — since a tool result must be visible to the follow-up step — the loop folds executed `ToolResult` values back into the next step's input as `Message("tool", …)`, keeping every model-visible input derived from logged events.

### Agent loop

One `Agent` owns one virtual thread and an `Inbox`:

```
turn/start → claim followup → step/start → LlmSessionWriter.stream
  → assistant chunks / assistant message → tool calls?
  → ToolPipeline.executeParallel (tool/call + tool/result events)
  → tool results folded into next-step input → step/end → turn/end
```

`followup` opens a new turn; `steer`/`inject` add input to the current turn's next step. Cancellation is cooperative via `CancellationToken`; `close()` awaits the thread's termination latch.

## Projections

`io.harnessengineering.projection.SessionProjection` folds a session event snapshot into framework-free records:

- `ContextPressure(contextWindow, projectedTokens, pressureTokens, percent)` — occupancy.
- `ContextBreakdown(systemTokens, toolsTokens, messageTokens)` — heuristic composition.
- `TokenUsage(uncachedInputTokens, cacheReadTokens, cacheWriteTokens, outputTokens)` — cumulative usage.
- `Result.requests()` — per-assistant-message ledger (`turn/step`, provider/model, input/output/reasoning, time).

All token figures are **reference estimates** derived from event text via `TokenEstimator` (chars-per-token heuristic); no provider tokenizer runs in the core.

## Web surface

The Spring adapter serves the Session Workbench (see `harness-core/src/main/resources/web/index.html`):

- `GET /sessions` — session summaries (id, event count, last event time).
- `POST /sessions` — create a session, returns `{id}`.
- `GET /sessions/{id}` — committed events.
- `GET /sessions/{id}/messages` — derived model messages.
- `GET /sessions/{id}/projection` — projection records for the context meter and popup.
- `POST /sessions/{id}/messages` — `{content}`; hands the message to the session's Agent (202), the turn streams back over SSE.
- `GET /sessions/{id}/stream` — SSE: replays, then follows live events.
- `GET /` — the browser client.

`AgentHost` lazily creates and owns one `Agent` per session with the demo provider plus the `harness_current_time` tool, so any session can be chatted with. The same read-only API (minus send) exists in `harness-core` via `HarnessHttpServer` for framework-free use.

## See also

- [glossary.md](glossary.md) — terms used across the codebase.
- [development.md](development.md) — build, run, and contribution guidance.
- [testing.md](testing.md) — test strategy and coverage expectations.
- [cookbook/](cookbook/) — how-to guides (adding a tool, an LLM adapter, a web surface).