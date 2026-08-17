# 术语表

HarnessEngineering 代码库中使用的术语，语义一致处沿袭 DeepSeek Harness 词汇。

## 核心

- **Context** — 持有服务注册表、配置与生命周期的运行时外壳；插件贡献/读取服务的接缝。
- **ServiceKey\<T\>** — 类型安全、按名称划分服务标识；查找不基于裸字符串。
- **ServiceRegistry** — 管理者已注册服务；`provide`/`remove` 通知订阅者，使依赖 Fiber 刷新。
- **Plugin** — 具名贡献单元，声明 `requires()` 与 `apply(Context)`，注册并返回 `Effect`。
- **Fiber** — 单个插件实例的生命周期外壳（`PENDING → LOADING → ACTIVE → UNLOADING → PENDING`，另有 `FAILED`/`DISPOSED` 终态）。
- **PluginManager** — 挂载插件、订阅注册表变化、独立刷新每个 Fiber。
- **Effect** — 每次注册返回的幂等 disposer；Fiber 按注册逆序执行 disposer。
- **EventBus** — 五种分发语义：`emit`、`parallel`、`serial`、`bail`、`waterfall`（监听器通过 `next()` 包装链）。
- **CancellationToken** — 协作式取消标志；长操作在安全点检查，派发前中止。

## Session

- **SessionId** — 一个追加式事件日志的稳定标识。
- **SessionEvent** — 不可变已提交条目 `(sequence, time, type, data)`；序号从 1 起严格递增。
- **Message** — 由已提交事件派生的 `(role, content)` 记录；即模型可见对话。
- **SessionStore** — 持久化后端；`append`（带序号校验）、`load`、`list()`。
- **EventLogSession** — 默认 `Session` 实现：校验 → 持久化 → 通知监听器；构造时重放。
- **Projection** — 对事件的框架无关折叠（`SessionProjection`），产出压力/构成/用量记录。所有 token 数字都是参考估计值。

## LLM / 工具

- **LlmProvider** — 模型流式输出的供应商中立来源；返回有限的 `Stream<StreamChunk>`。
- **StreamChunk** — 单次增量输出：文本、完成标记或工具调用。
- **LlmSessionWriter** — 消费 Provider 流，逐 chunk 追加 `assistant/chunk`，最后追加携带 provider/model 的 `assistant/message`。
- **Tool** — 带 `ToolDefinition`（schema）与 `execute(ToolCall, ToolContext)` 的可执行能力。
- **ToolRegistry** — 管理者工具；注册 effect 关闭时贡献消失。
- **ToolPipeline** — 执行调用（串行或并行），带中间件、重试与取消感知派发；确定性记录 `tool/call` 与 `tool/result` 事件。
- **RetryPolicy** — 工具失败重试的 `(maxAttempts, delay)` 计划；`none()` 关闭重试。

## Agent

- **Agent** — 拥有一个 Session 的单一虚拟线程；把 Inbox 消息变成模型步骤与工具调用。
- **Inbox** — 区分下一轮输入（`followup`）与当前轮步骤输入（`steer`/`inject`）。
- **Turn / Step** — turn 是一次用户触发的 Agent 运行（`turn/start`…`turn/end`）；step 是一轮模型往返（含可选工具执行，`step/start`…`step/end`）。
- **工具结果回灌** — 已执行 `ToolResult` 折叠进下一步的模型可见输入（`Message("tool", …)`），保证每个模型输入都能从日志推导。

## Web / 应用

- **AgentHost** — Spring 组件，按需为每个 Session 创建并持有唯一 `Agent`。
- **SseEmitter** — Spring MVC SSE 通道：连接时重放会话日志，随后跟随实时事件。
- **Session Workbench** — 浏览器客户端（`index.html`）：会话侧栏、会话头（Context / Session log）、带上下文仪表的 composer、SSE 实时更新。