# LunaCode Agent Loop NewPlan

## 架构概览

本章的新方案把 Agent Loop 拆成四个明确子层，并把运行模式移出 `agent` 根包：

- `agent`：只保留多轮循环入口、请求、循环上下文和继续/停止决策。
- `agent.turn`：单轮模型调用、流式收集、完整回复和工具调用收集。
- `agent.event`：Agent 输出给 Orchestrator / UI 的事件契约。
- `agent.execution`：工具批次执行、权限判断、耗时记录和 tool_result 事件发射。
- `runtime`：运行模式、运行配置、取消信号。
- `interaction`：AskUserQuestion 的 UI 问答通道。

这样拆分后，`DefaultAgentLoop` 只表达核心循环：“构建本轮请求 -> 跑一轮 -> 决策 -> 执行工具 -> 回灌结果 -> 继续或停止”。工具执行、Prompt 构建、事件定义、取消信号都不再混在同一个目录里。

## 模块职责

### agent

保留：

- `AgentLoop`
- `DefaultAgentLoop`
- `AgentRequest`
- `LoopContext`
- `LoopDecision`
- `LoopDecisionMaker`

职责：

- 添加用户消息。
- 按轮次调用 `AgentTurnRunner`。
- 合并 usage。
- 调用 `LoopDecisionMaker` 集中判断是否继续。
- 需要工具时调用 `AgentToolRunner`。
- 将工具结果写回 `ConversationManager`。

### agent.turn

包含：

- `AgentTurnInput`
- `AgentTurnResult`
- `AgentTurnRunner`
- `AgentTurnState`
- `StreamingTurnCollector`

职责：

- 创建 streaming assistant 消息。
- 调用 `ChatProvider.streamChat(PromptBundle, ProviderConfig)`。
- 消费 `StreamEvent`。
- 文本增量实时追加到 conversation 并发出 `AgentEvent.StreamText`。
- 收集完整 assistant 文本和完整工具调用列表。
- 遇到 usage 事件时发布累计 usage。
- 遇到流错误时把本轮标记为 failed。

### agent.event

包含：

- `AgentEvent`
- `AgentEventSink`

事件类型：

- `StreamText`
- `ToolUseStarted`
- `ToolResultReady`
- `TurnComplete`
- `LoopComplete`
- `UsageUpdated`
- `ErrorOccurred`

职责是作为 Agent 到 UI/Orchestrator 的稳定契约，不包含渲染逻辑。

### agent.execution

包含：

- `AgentToolRunner`

职责：

- 使用 `ToolBatchPlanner` 分批工具调用。
- 对只读且并发安全的工具并发执行。
- 对写入、Bash、AskUserQuestion 或不并发安全工具串行执行。
- 每个工具调用先做权限判断。
- 工具执行完成后发出 `ToolResultReady`。
- 返回 `ToolExecutionRecord` 列表供 Agent Loop 写回 tool_result 消息。

### runtime

包含：

- `AgentMode`
- `AgentRunConfig`
- `CancellationToken`

职责：

- 表达 Default / Plan 模式。
- 保存 workDir、planFile、最大迭代次数、未知工具阈值和 Clock。
- 让 `tool`、`prompt`、`orchestrator` 可以使用运行模式而不依赖 `agent` 包。

## 循环流程

```text
ChatOrchestrator.submitUserMessage
  -> AgentLoop.run
  -> ConversationManager.addMessage(USER)
  -> while true
       -> cancellation check
       -> PromptContextBuilder.build
       -> AgentTurnRunner.runTurn
       -> StreamingTurnCollector collects text/tool/usage
       -> LoopDecisionMaker.decide
       -> Complete / Stop / ContinueWithTools
       -> AgentToolRunner.executeToolBatches
       -> ConversationManager.addUserToolResultMessage
```

## 停止条件

`LoopDecisionMaker` 集中处理：

- 用户取消。
- 本轮失败。
- 达到最大迭代次数。
- 连续未知工具达到阈值。
- 本轮没有工具调用，视为完成。
- 本轮有工具调用，继续执行并进入下一轮。

## Plan Mode

Plan Mode 的执行语义由第三章接入，具体 Prompt 注入由第四章 `prompt` 包提供。

- `/plan` 进入 `AgentMode.PLAN`。
- Plan Mode 暴露 `AskUserQuestion`。
- 写入指定 plan file 自动 ALLOW。
- 写非 plan 文件、执行 Bash 等仍按 Default 权限矩阵返回 ASK。
- `/do` 切回 `AgentMode.DEFAULT`，旧 Plan Mode 禁令不残留。

## 文件组织

```text
src/main/java/com/lunacode/
├── agent/
│   ├── event/
│   ├── execution/
│   └── turn/
├── runtime/
├── interaction/
├── orchestrator/
├── prompt/
├── provider/
└── tool/
```

## 测试与验收

- `DefaultAgentLoopTest`：多轮循环、工具回灌、权限 ASK 语义。
- `LoopDecisionMakerTest`：完成、继续、错误、取消、上限、未知工具保护。
- `AgentTurnRunnerTest`：单轮模型调用与 turn_complete。
- `StreamingTurnCollectorTest`：文本双路收集、tool_use 收集、usage 和错误事件。
- `ToolBatchPlannerTest`：只读并发、副作用串行。
- `ToolPermissionGatewayTest`：Default / Plan 权限语义。
- `DefaultChatOrchestratorTest`、`ToolOrchestratorTest`：TUI 适配层回归。
- `PackageDependencyTest`：验证 `provider/tool/prompt` 不反向依赖 `agent`。
- 全量回归：运行 `mvn test`。

