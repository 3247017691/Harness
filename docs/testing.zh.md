# 测试指南

HarnessEngineering 如何测试，以及一个改动必须证明什么。

## 分层

- **`harness-core` 单元测试**（JUnit 5）逐机制覆盖：core（PluginManager、EventBus）、config、session、llm、tools、agent、cli、http、projection。框架无关、速度快。
- **`harness-spring-app` 集成测试**：在随机端口（`--server.port=0`）真实启动 Tomcat，使用临时会话目录，驱动 HTTP 面（事件、消息、投影、创建/发送、SSE）。

## 测试钉住的关键行为

- **生命周期**：缺依赖保持 `PENDING`；提供服务激活 Fiber；替换服务只执行一次 disposer；重复关闭幂等；失败插件保留错误；关闭管理器移除监听器。
- **EventBus**：`emit` 通知全部；`waterfall` 串联 `next()`；不调用 `next()` 即拦截；监听器异常行为确定。
- **Session**：序号严格递增；事件是不可变快照；重放与实时派生一致；非法 envelope 被拒绝；持久化失败绝不发布。`list()` 按名称序枚举持久化 id（JSONL），内存后端枚举已知键。
- **LLM**：记录 chunk 与组装后的 assistant 消息；按流序提取工具调用 chunk；assistant 消息携带 provider/model。
- **Agent**：一条 followup → 一个 turn；工具调用产生后续 step；已执行工具结果作为模型可见 `tool` 消息回灌；取消阻止派发；`close()` 等待当前任务。
- **工具**：并行派发先按请求序记录 call 事件，结果按请求序收敛；重试收敛；预取消令牌永不派发。
- **投影**：空会话产生基线；消息/工具流量折叠进构成、用量与请求台账；无计费输入时缓存命中率为 null。
- **HTTP/SSE**：服务浏览器页面；先重放再实时事件；非法 id 与未知路由返回错误。
- **应用组装**：Tomcat 启动并服务会话状态；会话可枚举、创建、发送（demo Agent 执行一次 `tool/call` → `tool/result` 往返）；投影可服务；SSE 传递实时事件。

## 约定

- **测试描述行为，不描述实现**。行为变化与其测试同步修改，并在改动里说明原因。
- **同步不用 `Thread.sleep`**。用 `CountDownLatch`、`CompletableFuture`、`AtomicInteger`，或带有限尝试次数的短轮询（如 `HarnessApplicationTest` 中的异步回合轮询）。
- **确定性流测试**用有限流 stub `LlmProvider`；取消测试用闩把流保持打开，取消后再释放。
- **测试隔离**：Spring 测试以命令行参数（最高优先级）传 `--harness.session-dir=<temp>` 与 `--server.port=0`，绝不触碰真实的 `.sessions/`。
- **fixture 可跨主机重放**：通过公开 API 构造事件；除非测试 store 本身，不要伪造存储文件。

## 运行

```powershell
mvn "-Dmaven.repo.local=.m2" test
```

只跑单模块：`mvn "-Dmaven.repo.local=.m2" -pl harness-core test`（需先 `install` 保证父 POM 可解析），或直接跑完整 reactor。每次推送前：`mvn test` 全绿、`git diff --check` 干净。