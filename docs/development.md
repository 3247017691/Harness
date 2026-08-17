# Development

Build, run, and contribution guidance for HarnessEngineering. Also read [architecture.md](architecture.md), [glossary.md](glossary.md), and [AGENTS.md](../AGENTS.md) before changing the core.

## Prerequisites

- JDK 21 or later (built against Java 25.0.2 runtime).
- Maven 3.9 or later.
- (Downloads only) an HTTP proxy at `http://127.0.0.1:7897` when the network needs it.

## Build and test

All Maven commands run from the workspace root. Use the workspace-local repository (PowerShell requires quoting the `-D` argument):

```powershell
mvn "-Dmaven.repo.local=.m2" test
mvn "-Dmaven.repo.local=.m2" -q package
```

`test` runs both modules: `harness-core` unit tests and `harness-spring-app` integration tests (real Tomcat boot + SSE reads). Coverage gate: the core suite must stay green; every behavior change adds or updates an owning test.

## Run the application

Package, assemble the classpath (the spring jar, the core jar, and every `.m2` jar except the stale `slf4j-api/1.7` that surefire caches), and start:

```powershell
mvn "-Dmaven.repo.local=.m2" -q package
$cp = "harness-spring-app\target\harness-spring-app-0.1.0-SNAPSHOT.jar;harness-core\target\harness-core-0.1.0-SNAPSHOT.jar" + ((Get-ChildItem .m2 -Recurse -Filter *.jar | Where-Object { $_.FullName -notmatch '\\slf4j-api\\1\.7' } | ForEach-Object { ";$($_.FullName)" }) -join "")
java -cp $cp io.harnessengineering.app.HarnessApplication
```

Default configuration (`harness-spring-app/src/main/resources/application.yml`): port `8080`, session dir `.sessions`. Open `http://127.0.0.1:8080/` for the Session Workbench.

CLI (core only):

```powershell
$cp = "harness-core\target\harness-core-0.1.0-SNAPSHOT.jar" + ((Get-ChildItem .m2 -Recurse -Filter *.jar | ForEach-Object { ";$($_.FullName)" }) -join "")
java -cp $cp io.harnessengineering.cli.HarnessCli append .sessions demo user "Hello"
java -cp $cp io.harnessengineering.cli.HarnessCli replay .sessions demo
java -cp $cp io.harnessengineering.cli.HarnessCli list .sessions
```

## Contribution rules

- **Dependency direction**: the core never imports Spring or web classes; new framework features live in `harness-spring-app`.
- **Registration is an effect**: contributions register through APIs that return an idempotent disposer; plugin teardown runs disposers reverse-order.
- **Model-visible ⟺ logged**: any input a model request sees must be reconstructable from the session log; add a session event type when you add a model-visible input.
- **Session invariants**: append = validate → persist → notify; persistence failure never publishes success; sequence is strictly increasing.
- **Concurrency**: long operations need an owner, a cancellation token/future, a completion wait point, and a deterministic shutdown order (stop intake → cancel request → await stream → stop tools → close agent → deregister). Never `shutdownNow()` and drop objects while their results may still publish.
- **Non-trivial changes add an Agent Note** under `docs/` recording the decision; mechanical edits are exempt.
- **Tests describe behavior**, not implementation; when behavior changes, change its tests in the same change.
- **Switch on discriminant tags**; closed unions end in an explicit branch, not silent fall-through.
- **Cleanup errors are collected, not swallowed**; one disposer failing must not stop later disposers.

## Environment notes (this workspace)

- The local Maven repository is `.m2/` inside the workspace (the default user `.m2` is not writable here); always pass `-Dmaven.repo.local=.m2` quoted.
- A stale `slf4j-api/1.7.x` can land in `.m2` from surefire; filter it out of the app classpath (see the run command above).
- Test isolation: Spring tests pass `--harness.session-dir=<temp>` and `--server.port=0` as command-line args (highest precedence) so they never touch the real `.sessions/`.
- Proxy is session-scoped; never commit proxy settings or tokens.

## Documentation

Bilingual pairs: English in `*.md`, Simplified Chinese in `*.zh.md`, kept in sync section-for-section. The docs tree mirrors the code structure (`docs/architecture.md`, `docs/glossary.md`, `docs/development.md`, `docs/testing.md`, `docs/cookbook/`).