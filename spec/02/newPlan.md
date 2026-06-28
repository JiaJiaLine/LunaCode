# LunaCode 工具系统 NewPlan

## 架构概览

本章的新方案继续把工具系统放在独立 `tool` 包中。工具系统只负责工具定义、工具注册、工具执行、路径边界、权限元信息、输出脱敏和工具结果结构化，不依赖 Agent Loop。

重构后的关键边界是：

- `tool` 可以依赖 `runtime.AgentMode`，用于按模式输出工具声明或做权限判断。
- `tool` 可以依赖 `interaction.UserQuestionBroker`，用于 Plan Mode 的 AskUserQuestion。
- `tool` 禁止依赖 `agent` 包；工具不应该知道循环、轮次、事件流和 UI 桥接。
- Provider 只接收工具声明，不直接执行工具。
- Conversation 只保存 tool_use/tool_result 内容块，不执行工具。

## 模块职责

### tool

`tool` 是本章核心模块，包含：

- `Tool`：工具统一接口，暴露名称、描述、JSON Schema、执行、校验、只读性、副作用和并发安全性。
- `ToolRegistry` / `DefaultToolRegistry`：注册、启用/禁用工具，并按 `AgentMode` 输出模型可见工具声明。
- `ToolExecutor` / `DefaultToolExecutor`：查找工具、校验参数、执行工具、包装普通失败。
- `ToolExecutionContext`：提供 workspaceRoot、命令超时、最大输出、敏感值脱敏器和 `UserQuestionBroker`。
- `ToolPermissionGateway` / `DefaultToolPermissionGateway`：集中判断 ALLOW / ASK / DENY。
- `ToolBatchPlanner`：按只读性、副作用和并发安全性为第三章 Agent Loop 提供批次规划。
- 六个核心工具：ReadFile、WriteFile、EditFile、Bash、Glob、Grep。
- Plan Mode 工具：AskUserQuestion。

### conversation

`conversation` 支持 Claude 兼容内容块。

- assistant 消息可以同时包含 text 和多个 tool_use。
- user 消息可以包含多个 tool_result。
- tool_use 和 tool_result 必须通过 id 配对。
- metadata 不进入发送给模型的消息内容。

### stream

`stream` 负责解析 Claude 流式 tool_use。

- `ToolUseBuffer` 收集 `input_json_delta.partial_json`。
- 参数拼接完成后产出 `StreamEvent.ToolUse`。
- JSON 解析失败是流解析错误，不进入工具执行。

## 实现方案

1. 启动时由 `LunaCodeApplication` 注册内置工具，并创建 `DefaultToolExecutor`。
2. Provider 调用前通过 `ToolRegistry.toAPIFormat(mode)` 获取当前启用工具声明。
3. 模型返回 tool_use 时，`stream` 解析工具名、调用 id 和 JSON 参数。
4. 单次工具执行由 `ToolExecutor` 完成；工具普通失败统一返回 `ToolResult.isError=true`。
5. `ToolResult.content` 回灌给模型，`metadata` 只给 UI 或测试使用。
6. 文件类工具统一通过 `WorkspacePathResolver` 限制工作区边界。
7. 命令工具统一由 `BashTool` 执行，并返回退出码、stdout、stderr、超时状态和耗时 metadata。

## 工具行为

- `ReadFile`：读取工作区文本文件，支持 `offset` / `limit`，返回带原始行号的内容。
- `WriteFile`：递归创建父目录，写入完整内容，POSIX 下尽量设置目录 0755、文件 0644。
- `EditFile`：基于 `old_text` 唯一匹配替换；0 次或多次匹配都不修改文件并返回结构化错误。
- `Glob`：支持 `**` 递归匹配，结果按相对路径排序。
- `Grep`：返回文件路径、行号、列号和匹配摘要，跳过明显二进制与构建产物目录。
- `Bash`：在工作区根目录执行，支持超时和敏感值脱敏。
- `AskUserQuestion`：通过 `interaction.UserQuestionBroker` 向 UI 提问，只用于 Plan Mode 澄清需求。

## 文件组织

```text
src/main/java/com/lunacode/
├── tool/
├── conversation/
├── stream/
├── runtime/
└── interaction/
```

本章明确不把工具执行逻辑放入 `agent`；第三章只从 `tool` 调用能力。

## 测试与验收

- `ToolRegistryTest`：注册、启停、声明生成。
- `ReadFileToolTest`、`WriteFileToolTest`、`EditFileToolTest`、`BashToolTest`、`GlobToolTest`、`GrepToolTest`：覆盖六个核心工具。
- `AskUserQuestionToolTest`：覆盖 broker 调用、用户回答包装和错误处理。
- `ToolMessageFormatTest`：验证 tool_use/tool_result 历史格式。
- `ClaudeToolUseStreamMapperTest`：验证参数碎片拼接和解析错误。
- `ToolPermissionGatewayTest`：验证权限矩阵和 plan file 放行。
- `PackageDependencyTest`：验证 `tool` 不依赖 `agent`。
- 全量回归：运行 `mvn test`。

