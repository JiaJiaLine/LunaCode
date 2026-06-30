# LunaCode 01-04 Refactor 修改方案

## 涉及范围

本目录记录已经完成的 01-04 阶段职责重划分方案。涉及章节为：

- `01`：TUI、conversation、provider、stream、orchestrator 的对话内核。
- `02`：tool 接口、注册、执行、权限边界。
- `03`：Agent Loop、多轮执行、轮次状态、停止决策。
- `04`：结构化 Prompt、环境上下文、System Reminder、缓存策略。

这次重构的目标不是新增用户可见功能，而是把原先集中在 `agent` 包里的职责重新放回对应层级，消除 `provider -> agent`、`tool -> agent`、`prompt -> agent` 这类反向依赖。

## 架构目标

重构后的依赖方向保持为：

```text
app -> orchestrator -> agent
agent -> agent.turn / agent.event / agent.execution / prompt / provider / tool / conversation
provider -> prompt / conversation / stream
tool -> runtime / interaction
prompt -> runtime / conversation
tui -> orchestrator / conversation
```

明确禁止：

```text
provider -> agent
tool -> agent
prompt -> agent
```

## 新包职责

### `com.lunacode.runtime`

承载运行期基础对象，避免 tool/prompt 依赖 agent：

- `AgentMode`
- `AgentRunConfig`
- `CancellationToken`

这些类型表达运行模式、运行配置和取消信号，不包含 Agent Loop 行为。

### `com.lunacode.interaction`

承载用户交互通道：

- `UserQuestionRequest`
- `UserQuestionBroker`
- `BlockingUserQuestionBroker`
- `PermissionConfirmationRequest`
- `PermissionConfirmationBroker`
- `BlockingPermissionConfirmationBroker`

`AskUserQuestionTool` 和权限确认流程通过该包与 TUI/Orchestrator 交互，不再 import `agent`。

### `com.lunacode.prompt`

承载模型请求上下文：

- `PromptBundle`
- `PromptContextBuilder`
- `PromptCachePolicy`
- `SystemChannel`
- `MessageChannel`
- `StaticSystemPrompt*`
- `PromptSection*`
- `EnvironmentContext*`
- `GitStatusSnapshot`
- `SystemReminder*`
- `PlanModeReminderPolicy`
- `ProjectInstructionContext`
- `MemoryContext`
- `SystemPromptBuilder`

Prompt 负责 system/tools/messages/cache policy 的分流和组装。Agent Loop 只消费 `PromptContextBuilder` 输出，不再直接拼装 Prompt 细节。

### `com.lunacode.agent`

只保留 Agent Loop 主流程与停止决策：

- `AgentLoop`
- `DefaultAgentLoop`
- `AgentRequest`
- `LoopDecision`
- `LoopDecisionMaker`
- `LoopContext`

该包表达“跑一轮、判断、执行工具、继续或停止”，不承载 Prompt、工具执行细节或 UI 事件定义。

### `com.lunacode.agent.turn`

承载单轮模型调用：

- `AgentTurnRunner`
- `AgentTurnInput`
- `AgentTurnResult`
- `AgentTurnState`
- `StreamingTurnCollector`

职责是调用 provider、收集流式文本、收集 tool_use，并发出轮次相关事件。

### `com.lunacode.agent.event`

承载 Agent 对外事件契约：

- `AgentEvent`
- `AgentEventSink`

事件包括流式文本、工具开始、权限请求、工具结果、轮次结束、循环结束、usage 更新和错误。

### `com.lunacode.agent.execution`

承载工具批次执行：

- `AgentToolRunner`

职责包括工具批次规划调用、权限判断、权限确认、耗时记录、tool_result 事件发射。`DefaultAgentLoop` 不再直接包含 `executeToolBatches` / `executeOne` 逻辑。

### `com.lunacode.tool`

保留工具体系本体：

- `Tool`
- `ToolRegistry`
- `ToolExecutor`
- `ToolBatchPlanner`
- `ToolPermissionGateway`
- `DefaultToolPermissionGateway`
- `ReadFileTool`
- `WriteFileTool`
- `EditFileTool`
- `BashTool`
- `GlobTool`
- `GrepTool`
- `AskUserQuestionTool`

工具层只能依赖 `runtime` 和 `interaction`，不能依赖 `agent`。

## 关键修改方案

### 1. 运行期类型下沉

把 `AgentMode`、`AgentRunConfig`、`CancellationToken` 从 agent 语义中剥离到 `runtime`。这样 tool 权限网关、prompt 构建器和 Agent Loop 都能共享运行配置，同时不会造成反向依赖。

### 2. 用户问题与权限确认独立

把 AskUserQuestion 的问答通道放入 `interaction`。同时新增权限确认 broker，让写入类或破坏性工具在 `PermissionDecision.ASK` 时可以真正等待用户确认，而不是只返回“需要确认”但 TUI 没有确认入口。

确认流程为：

```text
AgentToolRunner
  -> emit PermissionRequested
  -> PermissionConfirmationBroker.confirm(...)
  -> Orchestrator 状态变为 waiting_permission
  -> TUI 显示 Luna [permission] ...
  -> 用户输入 yes/y/确认/允许
  -> Orchestrator 回填 broker
  -> 工具继续执行或跳过
```

### 3. Prompt 从 Agent 中抽离

`provider.ChatProvider` 改为消费 `PromptBundle`。`AnthropicPromptAdapter` 和 `OpenAiPromptAdapter` 使用 `prompt` 包内的 System Reminder、System Channel、Message Channel 和缓存策略，不再 import `agent`。

Agent Loop 只在每轮调用 `PromptContextBuilder`，不关心具体 system/developer/messages/tools 如何拆分。

### 4. Agent Loop 拆分 Turn、Event、Execution

`DefaultAgentLoop` 只表达主循环：

```text
build prompt
run turn
make loop decision
run tools when needed
append tool results
continue or stop
```

单轮调用由 `agent.turn` 负责，事件契约由 `agent.event` 负责，工具批次执行由 `agent.execution.AgentToolRunner` 负责。

### 5. Orchestrator 负责交互状态适配

`DefaultChatOrchestrator` 连接 TUI 与 Agent 事件：

- `ToolUseStarted` 转换为 `tool_running` 状态。
- `PermissionRequested` 转换为 `waiting_permission` 状态。
- 用户输入优先回填 pending permission，再回填 pending question，最后才作为新消息。
- `/cancel` 同时取消当前 token、pending question 和 pending permission。

TUI 只消费 `StatusSnapshot` 和 conversation snapshot，不直接理解工具执行细节。

### 6. TUI 显示用户可理解的运行状态

TUI 状态展示补充：

- `Luna [permission] ...`：显示权限确认内容。
- `Luna [tool] Luna正在使用"WriteFile"工具写入"xxx"`：显示工具运行摘要。
- `Luna [question] ...`：显示 AskUserQuestion 等待用户回答。

工具摘要在 Orchestrator 层生成，TUI 不解析工具 JSON。

### 7. 工具行为修复纳入工具层

`GlobTool` 的 `**/` 行为修复在 tool 层完成：

- `**/xxx` 支持匹配工作区根目录下的 `xxx`。
- `src/**/*.java` 支持匹配 `src/C.java` 和 `src/a/D.java`。

该修复属于工具实现细节，不影响 Agent Loop 架构边界。

## 测试方案

### 架构边界测试

- `PackageDependencyTest`：断言 `provider`、`tool`、`prompt` 不 import `com.lunacode.agent`。

### Agent Loop 与执行测试

- `DefaultAgentLoopTest`
- `AgentTurnRunnerTest`
- `StreamingTurnCollectorTest`
- `AgentToolRunnerTest`

重点验证多轮循环、tool_use 收集、权限确认和 tool_result 事件。

### Prompt 与 Provider 测试

- `PromptContextBuilderTest`
- `MessageChannelBuilderTest`
- `SystemChannelTest`
- `PlanModeReminderPolicyTest`
- `AnthropicPromptAdapterTest`
- `OpenAiPromptAdapterTest`
- `ProviderCacheUsageTest`

重点验证 system/tools/messages/cache policy 分流和 provider 请求体映射。

### Tool 测试

- `ToolPermissionGatewayTest`
- `AskUserQuestionToolTest`
- `GlobToolTest`
- `ReadFileToolTest`
- `WriteFileToolTest`
- `EditFileToolTest`
- `BashToolTest`
- `GrepToolTest`

重点验证权限矩阵、用户问答通道、权限确认通道和各工具自身行为。

### TUI/Orchestrator 测试

- `DefaultChatOrchestratorTest`
- `LanternaLunaTuiTest`
- `InputLineBufferTest`

重点验证：

- 权限请求会进入 `waiting_permission`。
- TUI 打印 `Luna [permission] ...`。
- 工具运行状态显示可读摘要。
- 普通用户输入、问题回答、权限确认不会互相抢占。

## 验收命令

```text
mvn test
git diff --check
```

端到端仍按项目约定使用 tmux 验收：

```text
1. 在 tmux 中启动 LunaCode
2. 输入普通对话
3. 触发 ReadFile 读取 pom.xml 并总结依赖
4. 触发 WriteFile/EditFile，确认 TUI 显示 Luna [permission] 并可输入确认
5. 触发 Glob 使用 **/toolTest/**/hello.java，确认能匹配根目录 toolTest
6. 使用 /plan 进入 Plan Mode，确认生成计划且不会直接执行写入
```

如果当前环境没有 tmux，则以 `mvn test` 和手动启动 TUI 作为替代验证，并在验收记录中说明限制。
