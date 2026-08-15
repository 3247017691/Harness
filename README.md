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

## Current Scope

The initial implementation provides the Phase 1 runtime described in `PLAN.md`:

- typed service registry with change notifications;
- dependency-aware plugin lifecycle management;
- reverse-order, idempotent effect cleanup;
- synchronous event semantics: emit, serial, parallel, bail, and waterfall.
