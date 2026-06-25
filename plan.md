# LunaCode TUI 对话内核 Plan

## 架构概览

LunaCode 采用分层架构：入口层负责启动和装配配置；TUI 层负责终端界面、输入框、对话区域和状态栏；应用编排层负责把用户输入、对话管理器和 Provider 串起来；对话域层负责消息模型、状态流转、并发安全和 API 格式转换；Provider 层负责 OpenAI 与 Anthropic 的 HTTP 请求、SSE 解析和统一流事件映射。

TUI 使用 Java 终端 UI 库 Lanterna。它是纯 Java 文本 GUI 库，适合构建上方对话区、下方输入框、底部状态栏这种终端布局。网络层使用 Java HTTP 客户端直接读取 SSE 流，减少额外封装，方便精确处理 Anthropic 的事件生命周期和 OpenAI 的 chunk 流。

核心数据流是：用户在 TUI 输入消息 -> 应用编排层把 user 消息加入对话管理器 -> 创建 streaming 状态的 assistant 消息 -> Provider 使用 `ConversationManager.toAPIFormat()` 生成 API 层消息并发起流式请求 -> Provider 将原始流事件映射成内部 `StreamEvent` -> 编排层按 assistant 消息 ID 追加内容、更新 usage/status -> TUI 周期性拿消息快照和状态快照刷新界面。

模块划分如下：

- `app`：程序入口、依赖装配、生命周期管理。
- `config`：读取和校验 YAML 配置。
- `tui`：终端 UI 布局、输入处理、渲染和状态栏展示。
- `conversation`：内部消息模型、API 消息模型、对话管理器、并发安全快照。
- `provider`：统一 Provider 接口、OpenAI Provider、Anthropic Provider。
- `stream`：统一 `StreamEvent` 模型、SSE 解析、Provider 原始事件到内部事件的映射。
- `orchestrator`：协调 TUI 输入、Provider 调用、消息更新和错误处理。

## 核心数据结构

### MessageRole

```java
enum MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}
```

表示消息角色。本阶段 API 层只发送 `user` 和 `assistant`；`system` 和 `tool` 可以存在于内部层，但 `toAPIFormat()` 会过滤掉它们，避免把本阶段不支持的消息类型发送给 LLM。

### MessageStatus

```java
enum MessageStatus {
    STREAMING,
    COMPLETE,
    ERROR
}
```

表示内部消息状态。用户消息通常创建后即为 `COMPLETE`；assistant 消息流式开始时为 `STREAMING`，正常结束为 `COMPLETE`，异常为 `ERROR`。

### TokenUsage

```java
record TokenUsage(
    Integer inputTokens,
    Integer outputTokens,
    Integer totalTokens
) {}
```

表示 Token 用量。字段允许为空，表示 Provider 尚未返回或不支持对应统计。

### InternalMessage

```java
record InternalMessage(
    String id,
    MessageRole role,
    MessageStatus status,
    Instant timestamp,
    TokenUsage usage,
    String content,
    String errorSummary
) {}
```

内部层消息，用于 TUI 渲染和状态管理。`id` 唯一；`errorSummary` 只在 `ERROR` 状态下使用。对外快照返回不可变副本，避免调用方修改内部状态。

### ApiMessage

```java
record ApiMessage(
    String role,
    String content
) {}
```

API 层消息只包含 `role + content`。本阶段 `role` 只允许 `user` 和 `assistant`，后续实现 tool use 时再扩展 API 格式。

### StreamEvent

```java
sealed interface StreamEvent permits
    StreamEvent.MessageStart,
    StreamEvent.ContentBlockStart,
    StreamEvent.ContentDelta,
    StreamEvent.ContentBlockStop,
    StreamEvent.MessageDelta,
    StreamEvent.MessageStop,
    StreamEvent.Error {
}
```

Provider 层输出的统一内部流事件。OpenAI 和 Anthropic 原始流式响应都映射到它，TUI 和编排层不直接消费原始 API 事件。

核心事件：

```java
record MessageStart(TokenUsage usage) implements StreamEvent {}
record ContentBlockStart(int index, String type) implements StreamEvent {}
record ContentDelta(int index, String text) implements StreamEvent {}
record ContentBlockStop(int index) implements StreamEvent {}
record MessageDelta(TokenUsage usage, String stopReason) implements StreamEvent {}
record MessageStop(TokenUsage usage) implements StreamEvent {}
record Error(String summary, Throwable cause) implements StreamEvent {}
```

### ProviderConfig

```java
record ProviderConfig(
    String protocol,
    String model,
    URI baseUrl,
    String apiKey,
    ThinkingConfig thinking
) {}
```

表示 YAML 配置解析后的模型后端配置。`protocol` 支持 `openai` 和 `anthropic`；`thinking` 仅 Anthropic 使用。

### ThinkingConfig

```java
record ThinkingConfig(
    boolean enabled,
    Integer budgetTokens
) {}
```

表示 Claude extended thinking 配置。关闭时不向 Provider 请求体写入 thinking 参数。

## 核心接口

### ConversationManager

```java
interface ConversationManager {
    String addMessage(MessageRole role, String content);
    String addStreamingAssistantMessage();
    void appendContent(String messageId, String delta);
    void completeMessage(String messageId, TokenUsage usage);
    void failMessage(String messageId, String errorSummary);
    List<InternalMessage> snapshot();
    List<ApiMessage> toAPIFormat();
}
```

职责：

- 封装内部消息列表。
- 保证并发安全。
- 添加消息时返回唯一 ID。
- 按 ID 追加流式内容。
- 渲染时返回不可变快照。
- 把内部消息转换为 API 层格式。

`toAPIFormat()` 规则：

1. 过滤 `SYSTEM` 消息。
2. 过滤 `TOOL` 消息。
3. 过滤 `ERROR` 消息。
4. 只保留 `USER` 和 `ASSISTANT` 角色。
5. 合并相邻同角色消息，内容用空行连接。
6. 丢弃开头不是 `USER` 的消息，确保首条为 user。
7. 合并后再次保证 user 和 assistant 交替出现。
8. 输出只包含 `role + content`。

编排层在创建 streaming assistant 占位消息之前调用 `toAPIFormat()`，避免把空的 streaming assistant 消息发送给 Provider。

### ChatProvider

```java
interface ChatProvider {
    Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config);
}
```

职责：

- 接收 API 层消息和 Provider 配置。
- 发起 OpenAI 或 Anthropic 流式请求。
- 解析 SSE。
- 输出统一 `StreamEvent`。

### ChatOrchestrator

```java
interface ChatOrchestrator {
    void submitUserMessage(String content);
}
```

职责：

- 接收 TUI 输入。
- 添加 user 消息。
- 调用 `toAPIFormat()`。
- 创建 streaming assistant 消息。
- 消费 Provider 的 `StreamEvent`。
- 按消息 ID 追加内容、更新 usage、完成状态或错误状态。
- 通知 TUI 刷新。

### LunaTui

```java
interface LunaTui {
    void start();
    void render(List<InternalMessage> messages, StatusSnapshot status);
    void showFatalError(String summary);
}
```

职责：

- 创建对话区域、输入框和状态栏。
- 把用户输入交给 `ChatOrchestrator`。
- 根据消息快照和状态快照刷新界面。

## 模块设计

### app

**职责：** 程序启动入口和依赖装配。  
**对外接口：** `LunaCodeApplication.run(String[] args)`。  
**依赖：** `config`、`conversation`、`provider`、`orchestrator`、`tui`。

启动流程：

1. 解析命令行参数，确定配置文件路径。
2. 读取 YAML 配置并校验必需字段。
3. 根据 `protocol` 创建对应 `ChatProvider`。
4. 创建线程安全的 `ConversationManager`。
5. 创建 `ChatOrchestrator`。
6. 启动 `LunaTui`。

### config

**职责：** 读取 YAML 配置、校验字段、转换为 `ProviderConfig`。  
**对外接口：** `ConfigLoader.load(Path path)`。  
**依赖：** Jackson YAML 或等价 YAML 解析库。

配置示例：

```yaml
protocol: anthropic
model: claude-sonnet-4-20250514
base_url: https://api.anthropic.com
api_key: ${ANTHROPIC_API_KEY}
thinking:
  enabled: true
  budget_tokens: 4096
```

规则：

- `protocol` 只能是 `openai` 或 `anthropic`。
- `model`、`base_url`、`api_key` 必填。
- `api_key` 支持直接写值或环境变量占位。
- 校验失败时返回可理解错误，不打印密钥原文。

### conversation

**职责：** 管理内部消息列表、并发安全、消息状态流转和 API 格式转换。  
**对外接口：** `ConversationManager`。  
**依赖：** Java 并发工具。

实现策略：

- 使用 `ReentrantReadWriteLock` 或 `synchronized` 保护消息列表。
- 内部保存可变消息对象，快照时转换为不可变 `InternalMessage`。
- `appendContent()` 只允许更新存在的 assistant streaming 消息。
- `completeMessage()` 把目标消息状态改为 `COMPLETE` 并合并 usage。
- `failMessage()` 把目标消息状态改为 `ERROR` 并记录错误摘要。
- `snapshot()` 返回不可变列表。
- `toAPIFormat()` 在锁内基于当前消息生成新的 API 消息列表。

### stream

**职责：** SSE 行解析、事件聚合、原始 Provider 事件到 `StreamEvent` 的映射辅助。  
**对外接口：** `SseParser`、`AnthropicStreamMapper`、`OpenAiStreamMapper`。  
**依赖：** JSON 解析库。

处理规则：

- 识别 SSE 的 `event:` 和 `data:` 行。
- 空行表示一个 SSE 事件结束。
- `[DONE]` 作为 OpenAI 流结束标记。
- JSON 解析失败时产生 `StreamEvent.Error`。
- Anthropic 事件类型按原始事件名映射。
- OpenAI chunk 映射为统一的 message/content/message_stop 事件序列。

### provider

**职责：** Provider 统一接口和两家后端实现。  
**对外接口：** `ChatProvider`。  
**依赖：** `stream`、Java HTTP client、JSON 解析库。

#### AnthropicProvider

- 请求 `/v1/messages`。
- 请求头包含 API Key 和 Anthropic 版本。
- 请求体包含 `model`、`messages`、`stream: true`。
- `thinking.enabled` 为 true 时加入 thinking 配置。
- 解析事件：`message_start`、`content_block_start`、`content_block_delta`、`content_block_stop`、`message_delta`、`message_stop`。
- 把 usage 和 stop reason 映射到 `StreamEvent`。

#### OpenAiProvider

- 请求兼容 OpenAI Chat Completions 或 Responses 的流式接口，具体路径由配置或默认值决定。
- 请求头包含 Bearer API Key。
- 请求体包含 `model`、`messages`、`stream: true`。
- 开启 usage 统计请求选项，能拿到 usage 时更新状态栏。
- 把 chunk 中的文本增量映射为 `ContentDelta`。
- 收到结束标记时产生 `MessageStop`。

### orchestrator

**职责：** 把用户输入、对话管理器、Provider 流和 TUI 刷新连接起来。  
**对外接口：** `ChatOrchestrator`。  
**依赖：** `conversation`、`provider`、`config`、`tui`。

流程：

1. 收到用户输入。
2. 空输入直接忽略。
3. 添加 user 消息，获得 user ID。
4. 调用 `toAPIFormat()` 生成请求消息。
5. 添加 streaming assistant 消息，获得 assistant ID。
6. 在后台线程调用 Provider。
7. 遇到 `ContentDelta` 时按 assistant ID 追加内容。
8. 遇到 usage 事件时更新 assistant 消息和状态栏。
9. 遇到 `MessageStop` 时完成 assistant 消息。
10. 遇到异常或 `StreamEvent.Error` 时把 assistant 消息标记为 error。
11. 每次状态变化后通知 TUI 刷新。

### tui

**职责：** 终端 UI 布局、输入、渲染和状态栏。  
**对外接口：** `LunaTui`。  
**依赖：** Lanterna、`orchestrator`、`conversation`。

布局：

- 上方对话区域：滚动显示消息列表。
- 下方输入框：接收用户输入，回车提交。
- 底部状态栏：展示 Provider、model、输入 Token、输出 Token、响应状态。

规则：

- Provider 流式读取不在 UI 线程执行。
- TUI 通过消息快照渲染，不直接读写内部消息列表。
- 终端尺寸变化时重新布局。
- 错误消息以可读文本展示，不泄露 API Key。

## 模块交互

### 启动流程

```text
Main
  -> LunaCodeApplication
  -> ConfigLoader.load(configPath)
  -> ChatProviderFactory.create(providerConfig.protocol)
  -> DefaultConversationManager
  -> DefaultChatOrchestrator
  -> LanternaLunaTui.start()
```

启动失败时：

```text
ConfigLoader 校验失败
  -> LunaCodeApplication 捕获错误
  -> 终端打印可理解错误
  -> 退出，不进入 TUI
```

错误信息不得包含 API Key 原文。

### 用户提交消息

```text
用户在输入框输入内容并回车
  -> LunaTui 调用 ChatOrchestrator.submitUserMessage(content)
  -> ChatOrchestrator 调用 ConversationManager.addMessage(USER, content)
  -> ChatOrchestrator 调用 ConversationManager.toAPIFormat()
  -> ChatOrchestrator 调用 ConversationManager.addStreamingAssistantMessage()
  -> ChatOrchestrator 启动后台 Provider 流式任务
  -> LunaTui 获取 snapshot 并刷新
```

关键点：`toAPIFormat()` 在创建本轮 assistant 占位消息之前调用，避免把空的 streaming assistant 消息发送给模型。

### Provider 流式响应

```text
后台 Provider 任务
  -> ChatProvider.streamChat(apiMessages, providerConfig)
  -> Provider 发起 HTTP 请求
  -> SseParser 解析 event/data
  -> Provider StreamMapper 映射为 StreamEvent
  -> ChatOrchestrator 消费 StreamEvent
```

事件处理：

```text
MessageStart
  -> 更新状态栏 usage/input tokens 和 responding 状态

ContentBlockStart
  -> 记录当前内容块状态，必要时触发刷新

ContentDelta
  -> ConversationManager.appendContent(assistantId, delta.text)
  -> TUI 刷新对话区域

ContentBlockStop
  -> 标记内容块结束，必要时触发刷新

MessageDelta
  -> 更新 output tokens、stop reason、状态栏

MessageStop
  -> ConversationManager.completeMessage(assistantId, finalUsage)
  -> 状态栏切换为空闲/完成
  -> TUI 最终刷新

Error 或异常
  -> ConversationManager.failMessage(assistantId, summary)
  -> 状态栏切换为错误
  -> TUI 刷新
```

### Anthropic 流映射

```text
event: message_start
data: {"message":{"usage":{"input_tokens":...}}}
  -> StreamEvent.MessageStart(inputTokens)

event: content_block_start
data: {"index":0,"content_block":{"type":"text",...}}
  -> StreamEvent.ContentBlockStart(index=0, type="text")

event: content_block_delta
data: {"index":0,"delta":{"type":"text_delta","text":"..."}}
  -> StreamEvent.ContentDelta(index=0, text="...")

event: content_block_stop
data: {"index":0}
  -> StreamEvent.ContentBlockStop(index=0)

event: message_delta
data: {"delta":{"stop_reason":"..."}, "usage":{"output_tokens":...}}
  -> StreamEvent.MessageDelta(outputTokens, stopReason)

event: message_stop
data: {}
  -> StreamEvent.MessageStop(finalUsage)
```

### OpenAI 流映射

```text
首个有效 chunk
  -> StreamEvent.MessageStart(usage unknown)

chunk.choices[0].delta.content 有文本
  -> StreamEvent.ContentBlockStart(index=0, type="text")，仅在第一个文本增量前产生一次
  -> StreamEvent.ContentDelta(index=0, text=delta.content)

chunk.choices[0].finish_reason 非空
  -> StreamEvent.MessageDelta(stopReason=finish_reason)

chunk 中包含 usage
  -> StreamEvent.MessageDelta(usage=...)

data: [DONE]
  -> StreamEvent.ContentBlockStop(index=0)
  -> StreamEvent.MessageStop(finalUsage)
```

### 渲染数据流

```text
ConversationManager.snapshot()
  -> List<InternalMessage>
  -> LunaTui.render(messages, status)
  -> 对话区域按 role/status/content 绘制
  -> 状态栏按 StatusSnapshot 绘制
```

TUI 只读快照，不持有内部可变消息对象。

### 并发模型

```text
UI 线程：
  - 接收键盘输入
  - 触发 submitUserMessage
  - 执行界面刷新

Provider 后台线程：
  - 读取 HTTP SSE 流
  - 解析事件
  - 调用 ConversationManager 更新消息

ConversationManager：
  - 作为消息列表唯一写入点
  - 对 add/append/complete/fail/snapshot/toAPIFormat 统一加锁
```

同一时间只允许一个 Provider 请求处于 streaming 状态。若用户在模型回复期间继续提交新消息，本阶段策略是拒绝提交并在状态栏提示“正在响应中”，避免多条 assistant 流并发写入。

## 文件组织

```text
LunaCode/
├── pom.xml
├── AGENTS.md
├── spec.md
├── plan.md
├── task.md
├── checklist.md
├── config.example.yaml
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/
    │   │       └── lunacode/
    │   │           ├── app/
    │   │           │   ├── Main.java
    │   │           │   └── LunaCodeApplication.java
    │   │           ├── config/
    │   │           │   ├── ConfigLoader.java
    │   │           │   ├── ProviderConfig.java
    │   │           │   └── ThinkingConfig.java
    │   │           ├── conversation/
    │   │           │   ├── ApiMessage.java
    │   │           │   ├── ConversationManager.java
    │   │           │   ├── DefaultConversationManager.java
    │   │           │   ├── InternalMessage.java
    │   │           │   ├── MessageRole.java
    │   │           │   ├── MessageStatus.java
    │   │           │   └── TokenUsage.java
    │   │           ├── orchestrator/
    │   │           │   ├── ChatOrchestrator.java
    │   │           │   ├── DefaultChatOrchestrator.java
    │   │           │   └── StatusSnapshot.java
    │   │           ├── provider/
    │   │           │   ├── AnthropicProvider.java
    │   │           │   ├── ChatProvider.java
    │   │           │   ├── ChatProviderFactory.java
    │   │           │   └── OpenAiProvider.java
    │   │           ├── stream/
    │   │           │   ├── AnthropicStreamMapper.java
    │   │           │   ├── OpenAiStreamMapper.java
    │   │           │   ├── SseEvent.java
    │   │           │   ├── SseParser.java
    │   │           │   └── StreamEvent.java
    │   │           └── tui/
    │   │               ├── LanternaLunaTui.java
    │   │               └── LunaTui.java
    │   └── resources/
    └── test/
        └── java/
            └── com/
                └── lunacode/
                    ├── config/
                    │   └── ConfigLoaderTest.java
                    ├── conversation/
                    │   └── DefaultConversationManagerTest.java
                    └── stream/
                        ├── AnthropicStreamMapperTest.java
                        ├── OpenAiStreamMapperTest.java
                        └── SseParserTest.java
```

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 构建工具 | Maven | Java 项目初始化简单，依赖、测试、打包路径清晰。 |
| Java 版本 | Java 17 | 支持 record、sealed interface 等模型表达，兼顾稳定性。 |
| TUI 库 | Lanterna | 纯 Java、适合终端布局，能实现对话区、输入框、状态栏。 |
| HTTP 客户端 | Java 17 `HttpClient` | 标准库可用，减少依赖，便于直接读取 SSE 流。 |
| YAML 解析 | Jackson YAML | 成熟稳定，可把 YAML 映射到配置对象。 |
| JSON 解析 | Jackson Databind | 与 Jackson YAML 统一生态，适合 Provider 请求/响应解析。 |
| SSE 处理 | 自实现轻量 `SseParser` | 需要精确控制 Anthropic 事件名、OpenAI `[DONE]` 和错误映射。 |
| 消息并发安全 | `ReentrantReadWriteLock` | 读快照频繁、写入集中，读写锁表达清晰。 |
| 流式并发策略 | 单次只允许一个活跃响应 | 避免多轮同时 streaming 导致上下文顺序和 UI 状态混乱。 |
| Provider 抽象 | `ChatProvider -> StreamEvent` | TUI 和编排层只消费内部统一事件，新增 Provider 时影响范围小。 |
| Token 用量 | 以 Provider 返回为准 | 本阶段不实现 tokenizer，避免引入不必要复杂度。 |
| API Key 处理 | 配置读取后只作为请求认证使用 | 不在日志、状态栏、错误文本中输出密钥。 |