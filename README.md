# HarnessEngineering

A Java 21 runtime foundation that implements composable plugins, typed services, reversible effects, lifecycle fibers, and event dispatching.

## Prerequisites

- JDK 21 or later
- Maven 3.9 or later

## Verification

```powershell
mvn test
mvn -q package
```

## HTTP API

The read-only HTTP server exposes Session state over the JDK `HttpServer` (no framework dependency). Start it programmatically with a `SessionStore`:

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

Package the JAR, then append or replay a persisted Session event log from the workspace root:

```powershell
mvn -q package
$cp = "target/harness-engineering-0.1.0-SNAPSHOT.jar" + ((Get-ChildItem .m2 -Recurse -Filter *.jar | ForEach-Object { ";$_" }) -join "")
java -cp $cp io.harnessengineering.cli.HarnessCli append .sessions demo user "Hello"
java -cp $cp io.harnessengineering.cli.HarnessCli append .sessions demo assistant "Hi there"
java -cp $cp io.harnessengineering.cli.HarnessCli replay .sessions demo
```

## Current Scope

The runtime now includes:

- typed service registry, plugin lifecycle fibers, and synchronous event semantics;
- declarative YAML plugin composition with isolated nested groups;
- append-only Session event logs with in-memory and JSONL persistence;
- provider-neutral LLM streaming, parallel Tool execution with retry, and an Agent turn loop;
- a CLI and a read-only HTTP/SSE server over persisted session logs.
