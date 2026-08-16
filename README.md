# HarnessEngineering

A Java 21 runtime foundation that implements composable plugins, typed services, reversible effects, lifecycle fibers, and event dispatching.

## Modules

- `harness-core` — framework-free runtime: plugins, services, sessions, LLM, tools, agents, HTTP/SSE, CLI.
- `harness-spring-app` — Spring Boot application assembly over the core (adapter depends on core, never the reverse).

## Prerequisites

- JDK 21 or later
- Maven 3.9 or later

## Verification

```powershell
mvn test
mvn -q package
```

## Spring Boot adapter

Run the assembled application (Tomcat + Spring MVC) with an echo provider and a demo agent:

```powershell
mvn -q package
$cp = "harness-spring-app/target/harness-spring-app-0.1.0-SNAPSHOT.jar" + ((Get-ChildItem .m2 -Recurse -Filter *.jar | ForEach-Object { ";$_" }) -join "")
java -cp $cp io.harnessengineering.app.HarnessApplication
```

Configuration (`application.yml`):

```yaml
server:
  port: 8080

harness:
  session-dir: .sessions
```

The adapter wires the JSONL `SessionStore`, serves the read-only Session API and SSE through Spring MVC, and runs a demo agent on session `demo`.

## HTTP API

The read-only Session API is served by the Spring MVC adapter. A framework-free variant also exists in `harness-core` over the JDK `HttpServer` (no framework dependency); start it programmatically with a `SessionStore`:

```java
try (HarnessHttpServer server = new HarnessHttpServer(new JsonlSessionStore(Path.of(".sessions")), 8080)) {
    server.start();
}
```

Endpoints:

```text
GET /                            browser client page (EventSource + fetch, read-only)
GET /sessions/{id}               committed events as JSON
GET /sessions/{id}/messages      derived model messages as JSON
GET /sessions/{id}/stream        SSE stream (replays, then follows live events)
```

```powershell
curl http://127.0.0.1:8080/
curl http://127.0.0.1:8080/sessions/demo
curl -N http://127.0.0.1:8080/sessions/demo/stream
```

Open `http://127.0.0.1:8080/` in a browser to watch a session live.

The web layer only reads Session state; it never mutates Agent loop internals directly.

## CLI

Package the core JAR, then append or replay a persisted Session event log from the workspace root:

```powershell
mvn -q package
$cp = "harness-core/target/harness-core-0.1.0-SNAPSHOT.jar" + ((Get-ChildItem .m2 -Recurse -Filter *.jar | ForEach-Object { ";$_" }) -join "")
java -cp $cp io.harnessengineering.cli.HarnessCli append .sessions demo user "Hello"
java -cp $cp io.harnessengineering.cli.HarnessCli append .sessions demo assistant "Hi there"
java -cp $cp io.harnessengineering.cli.HarnessCli replay .sessions demo
```

## Current Scope

The runtime now includes:

- typed service registry, plugin lifecycle fibers, and synchronous event semantics;
- declarative YAML plugin composition with isolated nested groups;
- append-only Session event logs with in-memory and JSONL persistence;
- provider-neutral LLM streaming, parallel Tool execution with retry and cancellation convergence, and an Agent turn loop;
- a CLI and a read-only HTTP/SSE server (plus browser client) over persisted session logs;
- a Spring Boot application assembly in `harness-spring-app` (Tomcat + Spring MVC, SSE via `SseEmitter`).
