# Testing

How HarnessEngineering is tested, and what a change must prove.

## Layers

- **`harness-core` unit tests** (JUnit 5) cover each mechanism in isolation: core (PluginManager, EventBus), config, session, llm, tools, agent, cli, http, projection. They run framework-free and fast.
- **`harness-spring-app` integration tests** boot a real Tomcat on a random port (`--server.port=0`) with a temp session dir, then drive the HTTP surface (events, messages, projection, create/send, SSE).

## Key behaviors pinned by tests

- **Lifecycle**: missing dependency stays `PENDING`; providing a service activates the fiber; replacing a service runs disposers once; double-close is idempotent; failed plugins retain their error; closing the manager removes listeners.
- **EventBus**: `emit` notifies all; `waterfall` chains `next()`; not calling `next()` intercepts; listener exceptions behave deterministically.
- **Session**: sequence strictly increases; events are immutable snapshots; replay equals live derivation; invalid envelopes are rejected; a persistence failure never publishes. `list()` enumerates persisted ids in name order (JSONL) and known keys (in-memory).
- **LLM**: chunks and the assembled assistant message are recorded; tool-call chunks are extracted in stream order; the assistant message carries provider/model.
- **Agent**: one followup → one turn; tool calls produce a follow-up step; the executed tool result is fed back as a model-visible `tool` message; cancellation prevents dispatch; `close()` waits for the current task.
- **Tools**: parallel dispatch records call events first and results in request order; retry converges; pre-cancelled tokens never dispatch.
- **Projection**: empty session yields a baseline; message/tool traffic folds into breakdown, usage, and the request ledger; cache-hit percent is null without billed input.
- **HTTP/SSE**: the browser page is served; replay then live events stream; invalid ids and unknown routes return errors.
- **App assembly**: Tomcat boots and serves session state; sessions can be listed, created, and sent to (the demo agent runs a `tool/call` → `tool/result` round-trip); projection is served; SSE delivers live events.

## Conventions

- **Tests describe behavior, not implementation.** Change obsolete behavior together with its tests, and say why in the change.
- **No `Thread.sleep` for synchronization.** Use `CountDownLatch`, `CompletableFuture`, `AtomicInteger`, or short poll loops with a bounded attempt count (e.g. the async agent-turn poll in `HarnessApplicationTest`).
- **Deterministic stream tests** stub `LlmProvider` with finite streams; cancellation tests hold the stream open with a latch, cancel, then release.
- **Test isolation**: Spring tests pass `--harness.session-dir=<temp>` and `--server.port=0` as command-line args (highest precedence) so they never touch the real `.sessions/`.
- **Fixtures** must replay on any host: build events through the public API, never fabricate stored files unless testing the store itself.

## Running

```powershell
mvn "-Dmaven.repo.local=.m2" test
```

Run just one module: `mvn "-Dmaven.repo.local=.m2" -pl harness-core test` (after `install` so the parent POM is resolvable) or the full reactor. Before every push: `mvn test` green and `git diff --check` clean.