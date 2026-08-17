# Cookbook：新增 Tool

如何给 Agent 循环添加一个可调用的能力。工具是具名、带 schema 广告、可执行的单元，演示 Provider 的工具往返即可调用它。

## 1. 实现工具

```java
public final class MyTool implements Tool {
    @Override
    public ToolDefinition definition() {
        ObjectNode parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("type", "object");
        parameters.putObject("properties");   // 在这里声明参数
        parameters.putArray("required");
        return new ToolDefinition("my_tool", "一句话描述，无参数。", parameters);
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext context) {
        // 用 ObjectMapper 解析 call.arguments()，执行后返回：
        return ToolResult.success(call.id(), "结果文本");
        // 失败：ToolResult.failure(call.id(), "error_code", "说明");
    }
}
```

`ToolDefinition` 要求非空名称、描述与深拷贝后的 parameters 节点。`ToolResult` 失败必须带错误码，成功禁止携带。

## 2. 注册

工具归属的注册表决定其生命周期——注册返回 `Effect`：

```java
ToolRegistry registry = new ToolRegistry();
Effect registration = registry.register(new MyTool());
// 卸载时：registration.close();
```

模型看到的 schema 来自 `ToolPipeline.definitions()`（注册表快照）。Spring 组装里，在创建 Agent 的地方注册（`AgentHost` 按 Session 构建演示清单）。

## 3. 用确定性测试驱动

镜像 `AgentTest.toolCallCreatesFollowupStepAndExecutesTool`：stub 一个 Provider，第一次请求发出 `StreamChunk.toolCall("call-1", "my_tool", "{}")`，第二次返回文本；断言 `tool/call` 与 `tool/result` 事件，以及结果以 `tool` 消息进入后续请求。

## 4. 界面展示

`tool/call` 与 `tool/result` 事件会由 Session Workbench 自动渲染为工具卡片（`name`、`arguments`、success/content）。新增工具无需改 Web。

## 备注

- **策略放管线，不放工具**：重排、重试与取消在 `ToolPipeline`/中间件里；工具只执行一次调用。
- **结果确定性**：读取时钟或文件系统的工具，测试时应注入 `Clock`/路径。
- **模型可见输入**：参数与结果作为事件记录；模型还需要的东西也必须成为事件或折叠的 `tool` 消息。