# LunaCode Agent Loop Plan

## 架构概览

本阶段把第二章的固定“两次请求”编排改造成真正的 Agent Loop。新的结构以 `agent` 模块为核心：用户消息进入 Agent Loop，Agent Loop 产出 `AgentEvent` 异步事件流；TUI 只消费事件并更新界面。现有 `orchestrator` 保留为 TUI 和 Agent 之间的适配层，负责提交用户消息、维护当前状态、把事件映射到现有 `ConversationManager` 和 `StatusSnapshot`，避免一次性推翻当前 TUI。

Agent Loop 内部按“轮次状态机”推进。每一轮都是一个独立 `AgentTurn`：构建 System Prompt，调用 Provider，双路收集流式响应，收集工具调用，发出轮次完成事件，再交给统一的 `LoopDecision` 决策器判断是否继续。继续判断只放在一个地方，统一处理模型完成、迭代上限、取消信号、流式错误、连续未知工具和下一批工具调用。

Provider 层新增 System Prompt 参数。每轮调用 Claude 时都传入由 `SystemPromptBuilder` 构建的 Prompt，包含 LunaCode 角色设定、当前工作目录、操作系统、当前时间和模式指令。Default 模式只包含基础指令；Plan Mode 额外追加 `PLAN_MODE_INSTRUCTIONS`，要求模型先使用 `AskUserQuestion` 澄清需求，再探索与规划，把计划写入指定 plan file，并等待用户确认。

工具执行从“顺序执行列表”升级为“按安全性分批执行”。`ToolBatchPlanner` 使用现有 `Tool.isReadOnly()`、`Tool.isDestructive()`、`Tool.isConcurrencySafe(input)` 元信息分批：只读且并发安全的工具同批并发执行；写文件、改文件、Bash 或声明不适合并发的工具串行执行。工具调用前统一经过 `ToolPermissionGateway`。Default 权限矩阵表达为 read=allow、write=ask、command=ask；Plan Mode 不改变矩阵，只对指定 plan file 自动放行。

模块划分如下：

- `agent`：Agent Loop、轮次状态机、继续决策、取消信号、事件流、流式收集器、System Prompt 构建、Plan Mode 状态。
- `orchestrator`：适配现有 TUI，提交用户消息，订阅 AgentEvent，更新 conversation/status，暴露取消入口。
- `provider`：扩展 `ChatProvider` 请求参数，支持 System Prompt 和工具声明一起传入 Claude。
- `tool`：复用现有工具接口，增加 Plan Mode 专用 `AskUserQuestion` 工具、分批执行、权限网关、工具耗时统计和未知工具连续计数所需元信息读取。
- `tui`：消费 `AgentEvent` 或通过 orchestrator 的事件适配结果刷新界面，支持取消输入。
- `config`：增加 Agent 配置，例如最大迭代数、连续未知工具阈值、默认 plan file 路径。

## 核心数据结构

### AgentMode

```java
enum AgentMode {
    DEFAULT,
    PLAN
}
```

说明：
- `DEFAULT` 是普通执行模式。
- `PLAN` 是规划模式，System Prompt 追加 Plan Mode 指令，权限矩阵仍保持 Default，只自动放行指定 plan file。

### AgentRunConfig

```java
record AgentRunConfig(
    Path workDir,
    AgentMode mode,
    Path planFile,
    int maxIterations,
    int maxConsecutiveUnknownTools,
    Clock clock
) {}
```

说明：
- `workDir` 用于 System Prompt 的环境信息和工具上下文。
- `mode` 决定是否追加 Plan Mode 指令。
- `planFile` 是 Plan Mode 自动放行的唯一写入目标。
- `maxIterations` 是 Agent Loop 的兜底安全网。
- `maxConsecutiveUnknownTools` 用于判断模型是否连续请求不存在、拼错或已禁用工具。
- `clock` 便于测试当前时间。

### AgentEvent

```java
sealed interface AgentEvent permits
    AgentEvent.StreamText,
    AgentEvent.ToolUseStarted,
    AgentEvent.ToolResultReady,
    AgentEvent.TurnComplete,
    AgentEvent.LoopComplete,
    AgentEvent.UsageUpdated,
    AgentEvent.ErrorOccurred {

    record StreamText(String text) implements AgentEvent {}

    record ToolUseStarted(
        String requestId,
        String toolName,
        JsonNode input
    ) implements AgentEvent {}

    record ToolResultReady(
        String requestId,
        String toolName,
        ToolResult result,
        Duration duration
    ) implements AgentEvent {}

    record TurnComplete(int turnIndex) implements AgentEvent {}

    record LoopComplete(int totalTurns) implements AgentEvent {}

    record UsageUpdated(TokenUsage cumulativeUsage) implements AgentEvent {}

    record ErrorOccurred(String message, Throwable cause) implements AgentEvent {}
}
```

事件类型映射：
- `StreamText` 对应 `stream_text`，携带文本增量。
- `ToolUseStarted` 对应 `tool_use`，携带工具名、输入参数和请求 ID。
- `ToolResultReady` 对应 `tool_result`，携带执行结果、是否出错和耗时。
- `TurnComplete` 对应 `turn_complete`，携带当前轮次序号。
- `LoopComplete` 对应 `loop_complete`，携带总轮次。
- `UsageUpdated` 对应 `usage`，携带累计 token 用量。
- `ErrorOccurred` 对应 `error`，携带错误信息。

### AgentEventSink

```java
interface AgentEventSink {
    void emit(AgentEvent event);
}
```

Agent Loop 不直接操作 TUI，只向 `AgentEventSink` 发事件。生产代码中 sink 由 orchestrator 提供；测试中可使用内存 sink 收集事件序列。

### UserQuestionRequest

```java
record UserQuestionRequest(
    String requestId,
    String question
) {}
```

说明：
- `requestId` 使用模型工具调用 ID，后续回答会作为同一 ID 的 tool_result 回灌。
- `question` 是模型提出的一个聚焦澄清问题。
- 本章不设计复杂表单；一次工具调用只问一个问题。

### UserQuestionBroker

```java
interface UserQuestionBroker {
    String ask(UserQuestionRequest request);
}
```

职责：
- `AskUserQuestionTool` 通过它把问题交给 UI。
- UI 显示问题，等待用户输入回答。
- 返回值作为 `ToolResult.content` 回灌给模型。
- 如果用户取消当前 Agent Loop，broker 返回取消结果或抛出可转换为工具错误的异常。

### CancellationToken

```java
final class CancellationToken {
    boolean isCancellationRequested();
    void cancel();
}
```

取消由 UI 层触发，经 orchestrator 调用 `cancel()` 传到 Agent Loop。Loop 不强杀已经开始的底层模型流或工具调用，但每轮开始前必须检查 `isCancellationRequested()`；如果为 true，发出取消状态并干净退出，不再发起新的模型调用或工具批次。

### AgentTurnState

```java
enum AgentTurnState {
    STARTING,
    STREAMING_MODEL,
    COLLECTING_TOOL_USE,
    EXECUTING_TOOLS,
    RECORDING_RESULTS,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

每轮状态机只描述单轮生命周期，不决定整个循环是否继续。继续判断交给 `LoopDecisionMaker`。

### AgentTurnResult

```java
record AgentTurnResult(
    int turnIndex,
    String assistantMessageId,
    String fullText,
    List<ToolUse> toolUses,
    TokenUsage usage,
    AgentTurnState finalState,
    String errorSummary
) {}
```

说明：
- `fullText` 来自流式收集器的完整文本缓冲。
- `toolUses` 是本轮完整工具调用列表。
- `usage` 是本轮结束后的最新用量。
- `finalState` 供继续决策判断。

### LoopDecision

```java
sealed interface LoopDecision permits
    LoopDecision.ContinueWithTools,
    LoopDecision.Complete,
    LoopDecision.StopWithLimit,
    LoopDecision.StopCancelled,
    LoopDecision.StopUnknownTools,
    LoopDecision.StopError {

    record ContinueWithTools(List<ToolUse> toolUses) implements LoopDecision {}
    record Complete() implements LoopDecision {}
    record StopWithLimit(int maxIterations) implements LoopDecision {}
    record StopCancelled() implements LoopDecision {}
    record StopUnknownTools(int count) implements LoopDecision {}
    record StopError(String summary) implements LoopDecision {}
}
```

所有“是否继续”逻辑集中在 `LoopDecisionMaker`。Agent Loop 主循环只执行决策结果，不在多个分支里各自判断停止。

### LoopDecisionMaker

```java
final class LoopDecisionMaker {
    LoopDecision decide(LoopContext context, AgentTurnResult turnResult);
}
```

判断顺序：
1. 如果取消信号已请求，返回 `StopCancelled`。
2. 如果本轮失败，返回 `StopError`。
3. 如果已达到最大迭代数，返回 `StopWithLimit`。
4. 如果连续未知工具数达到阈值，返回 `StopUnknownTools`。
5. 如果本轮没有工具调用，返回 `Complete`。
6. 否则返回 `ContinueWithTools`。

模型“我做完了”的信号在本阶段以“没有工具调用并给出最终回复”作为可观察行为；后续如果引入显式完成标记，只需要扩展此决策器。

### LoopContext

```java
record LoopContext(
    AgentRunConfig config,
    CancellationToken cancellationToken,
    int currentIteration,
    int consecutiveUnknownToolCount,
    TokenUsage cumulativeUsage
) {}
```

LoopContext 保存跨轮状态。每轮完成后由主循环更新，再交给 `LoopDecisionMaker`。

### StreamingTurnCollector

```java
final class StreamingTurnCollector {
    AgentTurnResult collect(
        int turnIndex,
        Stream<StreamEvent> providerEvents,
        String assistantMessageId,
        AgentEventSink sink,
        TokenUsage initialUsage
    );
}
```

职责：
- 收到 `StreamEvent.ContentDelta` 时，立即追加到 conversation，并发出 `AgentEvent.StreamText`。
- 同时把文本追加到本轮 `StringBuilder`，形成完整 `fullText`。
- 收到 `StreamEvent.ToolUse` 时，记录工具调用，追加到 assistant 消息，并发出 `AgentEvent.ToolUseStarted`。
- 收到 token 用量事件时，合并累计用量并发出 `AgentEvent.UsageUpdated`。
- 收到流错误时，标记本轮失败并发出 `AgentEvent.ErrorOccurred`。

### SystemPromptBuilder

```java
final class SystemPromptBuilder {
    String build(SystemPromptConfig config);
}

record SystemPromptConfig(
    Path workDir,
    String osName,
    Instant now,
    AgentMode mode,
    Path planFile
) {}
```

最简 Prompt 组成：

```text
你是 LunaCode，一个终端环境中的 AI 编程助手。
你擅长阅读代码、编写代码和调试问题。
你会先思考再行动，每一步都解释你的推理过程。

# Environment
当前工作目录：{workDir}
操作系统：{osName}
当前时间：{now}

{PLAN_MODE_INSTRUCTIONS if plan mode}
```

`PLAN_MODE_INSTRUCTIONS`：

```text
Plan mode is active.
你不能执行任何修改操作，不能编辑非 plan 文件，不能提交代码，不能修改配置。
唯一可以写入的文件是指定 plan file：{planFile}
你的工作流程：
1. 如果需求不清楚，先使用 AskUserQuestion 一次提出一个聚焦问题，逐步澄清需求。
2. 用 ReadFile、Grep、Glob、Bash（只读命令）探索代码。
3. 分析用户需求，设计实现方案。
4. 把计划写入 plan file。
5. 等待用户确认后再执行。
```

### ToolBatch

```java
record ToolBatch(
    List<ToolUse> toolUses,
    boolean parallel
) {}
```

`parallel=true` 表示这一批工具可并发执行。`parallel=false` 表示按列表顺序串行执行。

### ToolBatchPlanner

```java
final class ToolBatchPlanner {
    List<ToolBatch> plan(List<ToolUse> toolUses, ToolRegistry registry);
}
```

规则：
- 工具不存在、拼写错误或已禁用时不直接丢弃，仍生成一个串行批次，执行器返回 `tool_not_found` 错误结果。
- `AskUserQuestion` 只能在 Plan Mode 暴露给模型，必须作为串行批次执行，避免多个澄清问题同时弹出。
- 工具存在且 `isReadOnly()` 为 true，并且 `isConcurrencySafe(input)` 为 true，可以合并到同一个并发批次。
- `isDestructive()` 为 true 或 `isConcurrencySafe(input)` 为 false 的工具必须独立串行执行。
- 遇到串行工具时，先 flush 当前并发批次，再执行串行批次。

### ToolPermissionGateway

```java
interface ToolPermissionGateway {
    PermissionDecision decide(ToolUse toolUse, Tool tool, AgentMode mode, Path planFile);
}

enum PermissionDecision {
    ALLOW,
    ASK,
    DENY
}
```

说明：
- 本章只定义并接入权限判断入口，不实现复杂权限系统。
- Default 矩阵表达为：读类工具 `ALLOW`，写类工具 `ASK`，Bash `ASK`。
- Plan Mode 与 Default 矩阵一致。
- Plan Mode 唯一特殊文件处理：如果写入目标等于指定 plan file，则 `ALLOW`。
- `AskUserQuestion` 在 Plan Mode 下 `ALLOW`，在 Default 模式下不暴露；如果被异常调用则返回工具不可用错误。
- 当前若没有完整确认 UI，`ASK` 结果先通过事件或状态暴露给 UI；具体确认交互可以由现有确认流程或后续章节实现。

### AskUserQuestionTool

```java
final class AskUserQuestionTool implements Tool {
    String name(); // "AskUserQuestion"
    ToolResult execute(ToolExecutionContext context, JsonNode input);
}
```

参数 Schema：

```json
{
  "type": "object",
  "properties": {
    "question": {"type": "string"}
  },
  "required": ["question"]
}
```

规则：
- 仅用于 Plan Mode 需求澄清。
- `question` 必须是一个聚焦问题，不能为空。
- 执行时通过 `UserQuestionBroker` 把问题交给 UI，并等待用户回答。
- 成功时返回 `ToolResult.success("用户回答：...", metadata)`。
- 用户取消或 broker 不可用时返回 `ToolResult.isError=true`。
- `isReadOnly() = true`，`isDestructive() = false`，`isConcurrencySafe(input) = false`。

### ToolExecutionContext 扩展

```java
record ToolExecutionContext(
    Path workspaceRoot,
    Duration commandTimeout,
    int maxContentChars,
    SensitiveValueMasker masker,
    UserQuestionBroker userQuestionBroker
) {}
```

说明：
- 现有文件和命令工具忽略 `userQuestionBroker`。
- `AskUserQuestionTool` 必须依赖该 broker 才能和 UI 往返。

### ToolExecutionRecord

```java
record ToolExecutionRecord(
    ToolUse toolUse,
    ToolResult result,
    Duration duration
) {}
```

用于生成 `AgentEvent.ToolResultReady` 和 `ContentBlock.ToolResultBlock`。

## 核心接口

### AgentLoop

```java
interface AgentLoop {
    void run(AgentRequest request, AgentEventSink sink, CancellationToken cancellationToken);
}
```

### AgentRequest

```java
record AgentRequest(
    String userMessage,
    AgentRunConfig config
) {}
```

职责：
- 添加用户消息。
- 按轮次调用 `AgentTurnRunner`。
- 每轮开始前检查取消信号。
- 调用 `LoopDecisionMaker` 决定继续或停止。
- 循环结束时发出 `AgentEvent.LoopComplete`。

### AgentTurnRunner

```java
final class AgentTurnRunner {
    AgentTurnResult runTurn(AgentTurnInput input);
}

record AgentTurnInput(
    int turnIndex,
    String systemPrompt,
    List<ApiMessage> messages,
    ArrayNode enabledTools,
    TokenUsage cumulativeUsage,
    AgentEventSink sink
) {}
```

职责：
- 创建 streaming assistant 消息。
- 调用 `ChatProvider.streamChat(...)`。
- 交给 `StreamingTurnCollector` 消费流事件。
- 本轮结束时发出 `AgentEvent.TurnComplete`。

### ChatProvider

```java
interface ChatProvider {
    Stream<StreamEvent> streamChat(
        List<ApiMessage> messages,
        ProviderConfig config,
        ArrayNode enabledTools,
        String systemPrompt
    );
}
```

兼容策略：
- 保留现有重载作为默认方法，旧测试继续可用。
- Anthropic 请求体把 `systemPrompt` 写入顶层 `system` 字段。
- OpenAI 请求体把 `systemPrompt` 作为第一条 `role=system` 消息写入 messages。
- 只有 Claude 是本阶段 System Prompt 的主要目标，但接口对 OpenAI 保持兼容。

### ChatOrchestrator

```java
interface ChatOrchestrator {
    void submitUserMessage(String content);
    void cancelCurrentRun();
    StatusSnapshot status();
}
```

`submitUserMessage` 解析 `/plan`、`/do` 和用户对澄清问题的回答：
- 如果当前存在待回答的 AskUserQuestion，普通输入优先作为问题答案提交给 `UserQuestionBroker`，不当作新任务。

`submitUserMessage` 解析 `/plan` 和 `/do`：
- 普通文本进入 `AgentMode.DEFAULT`。
- `/plan ...` 进入 `AgentMode.PLAN`，分配或复用指定 plan file。
- `/do` 切回 `AgentMode.DEFAULT`，把上一次 plan file 信息保留在上下文中。

`cancelCurrentRun`：
- 由 UI 按键或命令触发。
- 调用当前 `CancellationToken.cancel()`。

## 模块设计

### agent

**职责：** 实现 Agent Loop 主循环、每轮状态机、继续决策、事件流、System Prompt 和 Plan Mode 状态。  
**对外接口：** `AgentLoop`、`AgentEvent`、`AgentRequest`、`CancellationToken`。  
**依赖：** `conversation`、`provider`、`tool`、`config`。

核心流程：
1. `AgentLoop.run` 收到用户消息和配置。
2. 添加 user 消息到 `ConversationManager`。
3. 初始化 `LoopContext`，累计用量为 unknown，连续未知工具数为 0。
4. 进入循环。
5. 每轮开始前检查取消信号。
6. 使用 `SystemPromptBuilder` 构建本轮 System Prompt。
7. 调用 `AgentTurnRunner.runTurn`。
8. 根据工具结果更新连续未知工具计数。
9. 调用 `LoopDecisionMaker.decide`。
10. 如果继续，按 `ToolBatchPlanner` 执行工具并回灌工具结果，进入下一轮。
11. 如果停止，发出对应事件和 `LoopComplete`。

### orchestrator

**职责：** 连接现有 TUI 与 Agent Loop。  
**对外接口：** `ChatOrchestrator`。  
**依赖：** `agent`、`conversation`、`provider`、`tool`。

实现策略：
- `DefaultChatOrchestrator` 不再内联两段式工具流程，而是创建 `AgentRequest` 并提交给 `AgentLoop`。
- 内部维护当前 `CancellationToken`。
- 实现 `AgentEventSink`，收到事件后更新 `ConversationManager` 和 `StatusSnapshot`。
- 现有 TUI 仍可以通过 `conversationManager.snapshot()` 和 `status()` 渲染。
- 后续可让 TUI 直接订阅事件流，本章先用 sink 适配减少改动范围。

事件映射：
- `StreamText`：conversation 追加 assistant 内容。
- `ToolUseStarted`：conversation 追加 tool_use 内容块，状态变为 `tool_running`。
- `ToolResultReady`：conversation 添加 tool_result，状态变为 `tool_done` 或 `tool_error`。
- `UsageUpdated`：更新状态栏 token。
- `ErrorOccurred`：当前 assistant 消息进入 error，状态变为 `error`。
- `LoopComplete`：状态变为 `idle` 或 `cancelled`。

### provider

**职责：** 支持每轮 System Prompt。  
**对外接口：** `ChatProvider`。  
**依赖：** Jackson、Java HTTP Client。

Anthropic：
- 请求体增加顶层 `system` 字段。
- 保持现有 `messages`、`tools`、`thinking` 逻辑。
- 每轮请求都由 Agent Loop 显式传入 system prompt。

OpenAI：
- 请求 messages 前插入 system 消息。
- 暂不扩展 OpenAI 工具调用能力，只保持纯对话兼容。

### tool

**职责：** 工具分批、权限网关和工具执行耗时统计。  
**对外接口：** `ToolBatchPlanner`、`ToolPermissionGateway`、`ToolExecutionRecord`。  
**依赖：** 现有 `ToolRegistry`、`ToolExecutor`。

执行流程：
1. Agent Loop 收到本轮工具调用列表。
2. `ToolBatchPlanner` 按安全性生成批次。
3. 每个工具调用先发出 `ToolUseStarted` 事件。
4. 查询 `ToolPermissionGateway`。
5. `ALLOW` 直接执行。
6. 对 `AskUserQuestion`，通过 `UserQuestionBroker` 将问题交给 UI，等待用户回答后生成工具结果。
7. `ASK` 交给现有确认入口；如果当前没有确认 UI，则生成待确认状态，不静默执行。
8. `DENY` 生成错误 `ToolResult`。
9. 记录耗时，发出 `ToolResultReady`。
10. 生成 `ContentBlock.ToolResultBlock` 回灌。

未知工具处理：
- `ToolExecutor` 当前已返回 `errorType=tool_not_found`。
- Agent Loop 识别该 metadata，增加连续未知工具计数。
- 非未知工具结果会清零该计数。

### tui

**职责：** 触发提交和取消，按事件适配后的 conversation/status 展示界面。  
**对外接口：** `LunaTui`。  
**依赖：** `ChatOrchestrator`。

交互设计：
- 保留当前输入提交逻辑。
- 增加取消按键或命令，例如响应中按 Esc 或输入 `/cancel` 调用 `cancelCurrentRun()`。
- 收到普通工具事件后显示工具名和输入摘要。
- 收到 AskUserQuestion 工具调用时，把问题显示为需求澄清提示，并把用户下一次输入作为回答提交给 orchestrator。
- 收到工具结果后显示耗时和折叠摘要。
- 状态栏使用 `usage` 事件累计值。

### config

**职责：** 加载 Agent Loop 配置。  
**对外接口：** `AgentConfig`。  
**依赖：** `ConfigLoader`。

新增配置：

```yaml
agent:
  max_iterations: 8
  max_consecutive_unknown_tools: 3
  plan_file: ".lunacode/plan.md"
```

默认值：
- `max_iterations = 8`
- `max_consecutive_unknown_tools = 3`
- `plan_file = .lunacode/plan.md`

## 模块交互

### 普通 Agent Loop

```text
TUI submit user message
  -> ChatOrchestrator.submitUserMessage
  -> AgentLoop.run(request, sink, token)
  -> add user message
  -> loop turn 1
      -> check cancellation
      -> build System Prompt
      -> ChatProvider.streamChat(messages, config, tools, systemPrompt)
      -> StreamingTurnCollector emits stream_text/tool_use/usage
      -> turn_complete
      -> LoopDecisionMaker.decide
  -> execute tool batches if needed
  -> add tool_result user message
  -> loop turn 2...
  -> loop_complete
```

### 取消流程

```text
UI cancel action
  -> ChatOrchestrator.cancelCurrentRun()
  -> CancellationToken.cancel()
  -> current low-level call finishes naturally
  -> next loop turn begins
  -> AgentLoop sees cancellation requested
  -> emit loop_complete / cancelled status
  -> no new model call or tool batch starts
```

### Plan Mode 流程

```text
User: /plan 实现 X
  -> mode = PLAN
  -> SystemPromptBuilder appends PLAN_MODE_INSTRUCTIONS
  -> AskUserQuestion is exposed
  -> model asks one clarifying question if needed
  -> UI collects answer
  -> answer returns as tool_result
  -> model continues clarification/exploration
  -> permission matrix remains default
  -> write to plan file is auto ALLOW
  -> write elsewhere remains ASK
  -> command remains ASK
  -> loop completes after model writes plan and final text

User: /do
  -> mode = DEFAULT
  -> previous plan file remains in context
  -> Agent Loop executes based on plan and normal permissions
```

### 多工具分批

```text
Tool uses in one turn:
  ReadFile(a), Grep(x), WriteFile(b), Glob(y), Bash(cmd)

ToolBatchPlanner output:
  batch 1 parallel: ReadFile(a), Grep(x)
  batch 2 serial: WriteFile(b)
  batch 3 parallel: Glob(y)
  batch 4 serial: Bash(cmd)
```

## 文件组织

```text
src/main/java/com/lunacode/
├── agent/
│   ├── AgentLoop.java
│   ├── DefaultAgentLoop.java
│   ├── AgentRequest.java
│   ├── AgentRunConfig.java
│   ├── AgentMode.java
│   ├── AgentEvent.java
│   ├── AgentEventSink.java
│   ├── AgentTurnRunner.java
│   ├── AgentTurnInput.java
│   ├── AgentTurnResult.java
│   ├── AgentTurnState.java
│   ├── CancellationToken.java
│   ├── UserQuestionRequest.java
│   ├── UserQuestionBroker.java
│   ├── LoopContext.java
│   ├── LoopDecision.java
│   ├── LoopDecisionMaker.java
│   ├── StreamingTurnCollector.java
│   ├── SystemPromptBuilder.java
│   └── SystemPromptConfig.java
├── config/
│   ├── AgentConfig.java
│   ├── ConfigLoader.java
│   └── ProviderConfig.java
├── orchestrator/
│   ├── ChatOrchestrator.java
│   ├── DefaultChatOrchestrator.java
│   └── StatusSnapshot.java
├── provider/
│   ├── ChatProvider.java
│   ├── AnthropicProvider.java
│   └── OpenAiProvider.java
├── tool/
│   ├── ToolBatch.java
│   ├── ToolBatchPlanner.java
│   ├── AskUserQuestionTool.java
│   ├── ToolExecutionRecord.java
│   ├── ToolPermissionGateway.java
│   └── PermissionDecision.java
└── tui/
    ├── LunaTui.java
    └── LanternaLunaTui.java

src/test/java/com/lunacode/
├── agent/
│   ├── DefaultAgentLoopTest.java
│   ├── AgentTurnRunnerTest.java
│   ├── LoopDecisionMakerTest.java
│   ├── StreamingTurnCollectorTest.java
│   └── SystemPromptBuilderTest.java
├── provider/
│   ├── AnthropicProviderSystemPromptTest.java
│   └── OpenAiProviderSystemPromptTest.java
├── tool/
│   ├── ToolBatchPlannerTest.java
│   ├── AskUserQuestionToolTest.java
│   └── ToolPermissionGatewayTest.java
└── orchestrator/
    └── AgentOrchestratorEventBridgeTest.java
```


### 文件职责说明

#### 主代码文件

- `agent/AgentLoop.java`：定义 Agent Loop 的统一入口，接收用户请求、事件输出和取消信号。
- `agent/DefaultAgentLoop.java`：实现多轮 ReAct 循环，负责调用模型、执行工具、回灌工具结果和处理停止条件。
- `agent/AgentRequest.java`：封装一次 Agent 请求中的用户消息和运行配置。
- `agent/AgentRunConfig.java`：保存工作目录、运行模式、plan file、最大迭代次数、未知工具阈值和时钟。
- `agent/AgentMode.java`：定义 Default 和 Plan 两种 Agent 运行模式。
- `agent/AgentEvent.java`：定义 Agent 输出给 UI 的事件类型，如文本增量、工具调用、工具结果、用量、错误和完成。
- `agent/AgentEventSink.java`：定义事件发布接口，让 Agent Loop 与具体 UI 渲染解耦。
- `agent/AgentTurnRunner.java`：执行单轮模型调用，创建 assistant 流式消息并交给流式收集器处理。
- `agent/AgentTurnInput.java`：描述单轮调用输入，包括 system prompt、历史消息、工具声明、Provider 配置和累计用量。
- `agent/AgentTurnResult.java`：保存单轮调用结果，包括完整文本、工具调用列表、usage、最终状态和错误摘要。
- `agent/AgentTurnState.java`：枚举单轮状态机状态，用于表达本轮开始、流式输出、工具收集、完成或失败。
- `agent/CancellationToken.java`：提供可传播的取消信号，供 UI 请求停止后续轮次。
- `agent/UserQuestionRequest.java`：表示 Plan Mode 中模型提出的一次需求澄清问题。
- `agent/UserQuestionBroker.java`：定义 AskUserQuestion 工具与 UI 之间的提问和回答通道。
- `agent/LoopContext.java`：保存跨轮上下文，如当前轮次、连续未知工具次数和累计 token 用量。
- `agent/LoopDecision.java`：定义继续执行、正常完成、达到上限、取消、未知工具过多和错误停止等决策结果。
- `agent/LoopDecisionMaker.java`：集中判断 Agent Loop 是否继续，统一管理所有停止条件。
- `agent/StreamingTurnCollector.java`：消费 Provider 流式事件，实时发布文本增量，同时收集完整回复、工具调用和 usage。
- `agent/SystemPromptBuilder.java`：根据环境信息和运行模式构建每轮传给模型的 System Prompt。
- `agent/SystemPromptConfig.java`：封装构建 System Prompt 所需的工作目录、操作系统、当前时间、模式和 plan file。
- `config/AgentConfig.java`：定义 Agent Loop 配置项及默认值，包括最大迭代次数、未知工具阈值和默认 plan file。
- `config/ConfigLoader.java`：读取配置文件，并解析 Provider、thinking 和 agent 配置。
- `config/ProviderConfig.java`：保存模型 Provider 调用配置，并携带 AgentConfig 供运行时使用。
- `orchestrator/ChatOrchestrator.java`：定义 TUI 提交消息、取消当前运行和读取状态的接口。
- `orchestrator/DefaultChatOrchestrator.java`：连接 TUI 与 Agent Loop，解析 `/plan`、`/do`、`/cancel`，并把 AgentEvent 映射为 UI 状态。
- `orchestrator/StatusSnapshot.java`：保存 UI 状态快照，如 provider、model、token、状态、错误摘要和工具状态。
- `provider/ChatProvider.java`：定义模型流式调用接口，并支持工具声明和 System Prompt 参数。
- `provider/AnthropicProvider.java`：实现 Anthropic Messages API 请求，把 System Prompt 写入顶层 `system` 字段。
- `provider/OpenAiProvider.java`：实现 OpenAI Chat Completions 请求，把 System Prompt 作为第一条 system 消息。
- `tool/ToolBatch.java`：表示一批工具调用以及该批次是否可以并发执行。
- `tool/ToolBatchPlanner.java`：根据工具只读性、副作用和并发安全性，将工具调用拆成并发或串行批次。
- `tool/AskUserQuestionTool.java`：实现 Plan Mode 专用需求澄清工具，把模型问题交给 UI 并回灌用户回答。
- `tool/ToolExecutionRecord.java`：记录工具调用、工具结果和耗时，用于事件发布和工具结果回灌。
- `tool/ToolPermissionGateway.java`：定义工具权限判断入口，集中处理 Default、Plan Mode 和 plan file 放行规则。
- `tool/PermissionDecision.java`：定义权限判断结果：允许、需要确认或拒绝。
- `tui/LunaTui.java`：定义终端 UI 的基础接口。
- `tui/LanternaLunaTui.java`：实现终端 UI，负责渲染对话、处理输入、取消快捷键和需求澄清回答。

#### 测试文件

- `agent/DefaultAgentLoopTest.java`：验证多轮循环、工具回灌、停止条件、未知工具保护和取消行为。
- `agent/AgentTurnRunnerTest.java`：验证单轮调用状态机、System Prompt 传递和 turn_complete 事件。
- `agent/LoopDecisionMakerTest.java`：验证集中继续决策覆盖完成、继续、上限、取消、错误和未知工具分支。
- `agent/StreamingTurnCollectorTest.java`：验证流式文本双路收集、工具调用收集、usage 事件和错误事件。
- `agent/SystemPromptBuilderTest.java`：验证 Default 和 Plan Mode 的 System Prompt 内容。
- `provider/AnthropicProviderSystemPromptTest.java`：验证 Anthropic 请求体包含顶层 system 字段，并保持 tools/thinking 不变。
- `provider/OpenAiProviderSystemPromptTest.java`：验证 OpenAI 请求体首条消息为 system。
- `tool/ToolBatchPlannerTest.java`：验证只读工具并发批次、副作用工具串行批次和未知工具保留。
- `tool/AskUserQuestionToolTest.java`：验证需求澄清工具的参数校验、broker 调用、回答包装和错误处理。
- `tool/ToolPermissionGatewayTest.java`：验证权限矩阵、Plan Mode plan file 自动放行和 AskUserQuestion 权限。
- `orchestrator/AgentOrchestratorEventBridgeTest.java`：验证 Orchestrator 的事件桥接，以及 `/plan`、`/do`、澄清回答和取消路由。

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| Agent 对外契约 | 用户消息输入，`AgentEvent` 异步事件流输出 | UI 与 Agent Loop 解耦，后续可接不同界面或日志消费者。 |
| 轮次模型 | 每一轮抽象为 `AgentTurnRunner` 状态机 | 本轮生命周期清晰，便于测试和扩展。 |
| 继续判断 | `LoopDecisionMaker` 集中判断 | 避免停止条件散落在 Provider、工具执行和 UI 分支里。 |
| 完成信号 | 先以“无工具调用并给出最终回复”作为完成 | 可观测、兼容现有模型输出；未来可扩展显式完成事件。 |
| 取消语义 | UI 发信号，Loop 下一轮开始前检查 | 干净退出，不强杀底层调用，符合当前 Java HTTP 和工具执行模型。 |
| 事件字段 | 工具调用带工具名/输入/requestId，工具结果带结果/错误/耗时，用量带累计值 | 满足 UI 渲染“正在执行 ReadFile /path”“50ms”和状态栏 token 更新。 |
| System Prompt | 每轮构建并传给 Provider | 环境信息和模式指令随每次请求稳定注入，避免模型跨轮遗忘。 |
| Anthropic System Prompt | 顶层 `system` 字段 | 符合 Claude Messages API 请求结构。 |
| OpenAI System Prompt | 第一条 `role=system` 消息 | 保持现有 OpenAI 兼容路径。 |
| Plan Mode | Prompt 约束模型行为，不收窄默认工具声明，并额外暴露 `AskUserQuestion` | 与需求一致，模型可先澄清需求，再生成计划，权限网关负责兜底。 |
| Plan file 自动放行 | 在 `ToolPermissionGateway` 特判目标路径 | 特殊规则集中，不污染 WriteFile/EditFile 工具实现。 |
| AskUserQuestion | 作为 Plan Mode 专用工具，通过 UI broker 返回用户回答 | 符合一步步澄清需求的工作流，同时复用 tool_use/tool_result 回灌机制。 |
| 权限系统边界 | 本章只接入薄网关，不实现复杂确认系统 | 满足 Plan Mode 放行点，又不把本章扩展成权限章节。 |
| 多工具执行 | `ToolBatchPlanner` 分并发批和串行批 | 复用现有 Tool 元信息，读类工具可并发，副作用工具保守串行。 |
| 未知工具 | 先回灌错误工具结果，连续达到阈值后终止 | 给模型自我修正机会，同时防止无限迷失。 |
| TUI 迁移 | Orchestrator 先做事件到 conversation/status 的桥接 | 保持当前 TUI 可用，降低一次性重构风险。 |
| 配置默认值 | maxIterations=8，unknownTool=3，planFile=.lunacode/plan.md | 保守安全，足够覆盖常见多步任务。 |
