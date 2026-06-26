# LunaCode 工具系统 Plan

## 架构概览

本阶段在第一章已有的 `conversation`、`provider`、`stream`、`orchestrator`、`tui` 分层上增加工具系统。新增 `tool` 模块负责工具接口、工具注册、工具启停、工具执行和六个内置工具；`provider` 模块负责在每次调用 Claude API 前读取当前启用工具列表，并把工具声明组装成 Claude API 要求的 `tools` 格式；`stream` 模块负责解析 Claude 流式 `tool_use` 内容块，把 JSON 参数碎片拼接成完整输入；`conversation` 模块负责支持 Claude 的混合内容块消息；`orchestrator` 模块负责执行工具并把 `tool_result` 作为 user 消息回灌。

核心数据流是：用户提交请求后，编排器把对话历史转换为 Claude 消息格式，并在调用 API 前通过 `ToolRegistry.toAPIFormat()` 获取当前启用工具列表。模型流式返回时，如果是普通文本，继续按第一章的流式文本处理；如果返回 `tool_use` 内容块，流解析器收集 `id`、`name` 和 `input_json_delta` 参数碎片，在 `content_block_stop` 时解析完整 JSON 并发出 `ToolUse` 事件。编排器收到一个或多个 `ToolUse` 后，按工具名执行工具，把所有结果组成同一条 user 消息中的 `tool_result` 内容块，再请求模型生成最终回复。本阶段不做自动 Agent Loop；工具结果回灌后的最终回复请求不再携带工具列表。

模块划分如下：

- `tool`：统一工具接口、工具结果、工具注册中心、工具执行器、工作区路径校验、输出截断、敏感信息脱敏和六个内置工具。
- `provider`：构造 Claude API 请求，调用前读取启用工具的 Claude 格式声明，发送混合内容块消息。
- `stream`：解析 Claude SSE 生命周期事件，特别是 `tool_use` 内容块的开始、参数增量和结束。
- `conversation`：保存 user、assistant、tool_use、tool_result 组成的内容块消息，并保证 tool_use/tool_result 的 id 配对不丢失。
- `orchestrator`：把用户输入、模型流、工具执行、工具结果回灌和最终回复串起来。
- `tui`：展示工具状态和 metadata，例如文件修改时间、命令耗时、是否截断；metadata 不发送给模型。

## 核心数据结构

### Tool

```java
interface Tool {
    String name();
    String description();
    JsonNode inputSchema();
    ToolResult execute(ToolExecutionContext context, JsonNode input);
    boolean isReadOnly();
    boolean isDestructive();
    boolean isConcurrencySafe(JsonNode input);
    String category();
    ValidationError validateInput(JsonNode input);
}
```

说明：
- `name()` 返回模型可见工具名，本阶段固定为 `ReadFile`、`WriteFile`、`EditFile`、`Bash`、`Glob`、`Grep`。
- `description()` 返回给模型看的自然语言描述。
- `inputSchema()` 返回 JSON Schema，用于 Claude API 的工具声明。
- `execute(context, input)` 执行工具，返回 `ToolResult`。
- `isReadOnly()` 标记工具是否只读，例如 `ReadFile`、`Glob`、`Grep`。
- `isDestructive()` 标记工具是否可能修改状态，例如 `WriteFile`、`EditFile`、`Bash`。
- `isConcurrencySafe(input)` 根据输入判断工具是否可以并发执行。本阶段不并行执行工具，但保留该能力给后续 Agent Loop。
- `category()` 返回工具分类，例如 `file`、`shell`、`search`。
- `validateInput(input)` 只做参数校验，返回错误或 `null`。

### ValidationError

```java
record ValidationError(
    String code,
    String message
) {}
```

参数校验失败不是程序级异常，编排器会把它转换成 `ToolResult.isError=true` 的工具结果回传给模型。

### ToolResult

```java
record ToolResult(
    String content,
    boolean isError,
    Map<String, Object> metadata
) {}
```

语义：
- `content` 是发送给模型的文本内容，应当清晰、紧凑、可被模型直接用于下一步推理。
- `isError` 标记这次工具调用是否失败。工具不存在、参数错误、文件不存在、匹配不到、命令超时、命令非零退出等，都应该表现为 `isError=true` 的工具结果，而不是程序级 error。
- `metadata` 只给 UI 层使用，不发送给模型，避免浪费 token。示例包括文件修改时间、文件大小、命令执行耗时、退出码、是否截断、匹配数量。

只有真正系统级错误，例如 JVM 崩溃、内存不足、不可恢复的内部状态损坏，才作为程序级 error 上报。普通工具失败都必须包装成 `ToolResult`，让模型有机会基于结果调整回复。

### ToolExecutionContext

```java
record ToolExecutionContext(
    Path workspaceRoot,
    Duration commandTimeout,
    int maxContentChars,
    SensitiveValueMasker masker
) {}
```

职责：
- 提供工作区根路径。
- 提供命令默认超时。
- 提供返回给模型的 `content` 最大字符数。
- 对工具输出做敏感信息脱敏。

### ToolUse

```java
record ToolUse(
    String id,
    String name,
    JsonNode input
) {}
```

表示 Claude 请求的一次工具调用。`id` 对应 Claude 的 `toolu_xxx`，后续 `tool_result` 必须使用同一个 id 配对。

### ToolUseBuffer

```java
final class ToolUseBuffer {
    String id;
    String name;
    StringBuilder partialJson;
}
```

Claude 流式 tool use 参数按 `input_json_delta.partial_json` 多次到达。流解析器在 `content_block_start` 创建 buffer，在每个 `content_block_delta` 追加 JSON 碎片，在 `content_block_stop` 解析完整 JSON 并发出 `ToolUse` 事件。

### ContentBlock

```java
sealed interface ContentBlock permits
    ContentBlock.Text,
    ContentBlock.ToolUseBlock,
    ContentBlock.ToolResultBlock {

    record Text(String text) implements ContentBlock {}

    record ToolUseBlock(
        String id,
        String name,
        JsonNode input
    ) implements ContentBlock {}

    record ToolResultBlock(
        String toolUseId,
        String content,
        boolean isError
    ) implements ContentBlock {}
}
```

Claude 的一条 assistant 消息可以同时包含 text 和 tool_use 内容块，必须保存在同一条消息里，不能拆成两条。Claude 的 tool_result 放在 user 消息中，也以内容块形式保存。

### ApiMessage

```java
record ApiMessage(
    String role,
    List<ContentBlock> content
) {}
```

`role` 只能是 Claude 支持的 `user` 或 `assistant`。工具结果不是独立 role，而是 `role=user` 消息中的 `tool_result` 内容块。这样可以保持 user/assistant 交替，同时支持工具调用。

### StreamEvent

```java
sealed interface StreamEvent permits
    StreamEvent.MessageStart,
    StreamEvent.TextDelta,
    StreamEvent.ToolUse,
    StreamEvent.MessageStop,
    StreamEvent.Error {

    record MessageStart() implements StreamEvent {}
    record TextDelta(String text) implements StreamEvent {}
    record ToolUse(String id, String name, JsonNode input) implements StreamEvent {}
    record MessageStop() implements StreamEvent {}
    record Error(String summary, Throwable cause) implements StreamEvent {}
}
```

`StreamEvent.Error` 只表示流解析或 Provider 通信层失败。工具执行失败不使用它，而是生成 `ToolResult.isError=true`。

## 核心接口

### ToolRegistry

```java
interface ToolRegistry {
    void register(Tool tool);
    void enable(String name);
    void disable(String name);
    Optional<Tool> get(String name);
    List<Tool> getEnabledTools();
    ArrayNode toAPIFormat();
}
```

职责：
- 注册工具。
- 按名称启用或禁用工具。
- 获取单个工具。
- 获取所有启用工具。
- 每次调用 Claude API 前调用 `toAPIFormat()`，遍历所有启用工具，把 `name()`、`description()`、`inputSchema()` 组装成 Claude API 要求的工具声明格式。

Claude 工具声明格式：

```json
[
  {
    "name": "ReadFile",
    "description": "读取工作区内文本文件，返回带行号的内容。",
    "input_schema": {
      "type": "object",
      "properties": {
        "path": {"type": "string"},
        "offset": {"type": "integer"},
        "limit": {"type": "integer"}
      },
      "required": ["path"]
    }
  }
]
```

### ToolExecutor

```java
interface ToolExecutor {
    ToolResult execute(ToolUse toolUse);
}
```

职责：
- 根据 `ToolUse.name` 从 `ToolRegistry` 获取启用工具。
- 工具不存在或已禁用时返回 `ToolResult.isError=true`。
- 调用 `validateInput(input)`，失败时返回错误工具结果。
- 调用 `tool.execute(context, input)`。
- 捕获普通异常并转换成错误工具结果。
- 只在真正系统级不可恢复错误时向上抛出程序级 error。

### WorkspacePathResolver

```java
interface WorkspacePathResolver {
    Path resolveInsideWorkspace(String requestedPath);
}
```

所有文件类工具必须通过它解析路径。它负责拒绝 `..` 穿越、绝对路径逃逸、符号链接逃逸到工作区外等情况。

### ChatProvider

```java
interface ChatProvider {
    Stream<StreamEvent> streamChat(List<ApiMessage> messages, ArrayNode enabledTools);
}
```

职责：
- 接收 Claude 消息列表。
- 接收 `ToolRegistry.toAPIFormat()` 生成的当前启用工具列表。
- 当 `enabledTools` 为空时，不向 Claude 请求体写入 tools。
- 输出统一 `StreamEvent`。

## 模块设计

### tool

**职责：** 提供统一工具接口、注册中心、执行器、安全边界和六个内置工具。  
**对外接口：** `Tool`、`ToolRegistry`、`ToolExecutor`。  
**依赖：** Jackson、Java NIO、Java Process API。

默认注册工具名：
- `ReadFile`
- `WriteFile`
- `EditFile`
- `Bash`
- `Glob`
- `Grep`

#### ReadFile

参数：

```json
{
  "path": "pom.xml",
  "offset": 1,
  "limit": 200
}
```

规则：
- `path` 必填。
- `offset` 从 1 开始，缺省为 1。
- `limit` 缺省为默认上限，必须大于 0。
- `content` 返回带原始行号的文本，例如 `1\t<project ...>`。
- `metadata` 包含 `path`、`startLine`、`endLine`、`totalLines`、`fileSize`、`lastModifiedTime`、`truncated`。
- `isReadOnly() = true`，`isDestructive() = false`。

#### WriteFile

参数：

```json
{
  "path": "src/example.txt",
  "content": "hello\n"
}
```

规则：
- 写入前校验路径在工作区内。
- 递归创建父目录。
- POSIX 文件系统上父目录权限设置为 `0755`，目标文件权限设置为 `0644`。
- 非 POSIX 文件系统上权限设置采用 best effort，结果写入 `metadata.permissionsApplied`。
- 优先写入临时文件再移动到目标路径，降低半写入风险。
- `content` 返回写入成功摘要。
- `metadata` 包含 `path`、`bytesWritten`、`permissionsApplied`、`lastModifiedTime`。
- `isReadOnly() = false`，`isDestructive() = true`。

#### EditFile

参数：

```json
{
  "path": "src/example.txt",
  "old_text": "hello",
  "new_text": "hello LunaCode"
}
```

规则：
- `old_text` 必须非空。
- 只在 `old_text` 出现一次时替换。
- 出现 0 次返回 `isError=true`，`content` 说明匹配不到。
- 出现多次返回 `isError=true`，`content` 说明匹配不唯一。
- 修改策略复用 `WriteFile` 的路径、父目录和权限处理。
- `metadata` 包含 `path`、`matchCount`、`changed`、`lastModifiedTime`。
- `isReadOnly() = false`，`isDestructive() = true`。

#### Bash

参数：

```json
{
  "command": "mvn test",
  "timeout_seconds": 30
}
```

规则：
- 命令在工作区根目录执行。
- 默认超时来自 `ToolExecutionContext`。
- 返回 stdout/stderr 摘要到 `content`。
- 命令非零退出或超时返回 `isError=true`，不是程序级 error。
- `metadata` 包含 `exitCode`、`durationMillis`、`timedOut`、`stdoutChars`、`stderrChars`。
- `isReadOnly() = false`，`isDestructive() = true`，因为 shell 命令可能修改系统状态。

#### Glob

参数：

```json
{
  "pattern": "src/**/*.java",
  "limit": 200
}
```

规则：
- 支持 glob 风格匹配。
- `**` 匹配任意层级子目录。
- 结果按相对路径排序。
- `content` 返回路径列表。
- `metadata` 包含 `count`、`truncated`。
- `isReadOnly() = true`，`isDestructive() = false`。

#### Grep

参数：

```json
{
  "pattern": "ChatProvider",
  "path": "src",
  "limit": 100
}
```

规则：
- 在工作区内指定范围搜索文本。
- 返回文件路径、行号、列号和匹配行摘要。
- 默认跳过明显二进制文件和构建产物目录。
- `content` 返回紧凑的匹配列表。
- `metadata` 包含 `matchCount`、`scannedFiles`、`truncated`。
- `isReadOnly() = true`，`isDestructive() = false`。

### stream

**职责：** 解析 Claude SSE，并把工具调用内容块组装成 `StreamEvent.ToolUse`。  
**对外接口：** `ClaudeStreamMapper`。  
**依赖：** Jackson。

Claude 工具调用事件顺序：

```text
content_block_start -> type: "tool_use", id: "toolu_xxx", name: "ReadFile"
content_block_delta -> type: "input_json_delta", partial_json: "{"
content_block_delta -> type: "input_json_delta", partial_json: "\"path\""
content_block_delta -> type: "input_json_delta", partial_json: ": \"/main.py\"}"
content_block_stop
```

处理逻辑：
1. 收到 `content_block_start` 且 `type=tool_use` 时，记录 `id` 和 `name`，初始化字符串缓冲区。
2. 后续收到 `content_block_delta` 且 `type=input_json_delta` 时，把 `partial_json` 追加到缓冲区。
3. 收到对应 `content_block_stop` 时，解析缓冲区中的完整 JSON。
4. 解析成功后发出 `StreamEvent.ToolUse(id, name, input)`。
5. 解析失败时发出 `StreamEvent.Error`，这是流解析错误，不是工具执行错误。

文本内容块仍然映射为 `TextDelta`。

### conversation

**职责：** 保存 Claude 消息模式，保证 tool_use 和 tool_result 的 id 配对正确。  
**对外接口：** `ConversationManager`。  
**依赖：** `ContentBlock`。

新增接口：

```java
String addAssistantMessage(List<ContentBlock> blocks);
String addUserToolResultMessage(List<ContentBlock.ToolResultBlock> results);
List<ApiMessage> toAPIFormat();
```

规则：
- 一条 assistant 消息可以同时包含 text 和多个 tool_use 内容块。
- 一条 user 消息可以包含多个 tool_result 内容块。
- tool_result 必须放在 user 角色消息中，这是 Claude API 要求。
- 多个 tool_use 在同一条 assistant 消息里，多个 tool_result 在同一条 user 消息里，通过 `id` / `toolUseId` 配对。
- 格式转换方法不能把同一条 assistant 的 text 和 tool_use 拆开。
- 普通 user/assistant 交替不被打破；tool_result 只是 user 消息的一种内容块。

示例消息：

```text
user:
  text: "帮我读一下项目入口文件"

assistant:
  text: "好的，让我读取这个文件。"
  tool_use: id=toolu_1, name=ReadFile, input={"path": "src/main.py"}

user:
  tool_result: tool_use_id=toolu_1, content="1\tdef main():\n2\t print('hello')"

assistant:
  text: "这个文件包含了程序入口..."
```

### provider

**职责：** 构造 Claude API 请求，发送工具声明，解析 Claude 流式响应。  
**对外接口：** `ChatProvider`。  
**依赖：** `ToolRegistry`、`stream`、Jackson、Java HTTP client。

请求构造：
- 每次调用 Claude API 前都调用 `ToolRegistry.toAPIFormat()`。
- 第一次请求携带所有启用工具。
- 工具结果回灌后的最终回复请求传入空工具列表，避免自动连环工具调用。
- assistant 消息的 text 和 tool_use 内容块必须在同一条消息的 `content` 数组中。
- user 消息的 tool_result 内容块必须携带正确的 `tool_use_id`。

### orchestrator

**职责：** 编排用户输入、模型流、工具执行、结果回灌和最终回复。  
**对外接口：** `ChatOrchestrator`。  
**依赖：** `conversation`、`provider`、`tool`、`stream`。

流程：
1. 收到用户输入，添加普通 user text 消息。
2. 调用 `ToolRegistry.toAPIFormat()` 获取当前启用工具列表。
3. 调用 Claude API。
4. 流式消费 text delta，并累积 assistant text 内容块。
5. 收到一个或多个 `StreamEvent.ToolUse` 时，把它们加入同一条 assistant 消息。
6. 对同一条 assistant 消息中的多个工具调用按顺序执行。本阶段不并行执行。
7. 每个工具执行结果生成一个 `ContentBlock.ToolResultBlock`。
8. 所有 tool_result 放入同一条 user 消息。
9. 再次调用 Claude API 生成最终回复，这次不携带工具列表。
10. 如果最终回复仍出现工具调用，作为协议边界提示处理，不自动执行。

状态栏：
- `responding`：模型正在生成。
- `tool_running`：工具执行中，显示工具名。
- `tool_done`：工具执行成功。
- `tool_error`：工具返回 `isError=true`。
- `idle`：最终回复完成。
- `error`：程序级错误或 Provider 流错误。

### tui

**职责：** 展示工具执行状态和 metadata。  
**对外接口：** `LunaTui` 不依赖具体工具类。  
**依赖：** `conversation`、`orchestrator`。

展示规则：
- 工具结果的 `content` 可以用于对话区摘要。
- `metadata` 用于 UI 细节，例如文件修改时间、命令执行耗时、退出码、是否截断。
- `metadata` 不进入 API 消息，不发送给模型。
- `isError=true` 的工具结果显示为工具失败，但不等同于程序崩溃。

## 模块交互

### 普通对话流程

```text
User input
  -> ConversationManager.addUserTextMessage
  -> ToolRegistry.toAPIFormat()
  -> ClaudeProvider.streamChat(messages, enabledTools)
  -> StreamEvent.TextDelta
  -> ConversationManager append assistant text
  -> StreamEvent.MessageStop
  -> complete assistant message
```

### 一次工具调用流程

```text
User input
  -> ToolRegistry.toAPIFormat()
  -> Claude request with enabled tools
  -> content_block_start(type=tool_use)
  -> content_block_delta(type=input_json_delta)*
  -> content_block_stop
  -> StreamEvent.ToolUse(id, name, input)
  -> assistant message: [text blocks + all tool_use blocks]
  -> execute tools sequentially
  -> user message: [all tool_result blocks]
  -> second Claude request without tools
  -> final assistant text response
```

### 多工具调用格式

如果模型在一次回复中请求多个工具：

```text
assistant:
  text: "我会同时读取文件并搜索引用。"
  tool_use: id=toolu_1, name=ReadFile, input={"path":"pom.xml"}
  tool_use: id=toolu_2, name=Grep, input={"pattern":"jackson","path":"src"}

user:
  tool_result: tool_use_id=toolu_1, content="..."
  tool_result: tool_use_id=toolu_2, content="..."
```

注意：
- 所有 tool_use 保持在同一条 assistant 消息中。
- 所有 tool_result 保持在同一条 user 消息中。
- id 必须一一配对。
- 本阶段按顺序执行多个工具，不做并行执行。

## 文件组织

```text
LunaCode/
├── spec/
│   └── 02/
│       ├── spec.md
│       ├── plan.md
│       ├── task.md
│       └── checklist.md
├── src/
│   ├── main/java/com/lunacode/
│   │   ├── conversation/
│   │   │   ├── ApiMessage.java
│   │   │   ├── ContentBlock.java
│   │   │   ├── ConversationManager.java
│   │   │   └── DefaultConversationManager.java
│   │   ├── orchestrator/
│   │   │   ├── ChatOrchestrator.java
│   │   │   ├── DefaultChatOrchestrator.java
│   │   │   └── StatusSnapshot.java
│   │   ├── provider/
│   │   │   ├── AnthropicProvider.java
│   │   │   └── ChatProvider.java
│   │   ├── stream/
│   │   │   ├── ClaudeStreamMapper.java
│   │   │   ├── StreamEvent.java
│   │   │   └── ToolUseBuffer.java
│   │   └── tool/
│   │       ├── BashTool.java
│   │       ├── DefaultToolExecutor.java
│   │       ├── DefaultToolRegistry.java
│   │       ├── EditFileTool.java
│   │       ├── GlobTool.java
│   │       ├── GrepTool.java
│   │       ├── ReadFileTool.java
│   │       ├── Tool.java
│   │       ├── ToolExecutionContext.java
│   │       ├── ToolExecutor.java
│   │       ├── ToolRegistry.java
│   │       ├── ToolResult.java
│   │       ├── ValidationError.java
│   │       ├── WorkspacePathResolver.java
│   │       └── WriteFileTool.java
│   └── test/java/com/lunacode/
│       ├── conversation/
│       │   └── ToolMessageFormatTest.java
│       ├── orchestrator/
│       │   └── ToolOrchestratorTest.java
│       ├── stream/
│       │   └── ClaudeToolUseStreamMapperTest.java
│       └── tool/
│           ├── ToolRegistryTest.java
│           ├── ReadFileToolTest.java
│           ├── WriteFileToolTest.java
│           ├── EditFileToolTest.java
│           ├── BashToolTest.java
│           ├── GlobToolTest.java
│           └── GrepToolTest.java
```

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 工具接口 | 显式暴露 name、description、inputSchema、execute、权限和并发能力 | 模型声明、UI 展示、后续权限控制和 Agent Loop 都能共用同一套元信息。 |
| 工具结果 | `content + isError + metadata` | 模型只看必要文本，UI 可以展示额外信息，普通工具失败不升级为程序级 error。 |
| 工具名 | `ReadFile`、`WriteFile`、`EditFile`、`Bash`、`Glob`、`Grep` | 与 Claude Code 风格接近，名称短且模型容易理解。 |
| 工具声明格式 | `ToolRegistry.toAPIFormat()` 生成 Claude tools | 每次调 API 前动态读取启用工具列表，支持按名称启停。 |
| Claude 工具流解析 | buffer 拼接 `input_json_delta.partial_json`，结束时发 `ToolUse` | 避免编排器处理半截 JSON，职责边界更清楚。 |
| 消息模型 | `role + List<ContentBlock>` | 支持同一 assistant 消息中同时存在 text 和 tool_use，也支持 user 消息中的 tool_result。 |
| tool_result 角色 | 放在 user 消息里 | 符合 Claude API 要求，同时保持 user/assistant 交替。 |
| 多工具调用 | 同一 assistant 多个 tool_use，同一 user 多个 tool_result，顺序执行 | 支持模型一次请求多个工具，但不引入并行执行复杂度。 |
| 读取文件分页 | `offset` 从 1 开始，`limit` 表示读取行数 | 返回原始行号，便于模型分段读取和后续编辑定位。 |
| 写文件权限 | POSIX 支持时设置目录 0755、文件 0644，非 POSIX best effort | 满足权限要求，同时兼容 Windows 开发环境。 |
| 替换修改 | `EditFile` 使用原文唯一匹配替换 | 不引入复杂补丁格式，错误可清晰回传给模型。 |
| 查找文件 | `Glob` 支持 `**` 递归匹配 | 符合模型常用文件搜索习惯。 |
| 搜索代码 | `Grep` 返回路径、行号、列号和摘要 | 足够支撑代码理解，不提前做索引系统。 |
| Agent Loop | 工具结果回灌后第二次请求不携带工具 | 严格遵守本阶段不做自动循环的边界。 |
