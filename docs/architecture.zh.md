# 架构

HarnessEngineering 是一个 Java 21 agent 运行时，行为取向来自 DeepSeek Harness：通过注册贡献、副作用可逆、状态经 Session 转发、每条 Agent 由单一虚拟线程驱动。本文描述已交付源码（沿袭 [PLAN.md](../PLAN.md) 与 [GUIDE.md](../GUIDE.md)）。

## 模块

```
harness-engineering/        多模块父工程（io.harnessengineering:0.1.0-SNAPSHOT）
├── harness-core/           框架无关的核心运行时；不依赖 Spring/Web
│   └── io.harnessengineering/
│       ├── core/           ServiceKey、Context、Plugin、Fiber、PluginManager、Effect、EventBus、CancellationToken
│       ├── session/        SessionId、SessionEvent（seq/time/type/data）、Message、投影、
│       │                   SessionStore（内存 + JSONL）、EventLogSession、事件校验器
│       ├── llm/            LlmProvider、LlmRequest、StreamChunk、LlmSessionWriter
│       ├── tools/          Tool、ToolDefinition、ToolCall、ToolResult、ToolRegistry、ToolPipeline、RetryPolicy
│       ├── agent/          Agent、AgentState、Inbox（followup/steer/inject）
│       ├── config/         YAML 配置模型、加载器、插件工厂、嵌套组
│       ├── projection/     SessionProjection：上下文压力/构成/用量/逐请求台账
│       ├── http/           HarnessHttpServer：基于 JDK HttpServer 的框架无关只读 HTTP/SSE
│       └── cli/            HarnessCli：append / replay / list
└── harness-spring-app/     Spring Boot 组装层（Tomcat + Spring MVC + SseEmitter）
    └── io.harnessengineering.app/
        ├── HarnessApplication、HarnessProperties（harness.session-dir）
        ├── HarnessRuntimeConfiguration → SessionStore Bean
        ├── SessionRegistry            → JVM 内共享的 EventLogSession 缓存
        ├── AgentHost                  → 每个 Session 一个 Agent，首次使用时创建
        ├── DemoProvider               → 带工具往返的流式演示 Provider
        ├── CurrentTimeTool            → 演示工具 harness_current_time
        └── SessionApiController       → 会话列表/创建/发送/投影 + SSE + 浏览器页面
```

依赖方向单向：`harness-spring-app` 依赖 `harness-core`；核心永不反向依赖 Spring 或 Web 层。

## 核心机制

### 注册即副作用

一切贡献——服务、事件监听器、工具、插件——都通过返回 disposer（`Effect`）的 API 注册。关闭插件 Fiber 时按逆序执行 disposer，保证确定性且幂等的清理。`ServiceRegistry` 用 `ServiceKey<T>` 管理贡献，依赖解析类型安全。

### 插件与 Fiber 生命周期

| 状态 | 含义 |
|---|---|
| `PENDING` | 依赖未满足；尚未执行 `apply` |
| `LOADING` | 依赖满足；正在执行一次 `apply` |
| `ACTIVE` | 已成功应用 |
| `UNLOADING` | 某依赖消失；逆序执行 disposer，然后回到 `PENDING` |
| `FAILED` | `apply` 抛异常；保留原始异常 |
| `DISPOSED` | 已显式关闭；不可再次激活 |

`PluginManager` 订阅注册表变化并独立刷新每个 Fiber——服务替换只卸载/重载依赖它的 Fiber。

### 事件总线

`InMemoryEventBus` 提供五种语义：`emit`（不消费返回值）、`parallel`、`serial`、`bail`（遇到非空立即结束）、`waterfall`（监听器通过 `next()` 包装后续链；不调用 `next()` 即拦截）。

### Session 事件日志

`Session` 是追加式事件日志。`EventLogSession.append` 的顺序是校验 → 持久化 → 通知监听器——持久化失败绝不发布成功。`SessionEvent` 是不可变 record `(sequence, time, type, data)`，序号单调递增。`deriveMessages()` 从已提交事件投影模型可见对话；JSONL 后端原子追加（临时文件 + 原子移动）。

### 模型可见 ⟺ 已记录

任何进入模型请求的输入必须能从 Session 日志重建。因此 Agent 循环写入 `user/message` 与 `assistant/message` 事件；工具结果必须对后续步骤可见，循环把已执行的 `ToolResult` 折叠进下一步输入（`Message("tool", …)`），保证每个模型可见输入都来自已记录事件。

### Agent 循环

一个 `Agent` 拥有一个虚拟线程与一个 `Inbox`：

```
turn/start → 取 followup → step/start → LlmSessionWriter.stream
  → assistant chunk / assistant message → 有工具调用？
  → ToolPipeline.executeParallel（tool/call + tool/result 事件）
  → 工具结果折叠进下一步输入 → step/end → turn/end
```

`followup` 开新 turn；`steer`/`inject` 给当前 turn 的下一步追加输入。取消通过 `CancellationToken` 协作式完成；`close()` 等待线程终止闩。

## 投影

`io.harnessengineering.projection.SessionProjection` 把会话事件快照折叠成框架无关的记录：

- `ContextPressure(contextWindow, projectedTokens, pressureTokens, percent)` — 占用。
- `ContextBreakdown(systemTokens, toolsTokens, messageTokens)` — 启发式构成。
- `TokenUsage(uncachedInputTokens, cacheReadTokens, cacheWriteTokens, outputTokens)` — 累计用量。
- `Result.requests()` — 逐 assistant 消息台账（turn/step、provider/model、输入/输出/推理、时间）。

所有 token 数字都是**参考估计值**，由 `TokenEstimator`（字符/token 启发式）从事件文本推导；核心不运行任何供应商分词器。

## Web 层

Spring 适配层提供 Session Workbench（见 `harness-core/src/main/resources/web/index.html`）：

- `GET /sessions` — 会话摘要（id、事件数、最后事件时间）。
- `POST /sessions` — 创建会话，返回 `{id}`。
- `GET /sessions/{id}` — 已提交事件。
- `GET /sessions/{id}/messages` — 派生的模型消息。
- `GET /sessions/{id}/projection` — 上下文仪表与弹窗所需的投影记录。
- `POST /sessions/{id}/messages` — `{content}`；交给该会话的 Agent（202），回合经 SSE 回流。
- `GET /sessions/{id}/stream` — SSE：先重放，再跟随实时事件。
- `GET /` — 浏览器客户端。

`AgentHost` 按需为每个 Session 创建并持有唯一的 `Agent`（演示 Provider + `harness_current_time` 工具），任意会话都可对话。核心中的 `HarnessHttpServer` 提供同构的只读 API（不含发送），供框架无关场景使用。

## 参见

- [glossary.md](glossary.md) — 代码库通用术语。
- [development.md](development.md) — 构建、运行与贡献指南。
- [testing.md](testing.md) — 测试策略与覆盖预期。
- [cookbook/](cookbook/) — 操作手册（新增 Tool、LLM 适配器、Web 界面）。