# HarnessEngineering

A Java 21 agent harness in the shape of [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness): composable plugins, typed services, reversible effects, an append-only session event log, a provider-neutral LLM seam, parallel tools, and a single-owner agent loop — plus a dark-themed Session Workbench web client.

English | [中文](README.zh.md)

## What it looks like

`java -cp … io.harnessengineering.app.HarnessApplication` boots Tomcat and serves a browser workbench at `http://127.0.0.1:8080/`:

- **Session sidebar** — list, create, and switch sessions.
- **Conversation header** — `Context` popup (occupancy bar, composition, session totals, per-request ledger) and `Session log` (raw events, JSONL export).
- **Live conversation** — user/assistant messages, streaming `assistant/chunk` updates, tool cards, turn/step markers, all over one SSE stream.
- **Composer** — send box plus a context-occupancy ring fed by the projection endpoint.

## Repository layout

```
harness-engineering/
├── harness-core/          framework-free runtime (core, session, llm, tools, agent, config, projection, http, cli)
├── harness-spring-app/    Spring Boot assembly (Tomcat, Spring MVC, SSE; depends on core, never the reverse)
├── docs/                  architecture, glossary, development, testing, cookbook (English + 中文)
├── AGENTS.md              agent/contributor guidance
├── PLAN.md / GUIDE.md     roadmap and implementation guide (中文)
└── pom.xml                multi-module parent
```

## Prerequisites

- JDK 21 or later
- Maven 3.9 or later

## Verify

```powershell
mvn "-Dmaven.repo.local=.m2" test
mvn "-Dmaven.repo.local=.m2" -q package
```

(Use the workspace-local `.m2` repo; PowerShell requires quoting the `-D` argument.)

## Run the Session Workbench

```powershell
mvn "-Dmaven.repo.local=.m2" -q package
$cp = "harness-spring-app\target\harness-spring-app-0.1.0-SNAPSHOT.jar;harness-core\target\harness-core-0.1.0-SNAPSHOT.jar" + ((Get-ChildItem .m2 -Recurse -Filter *.jar | Where-Object { $_.FullName -notmatch '\\slf4j-api\\1\.7' } | ForEach-Object { ";$($_.FullName)" }) -join "")
java -cp $cp io.harnessengineering.app.HarnessApplication
```

Open `http://127.0.0.1:8080/`, hit **新会话**, and send a message — the demo agent answers by calling the `harness_current_time` tool, and the whole turn streams into the conversation.

## HTTP API

```text
GET  /sessions                  session summaries (id, event count, last event time)
POST /sessions                  create a session -> {id}
GET  /sessions/{id}             committed events
GET  /sessions/{id}/messages    derived model messages
GET  /sessions/{id}/projection  context pressure / breakdown / usage / request ledger
POST /sessions/{id}/messages    send {content}; the agent turn streams over SSE
GET  /sessions/{id}/stream      SSE: replay, then follow live events
GET  /                          browser client
```

A framework-free HTTP/SSE variant (read-only) also ships in `harness-core` over the JDK `HttpServer`.

## CLI

```powershell
$cp = "harness-core\target\harness-core-0.1.0-SNAPSHOT.jar" + ((Get-ChildItem .m2 -Recurse -Filter *.jar | ForEach-Object { ";$($_.FullName)" }) -join "")
java -cp $cp io.harnessengineering.cli.HarnessCli list .sessions
java -cp $cp io.harnessengineering.cli.HarnessCli append .sessions demo user "Hello"
java -cp $cp io.harnessengineering.cli.HarnessCli replay .sessions demo
```

## Documentation

- [docs/architecture.md](docs/architecture.md) — modules, core mechanisms, agent loop, projections, web surface.
- [docs/glossary.md](docs/glossary.md) — terms.
- [docs/development.md](docs/development.md) — build/run/contribution rules.
- [docs/testing.md](docs/testing.md) — test strategy.
- [docs/cookbook/](docs/cookbook/) — adding a tool, an LLM adapter, a web surface.

## Scope

The runtime includes a typed service registry with plugin lifecycle fibers; YAML plugin composition; append-only session event logs (in-memory + JSONL) with atomic persistence; provider-neutral LLM streaming and an agent turn loop with parallel, cancellable, retriable tools; session projections (pressure/breakdown/usage/ledger); a CLI; a framework-free HTTP/SSE server; and a Spring Boot assembly serving the Session Workbench.