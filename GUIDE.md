# HarnessEngineering Java 实施指导书

## 1. 环境准备

安装并确认：

```powershell
java -version
mvn -version
git --version
```

目标版本：JDK 21，Maven 3.9 或更高版本。

在 GitHub 创建空仓库后，在本地工作区执行：

```powershell
cd D:\.Workspace\HarnessEngineering
git init -b main
git remote add origin https://github.com/3247017691/Harness.git
```

网络代理只在当前 PowerShell 会话生效：

```powershell
$env:ALL_PROXY = "http://127.0.0.1:7897"
$env:all_proxy = $env:ALL_PROXY
```

不要把代理地址写进源码、Maven 配置或 Git 仓库。

## 2. 建立 Maven 工程

创建 `pom.xml`，第一版只配置：

- `maven.compiler.release=21`
- JUnit 5
- Surefire
- UTF-8 编码

第一版不引入 Spring。核心运行时应该在没有 Web 框架的情况下可测试。

## 3. 实现 Core API

### 3.1 ServiceKey

```java
public record ServiceKey<T>(String name, Class<T> type) {}
```

服务查找必须类型安全。禁止在业务代码中到处使用字符串和强制类型转换。

### 3.2 Effect

```java
@FunctionalInterface
public interface Effect extends AutoCloseable {
    void close();
}
```

`close()` 必须幂等。Fiber 关闭时从最后注册的 Effect 开始执行。一个 disposer 失败不能阻止后续 disposer 执行；失败应被收集并记录。

### 3.3 Plugin

```java
public interface Plugin {
    String name();
    List<ServiceKey<?>> requires();
    Effect apply(Context context) throws Exception;
}
```

插件的 `apply` 只负责注册本插件拥有的服务、事件、工具和任务，并返回对应的清理效果。插件不能修改其他插件的 Effect。

### 3.4 Fiber

Fiber 至少需要：

```java
enum FiberState { PENDING, LOADING, ACTIVE, FAILED, UNLOADING, DISPOSED }
```

实现要求：

1. 依赖不齐时为 `PENDING`，不能执行 `apply`。
2. 依赖满足时进入 `LOADING`，执行一次 `apply`。
3. 成功后为 `ACTIVE`。
4. 依赖消失时进入 `UNLOADING`，逆序关闭 Effect，回到 `PENDING`。
5. `close()` 后为 `DISPOSED`，不允许再次激活。
6. `apply` 抛异常时为 `FAILED`，保留原始异常。
7. `close()` 和每个 Effect disposer 都必须幂等。

不要用一个 boolean 替代状态枚举；后续诊断、重载和并发测试需要区分这些状态。

### 3.5 PluginManager

PluginManager 保存 Fiber 列表，并订阅 ServiceRegistry 的变化：

```java
public Fiber mount(Plugin plugin) {
    Fiber fiber = new Fiber(plugin, context);
    fibers.add(fiber);
    fiber.refresh();
    return fiber;
}
```

当服务发生变化时，不能简单地重新执行所有插件。每个 Fiber 根据自己的依赖计算是否需要加载或卸载。

## 4. 实现 EventBus

推荐先实现同步版本，再实现异步版本。Waterfall 的核心是递归链：

```java
private Object call(int index, Object value) {
    if (index == listeners.size()) return terminal.apply(value);
    Listener listener = listeners.get(index);
    return listener.call(value, nextValue -> call(index + 1, nextValue));
}
```

必须测试三种情况：

1. 监听器修改值并调用 `next()`，后续仍然执行。
2. 监听器不调用 `next()`，终点行为不执行。
3. 多个监听器按注册顺序组成链。

监听器注册返回 Effect，确保插件卸载后不会继续收到事件。

## 5. 实现 Session

### 5.1 事件 envelope

建议使用不可变 record：

```java
public record SessionEvent(
    long seq,
    Instant time,
    String type,
    JsonNode data
) {}
```

第一版可以用 Jackson `JsonNode`，但所有进入 Session 的数据都必须经过一次快照，避免调用方继续修改原对象。

### 5.2 Session API

```java
public interface Session {
    SessionId id();
    SessionEvent append(String type, JsonNode data);
    List<SessionEvent> events();
    List<Message> deriveMessages();
}
```

`append` 必须保证：序号单调递增、事件追加后不可修改、事件广播发生在提交之后。持久化后端订阅 `session/event`，而不是由 Agent 直接调用文件系统。

## 6. 实现 LLM 与 Tool

### 6.1 LLM 接口

```java
public interface LlmProvider {
    String providerId();
    Stream<StreamChunk> stream(LlmRequest request, CancellationToken token);
}
```

请求必须包含 provider、model、messages 和工具 schema。模型响应 chunk 先写入临时 assembler，完成后追加 `assistant/message`。

### 6.2 Tool 接口

```java
public interface Tool {
    ToolDefinition definition();
    ToolResult execute(ToolCall call, ToolContext context) throws Exception;
}
```

工具执行顺序：

```text
preExecute -> execute -> postExecute -> result
```

权限、沙箱、超时和取消应放在管线中，不要散落在每个 Tool 实现中。

## 7. 实现 Agent Loop

### 7.1 Inbox

Inbox 至少分为：

- `nextTurn`：下一轮 turn 使用。
- `nextStep`：当前 turn 的下一 step 使用。

`followup` 放入 `nextTurn` 并唤醒；`steer` 放入 `nextStep` 并唤醒；`inject` 放入 `nextStep` 但不唤醒。

### 7.2 Loop 伪代码

```java
while (!closed) {
    Turn turn = session.startTurn();
    MessageBatch input = inbox.claimNextTurn();
    if (preStep.reject(input)) {
        session.endTurn(Blocked.INSTANCE);
        continue;
    }

    while (true) {
        session.startStep(turn);
        Request request = requestPipeline.build(input, session.history());
        AssistantMessage response = llm.stream(request).assemble();
        session.appendAssistant(response);

        List<ToolCall> calls = response.toolCalls();
        if (calls.isEmpty()) break;
        List<ToolResult> results = tools.execute(calls);
        session.appendToolResults(results);
        input = inbox.claimNextStep();
    }
    session.endTurn(Completed.INSTANCE);
}
```

第一版使用一个虚拟线程执行一个 Agent。不要一开始使用共享线程池驱动所有 Agent，否则取消和关闭的所有权不清晰。

## 8. 并发与取消

每个长期操作必须有：

- 所属 Agent 或 Plugin owner；
- `CancellationToken` 或 `Future`；
- 明确的完成等待点；
- 关闭时的清理顺序。

推荐顺序：停止接收新输入 -> 取消当前模型请求 -> 等待流结束 -> 停止工具 -> 关闭 Agent scope -> 从注册表移除。

不要在 `close()` 中直接 `shutdownNow()` 后立即删除对象。必须等待已经启动的任务完成清理，避免结果在对象已经不可见后继续发布。

## 9. 测试清单

### Core

- 缺依赖的插件保持 Pending。
- 提供服务后进入 Active。
- 替换服务时 disposer 执行一次。
- 多次 close 不重复执行 disposer。
- 失败插件保留错误。
- 管理器 close 后事件监听器已移除。

### EventBus

- emit 通知所有监听器。
- waterfall 正确调用 next 链。
- 不调用 next 时终点被拦截。
- 监听器抛异常时行为确定。

### Session

- seq 单调递增。
- 事件不可变。
- replay 与实时 derive 结果一致。
- 非法 envelope 被拒绝。
- 持久化失败不会发布成功通知。

### Agent

- 一个 followup 产生一个 turn。
- 工具调用产生下一个 step。
- steer 不新开 turn。
- inject 不唤醒 idle Agent。
- 取消后不再派发新工具调用。
- close 等待当前任务退出。

推荐所有生命周期测试使用 `AtomicInteger`、`CountDownLatch` 或 `CompletableFuture`，避免 `Thread.sleep`。

## 10. 本地验证命令

```powershell
cd D:\.Workspace\HarnessEngineering
mvn test
mvn -q package
```

多模块布局：`harness-core`（框架无关的核心运行时）和 `harness-spring-app`（Spring Boot 组装层）。根目录构建会依次验证两个模块。

代码完成后检查：

```powershell
git status --short
git diff --check
git add .
git commit -m "feat: add Java harness runtime foundation"
```

## 11. 推送到 GitHub

确认远程和分支：

```powershell
git remote -v
git branch --show-current
```

设置代理并通过 SSH 推送：

```powershell
$env:ALL_PROXY = "http://127.0.0.1:7897"
$env:all_proxy = $env:ALL_PROXY
git push -u origin main
```

如果使用 GitHub Personal Access Token（PAT），不要将令牌写入源码、文档、Git URL、Maven 配置或 Git 提交。令牌只应短暂设置在当前终端，或由 Git Credential Manager 保存：

```powershell
$env:GITHUB_TOKEN = "<new-token>"
```

令牌泄露后应立即在 GitHub 的 Settings -> Developer settings -> Personal access tokens 中撤销，并生成仅包含所需仓库权限的新令牌。推送失败时先区分：认证失败、代理失败、远程非空、分支保护或网络超时。不要使用 `git push --force` 覆盖远程历史。

## 12. 什么时候引入框架

核心测试稳定后才引入框架。当前状态：

- **Spring Boot（已引入）**：`harness-spring-app` 模块提供应用启动、配置和装配；Web 层使用 `spring-boot-starter-web`（Tomcat + Spring MVC，SSE 用 `SseEmitter`）。核心模块的 JDK `HttpServer` 保留为框架无关选项，两种 HTTP 层都只读 Session 状态，依赖方向保持不变（核心不依赖 Spring）。
- **Reactor/Mutiny**：需要真正的异步流组合时再引入。
- **Jackson**：已用于配置、Session 持久化和 JSON 输出。
- **SQLite**：需要可查询的 Session 存储时再引入。

引入框架时保持依赖方向：框架适配层依赖 Harness 核心，核心不能反向依赖 Web 或 Spring。

## 13. 当前工作边界

截至当前进度，已完成：

- Phase 0 工程骨架、Phase 1 核心运行时、Phase 2 配置与插件组合、Phase 3 Session 事件日志（内存 + JSONL）、Phase 4 LLM 与 Tool、Phase 5 Agent Loop、Phase 6 CLI + HTTP/SSE + 浏览器客户端；
- 延后能力：并行工具调用、取消收敛、工具重试；
- 应用框架适配层：`harness-spring-app`（Spring Boot 装配，含配置属性、HttpServer/SessionStore Bean 和演示 Agent）。

后续可选方向：并发与取消的边界测试补强、LLM 请求超时/取消接入、SQLite 后端、可观测性（指标/日志）。新工作区先读取本文件和 `PLAN.md` 了解现状，再继续下一步。
