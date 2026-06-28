# LunaCode Agent Loop Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `src/main/java/com/lunacode/agent/AgentMode.java` | 定义 Default 和 Plan 两种 Agent 模式。 |
| 新建 | `src/main/java/com/lunacode/agent/AgentRunConfig.java` | 保存单次 Agent 运行配置。 |
| 新建 | `src/main/java/com/lunacode/agent/AgentRequest.java` | 表示 Agent Loop 输入。 |
| 新建 | `src/main/java/com/lunacode/agent/AgentEvent.java` | 定义 Agent 对 UI 输出的事件流类型。 |
| 新建 | `src/main/java/com/lunacode/agent/AgentEventSink.java` | 定义事件发布接口。 |
| 新建 | `src/main/java/com/lunacode/agent/CancellationToken.java` | 表示可传播的取消信号。 |
| 新建 | `src/main/java/com/lunacode/agent/UserQuestionRequest.java` | 表示 Plan Mode 需求澄清请求。 |
| 新建 | `src/main/java/com/lunacode/agent/UserQuestionBroker.java` | 定义 AskUserQuestion 与 UI 的往返接口。 |
| 新建 | `src/main/java/com/lunacode/agent/AgentTurnState.java` | 定义单轮状态机状态。 |
| 新建 | `src/main/java/com/lunacode/agent/AgentTurnInput.java` | 表示单轮模型调用输入。 |
| 新建 | `src/main/java/com/lunacode/agent/AgentTurnResult.java` | 表示单轮模型调用结果。 |
| 新建 | `src/main/java/com/lunacode/agent/LoopContext.java` | 保存跨轮循环状态。 |
| 新建 | `src/main/java/com/lunacode/agent/LoopDecision.java` | 定义继续或停止的决策结果。 |
| 新建 | `src/main/java/com/lunacode/agent/LoopDecisionMaker.java` | 集中判断 Agent Loop 是否继续。 |
| 新建 | `src/main/java/com/lunacode/agent/SystemPromptConfig.java` | 表示 System Prompt 构建输入。 |
| 新建 | `src/main/java/com/lunacode/agent/SystemPromptBuilder.java` | 构建 Default 和 Plan Mode 的 System Prompt。 |
| 新建 | `src/main/java/com/lunacode/agent/StreamingTurnCollector.java` | 双路收集 Provider 流式事件。 |
| 新建 | `src/main/java/com/lunacode/agent/AgentTurnRunner.java` | 执行一轮状态机。 |
| 新建 | `src/main/java/com/lunacode/agent/AgentLoop.java` | 定义 Agent Loop 主接口。 |
| 新建 | `src/main/java/com/lunacode/agent/DefaultAgentLoop.java` | 实现多轮 ReAct 循环。 |
| 新建 | `src/main/java/com/lunacode/config/AgentConfig.java` | 定义 Agent 配置默认值和字段。 |
| 修改 | `src/main/java/com/lunacode/config/ConfigLoader.java` | 读取 agent 配置。 |
| 修改 | `src/main/java/com/lunacode/config/ProviderConfig.java` | 携带 AgentConfig 或提供配置聚合入口。 |
| 修改 | `src/main/java/com/lunacode/provider/ChatProvider.java` | 增加带 System Prompt 的 streamChat 重载。 |
| 修改 | `src/main/java/com/lunacode/provider/AnthropicProvider.java` | 在 Claude 请求体写入顶层 system 字段。 |
| 修改 | `src/main/java/com/lunacode/provider/OpenAiProvider.java` | 在 OpenAI 请求消息前插入 system 消息。 |
| 新建 | `src/main/java/com/lunacode/tool/ToolBatch.java` | 表示可并发或串行工具批次。 |
| 新建 | `src/main/java/com/lunacode/tool/ToolBatchPlanner.java` | 按工具安全性分批。 |
| 新建 | `src/main/java/com/lunacode/tool/PermissionDecision.java` | 表示工具权限判断结果。 |
| 新建 | `src/main/java/com/lunacode/tool/ToolPermissionGateway.java` | 定义工具权限网关。 |
| 新建 | `src/main/java/com/lunacode/tool/DefaultToolPermissionGateway.java` | 实现 Default/Plan Mode 的权限矩阵和 plan file 自动放行。 |
| 新建 | `src/main/java/com/lunacode/tool/ToolExecutionRecord.java` | 保存工具执行结果和耗时。 |
| 新建 | `src/main/java/com/lunacode/tool/AskUserQuestionTool.java` | 实现 Plan Mode 需求澄清工具。 |
| 修改 | `src/main/java/com/lunacode/tool/ToolExecutionContext.java` | 增加 UserQuestionBroker。 |
| 修改 | `src/main/java/com/lunacode/tool/DefaultToolRegistry.java` | 支持按 AgentMode 输出工具声明。 |
| 修改 | `src/main/java/com/lunacode/tool/DefaultToolExecutor.java` | 支持权限网关、耗时记录和 AskUserQuestion。 |
| 修改 | `src/main/java/com/lunacode/orchestrator/ChatOrchestrator.java` | 增加取消入口。 |
| 修改 | `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` | 改为 AgentLoop 适配层。 |
| 修改 | `src/main/java/com/lunacode/orchestrator/StatusSnapshot.java` | 增加 cancelled、waiting_user、轮次、工具耗时等状态表达。 |
| 修改 | `src/main/java/com/lunacode/tui/LanternaLunaTui.java` | 支持取消和 AskUserQuestion 回答输入。 |
| 修改 | `src/main/java/com/lunacode/app/LunaCodeApplication.java` | 组装 AgentLoop、AgentConfig、AskUserQuestionTool 和权限网关。 |
| 新建 | `src/test/java/com/lunacode/agent/SystemPromptBuilderTest.java` | 验证 System Prompt。 |
| 新建 | `src/test/java/com/lunacode/agent/LoopDecisionMakerTest.java` | 验证继续决策。 |
| 新建 | `src/test/java/com/lunacode/agent/StreamingTurnCollectorTest.java` | 验证双路流式收集和 AgentEvent。 |
| 新建 | `src/test/java/com/lunacode/agent/AgentTurnRunnerTest.java` | 验证单轮状态机。 |
| 新建 | `src/test/java/com/lunacode/agent/DefaultAgentLoopTest.java` | 验证多轮循环、停止条件和 AskUserQuestion 回灌。 |
| 新建 | `src/test/java/com/lunacode/provider/AnthropicProviderSystemPromptTest.java` | 验证 Claude system 字段。 |
| 新建 | `src/test/java/com/lunacode/provider/OpenAiProviderSystemPromptTest.java` | 验证 OpenAI system 消息。 |
| 新建 | `src/test/java/com/lunacode/tool/ToolBatchPlannerTest.java` | 验证工具分批。 |
| 新建 | `src/test/java/com/lunacode/tool/ToolPermissionGatewayTest.java` | 验证权限矩阵和 plan file 自动放行。 |
| 新建 | `src/test/java/com/lunacode/tool/AskUserQuestionToolTest.java` | 验证需求澄清工具。 |
| 新建 | `src/test/java/com/lunacode/orchestrator/AgentOrchestratorEventBridgeTest.java` | 验证 orchestrator 事件桥接。 |
| 修改 | 现有相关测试 | 适配构造函数、Provider 接口和 ToolExecutionContext 字段变化。 |

## T1: 建立 Agent 基础模型

**文件：** `src/main/java/com/lunacode/agent/AgentMode.java`、`AgentRunConfig.java`、`AgentRequest.java`、`AgentEvent.java`、`AgentEventSink.java`、`CancellationToken.java`、`UserQuestionRequest.java`、`UserQuestionBroker.java`  
**依赖：** 无  
**步骤：**
1. 新建 `agent` 包。
2. 定义 `AgentMode`，包含 `DEFAULT` 和 `PLAN`。
3. 定义 `AgentRunConfig`，包含工作目录、模式、plan file、最大迭代数、连续未知工具阈值和 Clock。
4. 定义 `AgentRequest`，包含用户消息和运行配置。
5. 定义 `AgentEvent` sealed interface，包含 `StreamText`、`ToolUseStarted`、`ToolResultReady`、`TurnComplete`、`LoopComplete`、`UsageUpdated`、`ErrorOccurred`。
6. 定义 `AgentEventSink.emit(AgentEvent event)`。
7. 定义 `CancellationToken`，提供 `cancel()` 和 `isCancellationRequested()`。
8. 定义 `UserQuestionRequest` 和 `UserQuestionBroker`。

**验证：** 运行 `mvn test -DskipTests`，期望编译通过。

## T2: 增加 Agent 配置

**文件：** `src/main/java/com/lunacode/config/AgentConfig.java`、`ConfigLoader.java`、`ProviderConfig.java`  
**依赖：** T1  
**步骤：**
1. 新建 `AgentConfig`，包含 `maxIterations`、`maxConsecutiveUnknownTools`、`planFile`。
2. 为 `AgentConfig` 提供默认值：8、3、`.lunacode/plan.md`。
3. 修改 `ConfigLoader.RawConfig`，读取可选 `agent` 节点。
4. 修改配置返回结构，使应用启动时能拿到 `AgentConfig`。
5. 保持缺少 `agent` 配置时旧配置文件仍可加载。

**验证：** 运行 `mvn test -Dtest=ConfigLoaderTest`，期望现有配置测试通过，并新增默认 agent 配置测试通过。

## T3: 实现 System Prompt 构建器

**文件：** `src/main/java/com/lunacode/agent/SystemPromptConfig.java`、`SystemPromptBuilder.java`、`src/test/java/com/lunacode/agent/SystemPromptBuilderTest.java`  
**依赖：** T1  
**步骤：**
1. 定义 `SystemPromptConfig`，包含工作目录、操作系统、当前时间、模式和 plan file。
2. 实现 `SystemPromptBuilder.build`。
3. Default 模式输出角色设定和环境信息。
4. Plan Mode 额外追加 Plan Mode 指令。
5. Plan Mode 指令包含 AskUserQuestion 澄清需求、读类探索、写入 plan file、等待确认。
6. 确保 Default 模式不包含 Plan Mode 指令。

**验证：** 运行 `mvn test -Dtest=SystemPromptBuilderTest`，期望 Default/Plan 两种 Prompt 内容断言通过。

## T4: 扩展 ChatProvider System Prompt 接口

**文件：** `src/main/java/com/lunacode/provider/ChatProvider.java`  
**依赖：** T3  
**步骤：**
1. 增加 `streamChat(messages, config, enabledTools, systemPrompt)` 方法。
2. 保留现有重载作为 default 方法，避免旧调用点立即崩溃。
3. 明确空 System Prompt 时保持旧行为。

**验证：** 运行 `mvn test -Dtest=ChatProviderFactoryTest`，期望编译和现有 Provider 创建测试通过。

## T5: Anthropic 请求写入 System Prompt

**文件：** `src/main/java/com/lunacode/provider/AnthropicProvider.java`、`src/test/java/com/lunacode/provider/AnthropicProviderSystemPromptTest.java`  
**依赖：** T4  
**步骤：**
1. 修改 `streamChat` 调用链，接收 `systemPrompt`。
2. 修改 `buildRequestBody`，当 System Prompt 非空时写入顶层 `system` 字段。
3. 保持 messages、tools、thinking 原有行为不变。
4. 新增测试断言 Claude 请求体包含 `system` 字段。
5. 新增测试断言 tools 与 thinking 同时存在时不被 system 覆盖。

**验证：** 运行 `mvn test -Dtest=AnthropicProviderSystemPromptTest,ProviderRequestBodyTest`，期望全部通过。

## T6: OpenAI 请求写入 System Prompt

**文件：** `src/main/java/com/lunacode/provider/OpenAiProvider.java`、`src/test/java/com/lunacode/provider/OpenAiProviderSystemPromptTest.java`  
**依赖：** T4  
**步骤：**
1. 修改 `streamChat` 调用链，接收 `systemPrompt`。
2. 修改请求体构造，在 messages 第一项写入 `role=system`。
3. 保持 OpenAI 现有纯对话流式行为。
4. 新增测试断言第一条消息是 system。

**验证：** 运行 `mvn test -Dtest=OpenAiProviderSystemPromptTest,ProviderRequestBodyTest`，期望全部通过。

## T7: 实现循环决策模型

**文件：** `src/main/java/com/lunacode/agent/LoopContext.java`、`LoopDecision.java`、`LoopDecisionMaker.java`、`src/test/java/com/lunacode/agent/LoopDecisionMakerTest.java`  
**依赖：** T1  
**步骤：**
1. 定义 `LoopContext`，保存运行配置、取消信号、当前轮次、连续未知工具计数和累计 usage。
2. 定义 `LoopDecision` sealed interface。
3. 实现 `LoopDecisionMaker.decide`。
4. 按顺序处理取消、失败、迭代上限、未知工具阈值、无工具完成、有工具继续。
5. 添加覆盖每个决策分支的单元测试。

**验证：** 运行 `mvn test -Dtest=LoopDecisionMakerTest`，期望所有分支通过。

## T8: 实现单轮状态数据结构

**文件：** `src/main/java/com/lunacode/agent/AgentTurnState.java`、`AgentTurnInput.java`、`AgentTurnResult.java`  
**依赖：** T1  
**步骤：**
1. 定义 `AgentTurnState`，包含 STARTING、STREAMING_MODEL、COLLECTING_TOOL_USE、EXECUTING_TOOLS、RECORDING_RESULTS、COMPLETED、FAILED、CANCELLED。
2. 定义 `AgentTurnInput`。
3. 定义 `AgentTurnResult`。
4. 确保字段满足流式收集、继续决策和测试断言。

**验证：** 运行 `mvn test -DskipTests`，期望编译通过。

## T9: 实现 StreamingTurnCollector

**文件：** `src/main/java/com/lunacode/agent/StreamingTurnCollector.java`、`src/test/java/com/lunacode/agent/StreamingTurnCollectorTest.java`  
**依赖：** T1、T8  
**步骤：**
1. 接收 Provider `Stream<StreamEvent>`、assistant 消息 ID、event sink 和初始 usage。
2. 收到文本增量时追加 conversation，并发出 `AgentEvent.StreamText`。
3. 同时累积完整文本到本轮缓冲。
4. 收到 `StreamEvent.ToolUse` 时追加 tool_use 内容块，并发出 `AgentEvent.ToolUseStarted`。
5. 收到 usage 相关事件时合并累计 usage，并发出 `AgentEvent.UsageUpdated`。
6. 收到 `StreamEvent.Error` 时标记 FAILED，并发出 `AgentEvent.ErrorOccurred`。
7. 正常结束时返回 COMPLETED 的 `AgentTurnResult`。

**验证：** 运行 `mvn test -Dtest=StreamingTurnCollectorTest`，期望文本双路收集、工具收集、usage 事件和错误事件测试通过。

## T10: 实现 AgentTurnRunner

**文件：** `src/main/java/com/lunacode/agent/AgentTurnRunner.java`、`src/test/java/com/lunacode/agent/AgentTurnRunnerTest.java`  
**依赖：** T3、T4、T8、T9  
**步骤：**
1. 创建 streaming assistant 消息。
2. 调用 `ChatProvider.streamChat(messages, providerConfig, enabledTools, systemPrompt)`。
3. 使用 `StreamingTurnCollector` 消费流事件。
4. 本轮结束时发出 `AgentEvent.TurnComplete`。
5. 测试 provider 收到 System Prompt。
6. 测试本轮完成事件包含轮次序号。

**验证：** 运行 `mvn test -Dtest=AgentTurnRunnerTest`，期望通过。

## T11: 实现工具分批规划

**文件：** `src/main/java/com/lunacode/tool/ToolBatch.java`、`ToolBatchPlanner.java`、`src/test/java/com/lunacode/tool/ToolBatchPlannerTest.java`  
**依赖：** 现有 `Tool`、`ToolRegistry`  
**步骤：**
1. 定义 `ToolBatch`。
2. 实现 `ToolBatchPlanner.plan`。
3. 只读且并发安全工具合并为并发批次。
4. 写文件、改文件、Bash、不并发安全工具拆成串行批次。
5. `AskUserQuestion` 强制串行。
6. 未知工具保留为串行批次，交给执行器返回错误。

**验证：** 运行 `mvn test -Dtest=ToolBatchPlannerTest`，期望并发批、串行批、混合批、未知工具和 AskUserQuestion 测试通过。

## T12: 实现权限网关

**文件：** `src/main/java/com/lunacode/tool/PermissionDecision.java`、`ToolPermissionGateway.java`、`DefaultToolPermissionGateway.java`、`src/test/java/com/lunacode/tool/ToolPermissionGatewayTest.java`  
**依赖：** T1、现有 `Tool`  
**步骤：**
1. 定义 `PermissionDecision`。
2. 定义 `ToolPermissionGateway.decide`。
3. 实现 Default 矩阵：读类 ALLOW，写类 ASK，Bash ASK。
4. 实现 Plan Mode 矩阵与 Default 一致。
5. 实现 Plan Mode 指定 plan file 写入 ALLOW。
6. 实现 AskUserQuestion 在 Plan Mode ALLOW，在 Default 不暴露或返回不可用。
7. 增加路径归一化，确保 plan file 自动放行只匹配同一规范化路径。

**验证：** 运行 `mvn test -Dtest=ToolPermissionGatewayTest`，期望权限矩阵、plan file 自动放行和 AskUserQuestion 权限测试通过。

## T13: 实现 AskUserQuestionTool

**文件：** `src/main/java/com/lunacode/tool/AskUserQuestionTool.java`、`ToolExecutionContext.java`、`src/test/java/com/lunacode/tool/AskUserQuestionToolTest.java`  
**依赖：** T1  
**步骤：**
1. 扩展 `ToolExecutionContext`，增加 `UserQuestionBroker` 字段。
2. 更新现有构造调用点和测试工具上下文。
3. 实现 `AskUserQuestionTool` 的名称、描述、schema、权限标记和参数校验。
4. `execute` 调用 `UserQuestionBroker.ask`。
5. 用户回答包装为 `ToolResult.success`。
6. broker 缺失、问题为空、用户取消时返回 `ToolResult.error`。

**验证：** 运行 `mvn test -Dtest=AskUserQuestionToolTest,ReadFileToolTest,WriteFileToolTest,EditFileToolTest,BashToolTest,GlobToolTest,GrepToolTest`，期望全部通过。

## T14: 支持按模式输出工具声明

**文件：** `src/main/java/com/lunacode/tool/ToolRegistry.java`、`DefaultToolRegistry.java`、`src/test/java/com/lunacode/tool/ToolRegistryTest.java`  
**依赖：** T1、T13  
**步骤：**
1. 增加按 `AgentMode` 输出工具声明的方法。
2. Default 模式输出现有工具，不包含 AskUserQuestion。
3. Plan Mode 输出现有工具并额外包含 AskUserQuestion。
4. 保留现有 `toAPIFormat()` 行为，默认用于 Default 模式。
5. 更新注册中心测试。

**验证：** 运行 `mvn test -Dtest=ToolRegistryTest`，期望 Default/Plan 工具声明测试通过。

## T15: 扩展工具执行器返回执行记录

**文件：** `src/main/java/com/lunacode/tool/ToolExecutionRecord.java`、`DefaultToolExecutor.java`  
**依赖：** T12、T13  
**步骤：**
1. 定义 `ToolExecutionRecord`。
2. 在执行工具时记录开始和结束时间。
3. 保留现有 `execute(ToolUse)` 返回 `ToolResult` 的兼容方法。
4. 增加返回 `ToolExecutionRecord` 的方法供 Agent Loop 使用。
5. 识别 `tool_not_found` metadata，保持错误结果结构。
6. 对 ASK 和 DENY 生成结构化错误或待确认结果，不静默执行。

**验证：** 运行 `mvn test -Dtest=ToolRegistryTest,AskUserQuestionToolTest`，并运行 `mvn test -Dtest=ToolOrchestratorTest`，期望兼容旧工具执行路径。

## T16: 实现 DefaultAgentLoop 主循环

**文件：** `src/main/java/com/lunacode/agent/AgentLoop.java`、`DefaultAgentLoop.java`、`src/test/java/com/lunacode/agent/DefaultAgentLoopTest.java`  
**依赖：** T7、T10、T11、T15  
**步骤：**
1. 定义 `AgentLoop.run`。
2. `DefaultAgentLoop` 添加用户消息并初始化 LoopContext。
3. 每轮开始前检查取消信号。
4. 每轮构建 System Prompt。
5. 每轮调用 `AgentTurnRunner`。
6. 对工具调用使用 `ToolBatchPlanner` 分批。
7. 并发批使用 ExecutorService 并行执行，只在批次内并发。
8. 串行批按顺序执行。
9. 工具结果写入同一条 user tool_result 消息。
10. 更新连续未知工具计数。
11. 调用 `LoopDecisionMaker` 决定继续或停止。
12. 停止时发出 `LoopComplete`。

**验证：** 运行 `mvn test -Dtest=DefaultAgentLoopTest`，期望多轮工具回灌、无工具完成、迭代上限、取消、未知工具连续终止和 AskUserQuestion 回灌测试通过。

## T17: 改造 DefaultChatOrchestrator 为事件桥

**文件：** `src/main/java/com/lunacode/orchestrator/ChatOrchestrator.java`、`DefaultChatOrchestrator.java`、`StatusSnapshot.java`、`src/test/java/com/lunacode/orchestrator/AgentOrchestratorEventBridgeTest.java`  
**依赖：** T16  
**步骤：**
1. `ChatOrchestrator` 增加 `cancelCurrentRun()`。
2. `DefaultChatOrchestrator` 移除内联两段式工具流程。
3. 提交用户消息时创建 `AgentRequest` 和 `CancellationToken`。
4. 以后台线程运行 `AgentLoop`。
5. 实现 `AgentEventSink`，把事件映射到 conversation 和 status。
6. `StreamText` 追加 assistant 文本。
7. `ToolUseStarted` 更新工具状态。
8. `ToolResultReady` 更新工具结果状态和耗时。
9. `UsageUpdated` 更新 token。
10. `ErrorOccurred` 标记 assistant error。
11. `LoopComplete` 设置 idle 或 cancelled。
12. 处理 AskUserQuestion 待回答状态。

**验证：** 运行 `mvn test -Dtest=AgentOrchestratorEventBridgeTest,DefaultChatOrchestratorTest,ToolOrchestratorTest`，期望新旧编排测试通过。

## T18: 处理 /plan、/do 和澄清回答路由

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`StatusSnapshot.java`  
**依赖：** T17  
**步骤：**
1. 解析 `/plan ...`，创建 Plan Mode AgentRequest。
2. 解析 `/do`，切回 Default 模式，并保留最近 plan file 上下文。
3. 当前存在 AskUserQuestion 待回答时，把用户输入交给 `UserQuestionBroker`。
4. AskUserQuestion 回答不作为新的 user task 添加。
5. 取消时释放待回答状态。
6. status 增加 `waiting_user` 或等价状态。

**验证：** 运行 `mvn test -Dtest=AgentOrchestratorEventBridgeTest`，期望 `/plan`、`/do`、澄清回答路由和取消待回答状态测试通过。

## T19: 更新 TUI 取消与问题回答体验

**文件：** `src/main/java/com/lunacode/tui/LanternaLunaTui.java`、`LunaTui.java`、`src/test/java/com/lunacode/tui/InputLineBufferTest.java`  
**依赖：** T17、T18  
**步骤：**
1. 添加 `/cancel` 输入处理，调用 `orchestrator.cancelCurrentRun()`。
2. 响应中按 Esc 时优先取消当前运行；空闲时保持退出行为。
3. 当状态为 `waiting_user` 时，提示用户回答澄清问题。
4. 用户输入在 waiting_user 状态下提交给 orchestrator，作为 AskUserQuestion 回答。
5. 工具状态显示工具名、输入摘要和耗时。
6. 状态栏显示累计 token。

**验证：** 运行 `mvn test -Dtest=InputLineBufferTest`，并手动启动 TUI，期望 `/cancel` 和 waiting_user 提示行为可观察。

## T20: 更新应用组装

**文件：** `src/main/java/com/lunacode/app/LunaCodeApplication.java`  
**依赖：** T2、T13、T16、T17  
**步骤：**
1. 读取 AgentConfig。
2. 创建 UserQuestionBroker 实现并传入 ToolExecutionContext。
3. 注册 AskUserQuestionTool。
4. 创建 ToolPermissionGateway、ToolBatchPlanner、AgentTurnRunner、DefaultAgentLoop。
5. 创建新的 DefaultChatOrchestrator。
6. 确保 API Key 仍加入 SensitiveValueMasker。

**验证：** 运行 `mvn test -DskipTests`，期望编译通过。

## T21: 更新现有测试兼容新构造器

**文件：** 现有 `src/test/java/com/lunacode/**` 测试  
**依赖：** T20  
**步骤：**
1. 修复 `ToolExecutionContext` 增加字段后的测试构造。
2. 修复 `ChatProvider` 新重载后的测试 double。
3. 修复 `DefaultChatOrchestrator` 构造变化后的测试。
4. 保持第二章工具系统测试语义不变。

**验证：** 运行 `mvn test`，期望所有自动化测试通过。

## T22: 回归 Provider 和工具流格式

**文件：** `src/test/java/com/lunacode/provider/*`、`src/test/java/com/lunacode/stream/*`、`src/test/java/com/lunacode/conversation/*`  
**依赖：** T21  
**步骤：**
1. 运行 Provider 请求体测试，确认 system、tools、thinking 共存。
2. 运行 Claude tool_use 流解析测试，确认工具调用仍能收集完整 JSON。
3. 运行 conversation tool message 测试，确认 tool_use/tool_result 配对不回退。
4. 补充必要断言，确保 Agent Loop 多轮不会破坏 API message 格式。

**验证：** 运行 `mvn test -Dtest=ProviderRequestBodyTest,AnthropicProviderSystemPromptTest,OpenAiProviderSystemPromptTest,ClaudeToolUseStreamMapperTest,ToolMessageFormatTest`，期望全部通过。

## T23: 端到端场景准备

**文件：** `spec/03/checklist.md` 生成前不改代码  
**依赖：** T21、T22  
**步骤：**
1. 准备本章 checklist 需要的 tmux 场景草案。
2. 场景覆盖普通多轮工具循环。
3. 场景覆盖 `/plan` 使用 AskUserQuestion 澄清需求。
4. 场景覆盖 `/plan` 写 plan file 后 `/do` 执行。
5. 场景覆盖取消和未知工具连续终止。

**验证：** 本任务只准备验收场景，不运行代码；后续 checklist 阶段逐项写入并验收。

## T24: 全量自动化验证

**文件：** 全项目  
**依赖：** T1-T22  
**步骤：**
1. 运行 `mvn test`。
2. 若失败，按失败测试修复实现或测试夹具。
3. 运行 `mvn package -DskipTests`。
4. 确认构建产物生成。

**验证：** `mvn test` 通过，`mvn package -DskipTests` 通过。

## 执行顺序

```text
T1 -> T2 -> T3
T1 -> T7 -> T8 -> T9 -> T10
T4 -> T5 -> T6
T11 -> T12 -> T13 -> T14 -> T15
T3 + T10 + T11 + T15 -> T16
T16 -> T17 -> T18 -> T19 -> T20
T20 -> T21 -> T22 -> T24
T21 -> T23
```