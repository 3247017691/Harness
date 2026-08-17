# 开发指南

HarnessEngineering 的构建、运行与贡献指引。改动核心前请先读 [architecture.md](architecture.zh.md)、[glossary.md](glossary.zh.md) 与 [AGENTS.md](../AGENTS.md)。

## 前置条件

- JDK 21 或更高（当前用 Java 25.0.2 运行时构建）。
- Maven 3.9 或更高。
- （仅下载依赖时）网络需要代理时使用 `http://127.0.0.1:7897`。

## 构建与测试

所有 Maven 命令在工作区根目录执行，并显式使用工作区本地仓库（PowerShell 中 `-D` 参数必须加引号）：

```powershell
mvn "-Dmaven.repo.local=.m2" test
mvn "-Dmaven.repo.local=.m2" -q package
```

`test` 运行两个模块：`harness-core` 单元测试与 `harness-spring-app` 集成测试（真实 Tomcat 启动 + SSE 读取）。覆盖门槛：核心套件保持全绿；每个行为改动都要新增或更新对应的 owner 测试。

## 运行应用

打包并把类路径组装起来（spring jar、core jar，以及除 surefire 缓存的过期 `slf4j-api/1.7` 之外的所有 `.m2` jar）：

```powershell
mvn "-Dmaven.repo.local=.m2" -q package
$cp = "harness-spring-app\target\harness-spring-app-0.1.0-SNAPSHOT.jar;harness-core\target\harness-core-0.1.0-SNAPSHOT.jar" + ((Get-ChildItem .m2 -Recurse -Filter *.jar | Where-Object { $_.FullName -notmatch '\\slf4j-api\\1\.7' } | ForEach-Object { ";$($_.FullName)" }) -join "")
java -cp $cp io.harnessengineering.app.HarnessApplication
```

默认配置（`harness-spring-app/src/main/resources/application.yml`）：端口 `8080`，会话目录 `.sessions`。打开 `http://127.0.0.1:8080/` 查看 Session Workbench。

CLI（仅核心）：

```powershell
$cp = "harness-core\target\harness-core-0.1.0-SNAPSHOT.jar" + ((Get-ChildItem .m2 -Recurse -Filter *.jar | ForEach-Object { ";$($_.FullName)" }) -join "")
java -cp $cp io.harnessengineering.cli.HarnessCli append .sessions demo user "Hello"
java -cp $cp io.harnessengineering.cli.HarnessCli replay .sessions demo
java -cp $cp io.harnessengineering.cli.HarnessCli list .sessions
```

## 贡献规则

- **依赖方向**：核心永不导入 Spring 或 Web 类；新框架特性放在 `harness-spring-app`。
- **注册即副作用**：贡献通过返回幂等 disposer 的 API 注册；插件卸载逆序执行 disposer。
- **模型可见 ⟺ 已记录**：模型请求看到的任何输入必须能从 Session 日志重建；新增模型可见输入时要新增会话事件类型。
- **Session 不变量**：append = 校验 → 持久化 → 通知；持久化失败绝不发布成功；序号严格递增。
- **并发**：长操作需要 owner、取消令牌/Future、完成等待点与确定性的关闭顺序（停止接收输入 → 取消请求 → 等待流结束 → 停止工具 → 关闭 Agent → 从注册表移除）。不要在 `shutdownNow()` 后立即丢弃对象——它们的结果可能仍在发布。
- **非平凡的改动要写 Agent Note**（记录在 `docs/` 下的决策记录）；机械性小改动除外。
- **测试描述行为**而非实现；行为改变时在同一个改动里修改其测试。
- **判别字段用 switch 穷举**；闭合联合以显式分支收尾，不允许静默穿透。
- **清理错误要收集而不是吞掉**；一个 disposer 失败不能阻止后续 disposer 执行。

## 本工作区环境说明

- 本地 Maven 仓库是工作区内的 `.m2/`（默认用户 `.m2` 不可写）；始终带引号传 `-Dmaven.repo.local=.m2`。
- surefire 可能把过期的 `slf4j-api/1.7.x` 放进 `.m2`；应用类路径里要过滤掉它（见上面启动命令）。
- 测试隔离：Spring 测试以命令行参数（最高优先级）传 `--harness.session-dir=<temp>` 与 `--server.port=0`，绝不触碰真实的 `.sessions/`。
- 代理仅当前会话生效；绝不把代理配置或令牌写进仓库。

## 文档

中英成对：英文在 `*.md`，简体中文在 `*.zh.md`，逐节保持同步。docs 树与代码结构对应（`docs/architecture.md`、`docs/glossary.md`、`docs/development.md`、`docs/testing.md`、`docs/cookbook/`）。