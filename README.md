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
- provider-neutral LLM streaming, serial Tool execution, and an Agent turn loop;
- a CLI for writing and replaying persisted session logs.
