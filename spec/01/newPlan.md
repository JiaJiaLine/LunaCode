# LunaCode TUI 对话内核 NewPlan

## 架构概览

本章的新方案把 TUI 对话内核定义为 LunaCode 的基础交互壳层：它负责终端界面、对话历史、Provider 流式事件和状态展示，但不承载工具执行、Agent Loop、Prompt 结构化或权限策略。后续章节可以复用这些基础能力，但不能把后续能力反向塞回 TUI 内核。

当前总体包边界中，第一章主要涉及：

- `app`：程序入口和依赖装配。
- `config`：读取 Provider、thinking、agent 等配置；第一章只依赖 Provider 基础字段。
- `tui`：终端 UI，负责输入框、对话区、状态栏和刷新。
- `orchestrator`：TUI 与后端能力之间的适配层；当前完整系统中会桥接 AgentEvent，但第一章语义上只要求它能提交用户输入并更新 UI 状态。
- `conversation`：内部消息、API 消息、内容块、消息状态和并发安全快照。
- `provider`：OpenAI / Anthropic 请求实现与 Prompt 请求体适配。
- `stream`：SSE 解析和 Provider 原始流事件到统一事件的映射。

第一章的职责边界是：TUI 不直接依赖 Provider 原始事件，不直接管理工具，也不判断 Agent Loop 是否继续。它只消费对话快照和状态快照。

## 核心设计

### 对话数据

`conversation` 是消息状态的唯一事实来源。

- `InternalMessage` 用于 UI 渲染，包含 id、role、status、content、usage、errorSummary。
- `ApiMessage` 用于 Provider 请求，表达 role 和 `ContentBlock` 列表。
- `ContentBlock` 支持 text、tool_use、tool_result，保证后续工具系统可以复用同一套历史格式。
- `TokenUsage` 表示 token 与缓存 usage；第一章只要求普通 input/output/total 可展示，缓存字段由第四章扩展。

### 流式事件

`stream` 负责把不同 Provider 的 SSE 映射成统一 `StreamEvent`。

- Anthropic：识别 message_start、content_block_start、content_block_delta、content_block_stop、message_delta、message_stop。
- OpenAI：识别 chunk 文本增量、finish reason、usage 和 `[DONE]`。
- 解析失败或 Provider 错误产出 `StreamEvent.Error`，由上层把当前 assistant 消息置为 error。

### TUI 与状态

`tui` 只做展示和输入采集。

- 对话区从 `ConversationManager.snapshot()` 获取不可变快照。
- 状态栏从 `StatusSnapshot` 获取 provider、model、token、状态和工具摘要。
- 用户输入提交给 `ChatOrchestrator`，TUI 不知道 Provider、PromptBundle、工具执行细节。

## 实现方案

1. `LunaCodeApplication` 启动时读取配置，创建 `ConversationManager`、`ChatProvider`、工具注册中心、`DefaultChatOrchestrator` 和 `LanternaLunaTui`。
2. 用户输入由 `LanternaLunaTui` 提交给 `ChatOrchestrator.submitUserMessage`。
3. 普通文本对话在当前完整系统中会进入 Agent Loop，但 UI 层仍只观察 conversation/status，不关心是否经过 Agent。
4. Provider 流式返回文本时，文本增量追加到当前 assistant 消息；完成时状态变为 complete；失败时状态变为 error。
5. 状态栏持续展示 provider/model/usage/state。usage 缺失时显示未知，不让 UI 失败。

## 文件组织

```text
src/main/java/com/lunacode/
├── app/
├── config/
├── conversation/
├── orchestrator/
├── provider/
├── stream/
└── tui/
```

第一章不新增 `agent`、`tool`、`prompt` 的职责；这些包属于后续章节。

## 测试与验收

- `DefaultConversationManagerTest`：验证消息添加、追加、状态流转、快照不可变和 API 格式转换。
- `SseParserTest`、`AnthropicStreamMapperTest`、`OpenAiStreamMapperTest`：验证 SSE 和流事件映射。
- `ConfigLoaderTest`、`ChatProviderFactoryTest`：验证配置和 Provider 创建。
- `DefaultChatOrchestratorTest`：验证普通输入、响应状态和重复提交保护。
- `InputLineBufferTest`：验证 TUI 输入缓冲基础行为。
- 全量回归：运行 `mvn test`。

