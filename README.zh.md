# HarnessEngineering

一个以 [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness) 为形态的 Java 21 agent 运行时：可组合插件、类型化服务、可逆副作用、追加式会话事件日志、供应商中立的 LLM 接缝、并行工具，以及单所有者 Agent 循环——外加一个深色主题的 Session Workbench 浏览器工作台。

[English](README.md) | 中文

## 长什么样

`java -cp … io.harnessengineering.app.HarnessApplication` 启动 Tomcat，在 `http://127.0.0.1:8080/` 提供浏览器工作台：

- **会话侧栏** — 列出、创建与切换会话。
- **对话头部** — `Context` 弹窗（占用条、构成、会话累计用量、逐请求台账）与 `Session log`（原始事件、JSONL 导出）。
- **实时对话** — 用户/助手消息、流式 `assistant/chunk` 更新、工具卡片、turn/step 标记，全部走同一条 SSE 流。
- **Composer** — 发送框 + 由投影端点驱动的上下文占用圆环。

## 仓库布局

```
harness-engineering/
├── harness-core/          框架无关的核心运行时（core、session、llm、tools、agent、config、projection、http、cli）
├── harness-spring-app/    Spring Boot 组装层（Tomcat、Spring MVC、SSE；依赖核心，方向不反转）
├── docs/                  架构、术语表、开发、测试、cookbook（中文 + English）
├── AGENTS.md              Agent/贡献者指引
├── PLAN.md / GUIDE.md     路线图与实施指导书（中文）
└── pom.xml                多模块父工程
```

## 前置条件

- JDK 21 或更高
- Maven 3.9 或更高

## 验证

```powershell
mvn "-Dmaven.repo.local=.m2" test
mvn "-Dmaven.repo.local=.m2" -q package
```

（使用工作区本地 `.m2` 仓库；PowerShell 中 `-D` 参数必须加引号。）

## 运行 Session Workbench

```powershell
mvn "-Dmaven.repo.local=.m2" -q package
$cp = "harness-spring-app\target\harness-spring-app-0.1.0-SNAPSHOT.jar;harness-core\target\harness-core-0.1.0-SNAPSHOT.jar" + ((Get-ChildItem .m2 -Recurse -Filter *.jar | Where-Object { $_.FullName -notmatch '\\slf4j-api\\1\.7' } | ForEach-Object { ";$($_.FullName)" }) -join "")
java -cp $cp io.harnessengineering.app.HarnessApplication
```

打开 `http://127.0.0.1:8080/`，点 **新会话**，发送一条消息——演示 Agent 会调用 `harness_current_time` 工具作答，整个回合实时流入对话。

## HTTP API

```text
GET  /sessions                  会话摘要（id、事件数、最后事件时间）
POST /sessions                  创建会话 -> {id}
GET  /sessions/{id}             已提交事件
GET  /sessions/{id}/messages    派生的模型消息
GET  /sessions/{id}/projection  上下文压力 / 构成 / 用量 / 请求台账
POST /sessions/{id}/messages    发送 {content}；Agent 回合经 SSE 回流
GET  /sessions/{id}/stream      SSE：先重放，再跟随实时事件
GET  /                          浏览器客户端
```

核心内还提供基于 JDK `HttpServer` 的框架无关 HTTP/SSE 变体（只读）。

## CLI

```powershell
$cp = "harness-core\target\harness-core-0.1.0-SNAPSHOT.jar" + ((Get-ChildItem .m2 -Recurse -Filter *.jar | ForEach-Object { ";$($_.FullName)" }) -join "")
java -cp $cp io.harnessengineering.cli.HarnessCli list .sessions
java -cp $cp io.harnessengineering.cli.HarnessCli append .sessions demo user "Hello"
java -cp $cp io.harnessengineering.cli.HarnessCli replay .sessions demo
```

## 文档

- [docs/architecture.zh.md](docs/architecture.zh.md) — 模块、核心机制、Agent 循环、投影、Web 层。
- [docs/glossary.zh.md](docs/glossary.zh.md) — 术语。
- [docs/development.zh.md](docs/development.zh.md) — 构建/运行/贡献规则。
- [docs/testing.zh.md](docs/testing.zh.md) — 测试策略。
- [docs/cookbook/](docs/cookbook/) — 新增 Tool、LLM 适配器、Web 界面。

## 范围

运行时包含：带插件生命周期 Fiber 的类型化服务注册表；YAML 插件组合；追加式会话事件日志（内存 + JSONL，原子持久化）；供应商中立 LLM 流式与带并行/可取消/可重试工具的 Agent 回合循环；会话投影（压力/构成/用量/台账）；CLI；框架无关 HTTP/SSE 服务器；以及服务 Session Workbench 的 Spring Boot 组装。