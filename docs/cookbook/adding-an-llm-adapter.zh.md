# Cookbook：新增 LLM 适配器

如何把一个真实（或模拟）模型接入 harness。核心是供应商中立的：适配器就是实现 `LlmProvider` 的一个类。

## 1. 实现 Provider

```java
public final class MyProvider implements LlmProvider {
    @Override
    public String providerId() {
        return "my-provider";
    }

    @Override
    public Stream<StreamChunk> stream(LlmRequest request) {
        // request：provider id、model、List<Message>、List<ToolDefinition>
        // 按序流式输出 chunk：
        //   StreamChunk.text("partial")           -> assistant/chunk
        //   StreamChunk.toolCall(id, name, args)  -> 工具调用（结束本次流）
        //   StreamChunk.completed()               -> 显式结束标记
        return Stream.of(StreamChunk.text("answer"));
    }
}
```

规则：

- `stream` 返回**有限**流；writer 用 `chunks.toList()` 物化。
- `toolCall` chunk 本身就是响应终点——之后由 Agent 循环执行收集到的调用。
- Provider 流运行在 Agent 的虚拟线程上，由 `LlmSessionWriter` 消费；取消经管线表达（当前 `stream` 缝不含 token）。

## 2. 记录 Provider 标识

`LlmSessionWriter` 把请求中的 `providerId` 与 `model` 印到组装后的 `assistant/message` 事件上，投影的请求台账读取它们。界面还要展示的任何东西，必须在同一个改动里加进事件数据。

## 3. 接入

在 Spring 组装里，创建 Agent 处使用你的 Provider：

```java
Agent agent = new Agent(session, new MyProvider(), "my-model", new ToolPipeline(tools));
```

要让每个会话都能对话，替换 `AgentHost` 中的 `DemoProvider`，或自行构造宿主。

## 4. 测试

- **Writer 测试**（`LlmSessionWriterTest` 风格）：逐 chunk 断言 `assistant/chunk`、组装后的 `assistant/message`（含 provider/model），以及按流序提取工具调用。
- **Agent 往返测试**（`AgentTest` 风格）：Provider 先发工具调用再发最终文本，产生 `tool/call` → `tool/result` → 后续 step；结果以 `tool` 消息到达模型。
- **投影测试**：用 stub 会话断言请求台账记录 `providerId`/`model`/输入/输出。

## 备注

- 浏览器流式：Session Workbench 实时渲染 `assistant/chunk`，因此 chunk 粒度细的 Provider 无需改 Web 就有流式体验。
- 保持核心不依赖 HTTP/SDK：传输放在适配器里；核心只看到 `LlmRequest`/`StreamChunk`。