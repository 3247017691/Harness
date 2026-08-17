# AGENTS.md

HarnessEngineering 是一个 Java 21 实现的 agent 运行时：一切贡献（服务、插件、事件、会话、工具、Agent）都通过核心扩展点注册，副作用可逆、生命周期可诊断。阅读 [docs/architecture.md](docs/architecture.md) 之后再改动 `harness-core/`；文档规范见 [docs/development.md](docs/development.md)。

## 仓库布局

```
harness-core/     框架无关的核心运行时（不依赖 Spring/Web）
  io.harnessengineering.core/     ServiceKey、Context、Plugin/Fiber、Effect、EventBus、CancellationToken
  io.harnessengineering.session/  Session 事件日志：envelope、消息投影、内存/JSONL 后端
  io.harnessengineering.llm/      LlmProvider、流式 chunk、assistant 消息写回
  io.harnessengineering.tools/    Tool 定义、注册、并行管线、重试
  io.harnessengineering.agent/    Agent、Inbox、Turn/Step 循环
  io.harnessengineering.config/   YAML 配置与插件组合
  io.harnessengineering.projection/ 上下文压力/构成/用量投影
  io.harnessengineering.http/     JDK HttpServer 只读 HTTP/SSE 变体
  io.harnessengineering.cli/      CLI（append/replay/list）
harness-spring-app/ Spring Boot 组装层（只依赖核心，核心永不反向依赖）
  io.harnessengineering.app/      HarnessApplication、AgentHost、SessionApiController、demo
docs/              架构、术语表、开发指南、测试指南、cookbook（中英成对）
```

## 命令

```powershell
mvn "-Dmaven.repo.local=.m2" test       # 单元测试（core + spring-app）
mvn "-Dmaven.repo.local=.m2" -q package # 打包
$cp = "harness-spring-app\target\harness-spring-app-0.1.0-SNAPSHOT.jar;harness-core\target\harness-core-0.1.0-SNAPSHOT.jar" + ((Get-ChildItem .m2 -Recurse -Filter *.jar | Where-Object { $_.FullName -notmatch '\\slf4j-api\\1\.7' } | ForEach-Object { ";$($_.FullName)" }) -join "")
java -cp $cp io.harnessengineering.app.HarnessApplication   # 启动 Web 工作台（默认 8080）
```

- 所有 Maven 命令必须在工作区根目录运行，并显式使用本地仓库 `-Dmaven.repo.local=.m2`（PowerShell 中 `-D` 参数必须加引号）。
- 下载依赖需要代理时：`$env:ALL_PROXY=$env:all_proxy="http://127.0.0.1:7897"`；不要把代理写进源码或仓库配置。
- 运行前检查：`mvn test` 全绿；`git diff --check` 无空白错误。

## 依赖方向

- `harness-core` 是框架无关的；核心不能依赖 Spring、Web 或任何框架适配层。
- `harness-spring-app` 只依赖核心；Web 层只读 Session 状态，发送消息通过 Agent 的 Inbox 提交，绝不直接修改 Agent 循环内部。
- 新增依赖、工具或面板若影响模型可见输入，必须同时追加对应的 Session 事件（模型可见 ⟺ 已记录）。

## 约定

- **注册即副作用**：所有贡献通过 `ctx.effect()` / `Effect` / 注册器返回的 disposer 注册；disposer 幂等、逆序清理。
- **模型可见 ⟺ 已记录**：任何进入模型请求的输入必须可从 Session 事件日志重建；新输入类型需要新事件类型。
- **判别字段用 switch 穷举**：闭合联合以 `default -> { }` 或显式分支收尾，不允许静默忽略。
- **会话事件先校验、后持久化、再发布**：`Session.append` 的顺序是校验 → 持久化 → 通知监听器；持久化失败不得伪造成功。
- **非平凡的改动需要 Agent Note**（docs 或 `docs/` 内的决策记录），机械性小改动除外。
- **测试描述行为，不描述实现**：改变行为时同步修改测试，并在改动说明中解释原因。
- **清理顺序**：停止接收新输入 → 取消当前模型请求 → 等待流结束 → 停止工具 → 关闭 Agent → 从注册表移除；不在 `close()` 后继续发布结果。

## 防御模式

生命周期、并发、子进程与关闭相关工作先读 [docs/development.md](docs/development.md#并发与取消)。长期操作必须有 owner、`CancellationToken` 或 `Future`、明确的完成等待点以及确定性的清理顺序。