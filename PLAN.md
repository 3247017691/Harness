# HarnessEngineering Java 版项目计划书

## 1. 项目目标

HarnessEngineering 是一个使用 Java 21 重实现 DeepSeek Harness 核心运行时思想的工程。第一阶段不追求逐行移植 TypeScript，而是保留可组合插件、依赖注入、Fiber 生命周期、可逆副作用、事件 Waterfall、会话事件日志和 Agent Loop 这些决定系统行为的机制。

项目定位为可运行的工程基础，不直接绑定 Spring、OSGi 或某一家模型供应商。框架层使用 Java 标准库和少量稳定依赖，业务层通过接口和插件组合。

## 2. 非目标

- 不复制 Cordis 的 JavaScript Proxy 语义。
- 不在第一阶段实现完整 Web GUI。
- 不在第一阶段实现所有 DeepSeek Harness 工具。
- 不承诺兼容 DeepSeek Harness 的磁盘格式或插件二进制格式。
- 不把 Spring ApplicationContext 当作核心运行时；后续可以提供适配层。

## 3. 目标架构

```text
Bootstrap
  -> Configuration Loader
  -> Runtime Context
       -> Service Registry
       -> Plugin Manager
       -> Event Bus
       -> Session Store
       -> LLM Gateway
       -> Tool Registry
       -> Agent Factory
  -> Agent Loop
       -> Inbox
       -> Turn
       -> Step
       -> Model Stream
       -> Tool Pipeline
       -> Session Events
```

### 3.1 Context

`Context` 持有服务注册表、事件总线、日志器和运行时配置。Java 版本使用显式 API：`context.get(ServiceKey<T>)`，不模仿 `ctx.sessions` 的动态属性代理。

### 3.2 Service Registry

服务由 `ServiceKey<T>` 标识。提供服务时必须保证同一作用域中不会出现重复实现。服务替换会通知依赖插件，使依赖插件先卸载、再在依赖重新满足时加载。

### 3.3 Plugin 和 Fiber

插件声明名称、依赖服务和 `apply(Context)`。每次挂载产生一个 Fiber。Fiber 状态为：

`PENDING -> LOADING -> ACTIVE -> UNLOADING -> PENDING`

启动失败进入 `FAILED`，显式关闭进入 `DISPOSED`。插件返回的 `Effect` 由 Fiber 持有，关闭时按逆序执行。

### 3.4 Event Bus

提供五类语义：

- `emit`：同步通知，不消费返回值。
- `parallel`：并发执行并等待所有监听器。
- `serial`：按顺序执行并等待。
- `bail`：遇到非空结果立即结束。
- `waterfall`：监听器通过 `next()` 包装后续链；不调用 `next()` 即拦截。

### 3.5 Session

Session 是追加式事件日志。模型可见的消息、模型响应、工具调用和工具结果必须进入日志。模型历史由日志投影得到，而不是由 Agent 内存变量作为唯一来源。

### 3.6 Agent Loop

每个 Agent 有一个输入 Inbox。驱动流程为：

```text
turn/start
  -> claim input
  -> agent/pre-step
  -> step/start
  -> agent/request
  -> llm stream
  -> assistant chunks/message
  -> tool pipeline
  -> step/end
  -> turn/end
```

第一版先实现串行工具调用；并发工具调用、取消收敛和重试策略在后续阶段加入。

## 4. 分阶段路线

### Phase 0：工程骨架

交付：Java 21、Maven、JUnit、格式规范、README、GitHub 远程。

验收：`mvn test` 能执行；示例程序可启动；本地 Git 可提交。

### Phase 1：Cordis 风格核心

交付：`Context`、`ServiceKey`、`ServiceRegistry`、`Plugin`、`Fiber`、`PluginManager`、`Effect`、`EventBus`。

验收：

- 缺少依赖的插件保持 `PENDING`。
- 提供依赖后插件进入 `ACTIVE`。
- 服务移除或替换会触发依赖插件的卸载和重新加载。
- Effect 按逆序清理且只执行一次。
- Waterfall 可修改值，也可通过不调用 `next()` 拦截。
- 启动失败进入 `FAILED`，错误可查询。

### Phase 2：配置和组合

交付：YAML 配置模型、插件工厂、插件 ID、配置校验、嵌套组和隔离作用域。

验收：配置可加载、禁用插件不启动、配置更新可回滚、启动错误带有插件 ID。

### Phase 3：Session 事件日志

交付：`SessionId`、事件 envelope、事件序号、消息投影、内存后端、JSONL 后端。

验收：追加事件后序号单调递增；重放得到同样消息；非法事件被拒绝；持久化失败不会伪造成功状态。

### Phase 4：LLM 与 Tool 能力

交付：`LlmProvider`、流式 chunk、请求头、Tool Definition、Tool Executor、工具 Waterfall。

验收：模型响应可流式写入 Session；工具调用和结果可重放；工具失败进入结构化结果；工具注册随插件卸载消失。

### Phase 5：Agent Loop

交付：Inbox、Turn、Step、取消、重试、工具循环。

验收：用户消息触发一个 turn；工具调用产生后续 step；取消不会遗留运行中的任务；所有模型可见输入可从日志重建。

### Phase 6：命令行和 Web

交付：CLI、HTTP API、SSE、浏览器客户端。

建议：先做 CLI 和 SSE，再决定是否引入 Spring Boot、Vert.x 或 Quarkus。Web 层只读取 Session 与 Agent 状态，不直接修改 Loop 内部状态。

## 5. 推荐目录

```text
HarnessEngineering/                  # 父 POM（多模块）
├── pom.xml
├── README.md / README.zh.md         # 双语 README（镜像 dsh 形态）
├── AGENTS.md                        # 贡献者/Agent 指引（镜像 dsh 形态）
├── PLAN.md / GUIDE.md
├── docs/                            # 架构、术语表、开发、测试、cookbook（中英成对）
│   ├── architecture.md / .zh.md
│   ├── glossary.md / .zh.md
│   ├── development.md / .zh.md
│   ├── testing.md / .zh.md
│   └── cookbook/
│       ├── adding-a-tool.md / .zh.md
│       ├── adding-an-llm-adapter.md / .zh.md
│       └── adding-a-web-surface.md / .zh.md
├── harness-core/                    # 框架无关的核心运行时
│   ├── pom.xml
│   └── src/main/java/io/harnessengineering/
│       ├── core/          # Context、Plugin、Fiber、EventBus、CancellationToken
│       ├── session/       # Session 和事件投影、内存/JSONL 后端
│       ├── llm/           # 模型适配器接口和流
│       ├── tools/         # Tool 定义、并行执行管线、重试
│       ├── agent/         # Agent、Inbox、Loop
│       ├── config/        # YAML 配置与 Loader
│       ├── projection/    # 上下文压力/构成/用量/台账投影
│       ├── http/          # 只读 HTTP/SSE 服务（JDK HttpServer）
│       └── cli/           # 命令行入口
│   └── src/test/java/
└── harness-spring-app/              # Spring Boot 组装层（适配层依赖核心）
    ├── pom.xml
    └── src/main/java/io/harnessengineering/app/
```

## 6. 技术决策

- Java 21：使用 record、sealed interface、虚拟线程和结构化并发的可选能力。
- Maven：第一阶段降低工程工具复杂度。
- JUnit 5：单元和生命周期测试。
- Jackson YAML：Phase 2 再引入，不让配置依赖污染核心。
- Reactor 或 Mutiny：只有在流式和取消语义证明需要后再引入。
- Spring Boot：作为应用层适配，不作为核心插件运行时。
- OSGi：暂不采用。它适合成熟模块分发，但会显著增加第一阶段的部署和调试成本。

## 7. 质量门槛

每个阶段都必须满足：

1. 公开 API 有 Javadoc。
2. 生命周期测试覆盖成功、失败、取消和重复关闭。
3. 注册表贡献有卸载测试。
4. 模型可见内容有快照或确定性断言。
5. 不把异常吞掉；清理错误要记录并保证其他 disposer 继续执行。
6. 所有异步任务有 owner、有取消方式、有完成等待点。

## 8. GitHub 协作

远程仓库：`https://github.com/3247017691/Harness.git`

推荐分支：

- `main`：可运行主线。
- `feature/core-runtime`：Phase 1。
- `feature/session-log`：Phase 3。
- `feature/agent-loop`：Phase 5。

首次推送前设置代理：

```powershell
$env:ALL_PROXY = "http://127.0.0.1:7897"
$env:all_proxy = $env:ALL_PROXY
git remote add origin https://github.com/3247017691/Harness.git
git push -u origin main
```

## 9. 第一阶段完成定义

Phase 1 完成的标准不是代码数量，而是以下行为同时成立：插件可以声明依赖；依赖未满足时不执行；服务出现时自动激活；服务替换时旧副作用先清理；清理后插件重新执行；事件 Waterfall 能被中间件拦截；关闭管理器后没有仍然注册的监听器或服务。

## 10. 实施进度

- Phase 0 工程骨架：完成（Maven 多模块、JUnit 5、README）。
- Phase 1 核心运行时：完成（Context、ServiceKey/Registry、Plugin/Fiber/PluginManager、Effect、EventBus）。
- Phase 2 配置和组合：完成（YAML 模型、插件工厂、PluginId、校验、嵌套组与隔离作用域）。
- Phase 3 Session 事件日志：完成（SessionId、envelope、序号、消息投影、内存与 JSONL 后端、list() 枚举）。
- Phase 4 LLM 与 Tool：完成（LlmProvider、流式 chunk、Tool 定义/注册/执行、结构化失败、工具结果回灌模型输入）。
- Phase 5 Agent Loop：完成（Inbox、Turn/Step、串行与并行工具循环、取消、关闭等待）。
- Phase 6 命令行和 Web：完成（CLI append/replay/list、HTTP API、SSE、浏览器客户端）。
- 延后能力：完成（并行工具调用、取消收敛、工具重试 RetryPolicy）。
- 框架适配层：完成（`harness-spring-app`，Spring Boot 组装，含 Tomcat/Spring MVC Web 层；方向为核心 ← 适配层）。
- **形态对齐（deepseek-harness 形态）**：完成（AGENTS.md + docs/ 双语树；Session Workbench 深色工作台——会话侧栏/新建/切换、会话头 Context + Session log、流式对话与工具卡片、composer 上下文仪表、Context 弹窗与投影台账；`GET/POST /sessions`、`POST /sessions/{id}/messages`、`GET /sessions/{id}/projection`）。

后续方向：LLM 请求超时/取消接入、SQLite 后端、可观测性。
