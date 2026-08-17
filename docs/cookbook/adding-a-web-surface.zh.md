# Cookbook：新增 Web 界面

如何扩展 Session Workbench——位于 `harness-core/src/main/resources/web/index.html` 的浏览器客户端，新增控件、弹窗或实时视图。该页面是单个自包含 HTML 文件（原生 JS + CSS，无构建步骤），由 Spring MVC 适配层和框架无关的 `HarnessHttpServer` 从 classpath 提供服务。

## 1. 理解数据流

- 页面经 REST API 读取会话状态，**所有**实时更新走同一条 SSE 流（`GET /sessions/{id}/stream`）：连接时重放日志，随后跟随追加事件。
- 每个事件携带 `{sequence, time, type, data}`。渲染器按 `type` 分派：`user/message`、`assistant/chunk`、`assistant/message`、`tool/call`、`tool/result`、`turn/start`、`step/start`、`step/end`、`turn/end`。
- 派生/聚合数据来自专用端点：`GET /sessions/{id}/messages`（模型消息）、`GET /sessions/{id}/projection`（压力/构成/用量/台账）。

## 2. 选择你的界面

- **新头部控件**——在头部操作区（`Context` / `Session log` 旁）加一个 `.capsule` 按钮，并在 IIFE 里绑定。
- **新弹窗**——复用 `#modal-root` 外壳：设置标题/描述，把 HTML 放进 `#modal-body`，用 `openModal`/`closeModal` 开关。`Escape` 与遮罩可关闭。
- **新实时视图**——扩展 `renderEvent` 的 `switch`（或端到端新增事件类型：核心事件常量 → 校验器 → 渲染器）。
- **新 composer 元素**——composer 行已容纳上下文仪表；在 `#meterInput` 前加相邻元素。

## 3. 用设计 token 写样式

使用 `:root` 中声明的 CSS 变量（`--bg`、`--bg-raised`、`--border`、`--text*`、`--accent`、`--tint-*`），不要写死颜色，深色主题的一致性自动成立。

## 4. 测试

- **行为**：Spring 集成测试断言页面被服务且包含 workbench 标记；新的静态控件就补对应断言。
- **事件渲染**依赖服务端事件——用公开 API 驱动会话（如追加事件，断言 SSE 或 REST 响应）来覆盖新渲染路径，不要在单元测试里跑浏览器。
- **保持页面框架无关**：无构建步骤、无外部 CDN 脚本；workbench 必须能从 classpath 离线工作。

## 5. 常见坑

- **XSS**：注入 HTML 前用 `esc()` 转义用户/工具内容；返回字符串并用于 `innerHTML` 的格式化函数必须转义。
- **SSE 重放缓冲**：连接时流会重放整个日志；客户端应把任何事件视为增量且幂等——聚合 UI 只以投影端点为来源，不要仅凭重放事件重复渲染。
- **本项目没有 Playwright 黄金快照**（Windows CI 差异不适用），但仍要保持 `git diff --check` 干净、文件以一个换行结尾。