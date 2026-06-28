# LunaCode Agent Loop Checklist

> 每一项都通过运行代码、测试输出或观察 TUI 行为来验证，聚焦系统行为。

## 实现完整性

- [ ] Agent Loop 能接收一条用户消息并输出 `AgentEvent` 异步事件流（验证：运行 `mvn test -Dtest=DefaultAgentLoopTest`，看到 Agent Loop 输入输出测试通过）。
- [ ] 每一轮 Agent Loop 都通过独立状态机推进，并能产生本轮开始、模型流式输出、工具调用、工具结果和本轮完成等状态变化（验证：运行 `mvn test -Dtest=AgentTurnRunnerTest`，看到状态推进断言通过）。
- [ ] “是否继续循环”的判断集中在同一个决策器中，覆盖完成、迭代上限、取消、异常状态和下一批工具调用（验证：运行 `mvn test -Dtest=LoopDecisionMakerTest`，看到所有决策分支通过）。
- [ ] 每轮调用 Claude 时都会传入 System Prompt（验证：运行 `mvn test -Dtest=AgentTurnRunnerTest,AnthropicProviderSystemPromptTest`，看到 Provider 收到 system prompt 的断言通过）。
- [ ] System Prompt 包含 LunaCode 角色设定、当前工作目录、操作系统和当前时间（验证：运行 `mvn test -Dtest=SystemPromptBuilderTest`，看到 Default 模式 Prompt 内容断言通过）。
- [ ] Plan Mode 的 System Prompt 额外包含 Plan Mode 指令，Default 模式不包含该指令（验证：运行 `mvn test -Dtest=SystemPromptBuilderTest`，看到 Plan/Default 差异断言通过）。
- [ ] `stream_text` 事件携带文本增量，并且文本也被收集为本轮完整响应（验证：运行 `mvn test -Dtest=StreamingTurnCollectorTest`，看到实时事件和完整文本断言通过）。
- [ ] `tool_use` 事件携带工具名、工具输入和请求 ID（验证：运行 `mvn test -Dtest=StreamingTurnCollectorTest`，看到工具事件字段断言通过）。
- [ ] `tool_result` 事件携带工具结果、是否出错和耗时（验证：运行 `mvn test -Dtest=DefaultAgentLoopTest`，看到工具结果事件包含耗时断言通过）。
- [ ] `usage` 事件携带累计输入 token 和累计输出 token（验证：运行 `mvn test -Dtest=StreamingTurnCollectorTest`，看到累计 usage 断言通过）。
- [ ] 每轮模型调用结束时发布 `turn_complete`，整个循环结束时发布 `loop_complete`（验证：运行 `mvn test -Dtest=AgentTurnRunnerTest,DefaultAgentLoopTest`，看到轮次和循环完成事件断言通过）。
- [ ] 多个只读且并发安全的工具调用会被分到同一个并发批次（验证：运行 `mvn test -Dtest=ToolBatchPlannerTest`，看到 ReadFile/Grep/Glob 并发批次断言通过）。
- [ ] 写文件、改文件、Bash 和不并发安全工具会串行执行（验证：运行 `mvn test -Dtest=ToolBatchPlannerTest`，看到副作用工具串行批次断言通过）。
- [ ] 工具执行失败会以结构化 tool_result 回灌，不导致进程崩溃（验证：运行 `mvn test -Dtest=DefaultAgentLoopTest`，看到失败工具结果回灌断言通过）。
- [ ] 模型请求不存在、拼写错误或已禁用工具时，先返回结构化错误结果让模型调整（验证：运行 `mvn test -Dtest=DefaultAgentLoopTest`，看到首次未知工具返回 tool_result 断言通过）。
- [ ] 连续未知工具达到阈值后 Agent Loop 提前终止（验证：运行 `mvn test -Dtest=DefaultAgentLoopTest,LoopDecisionMakerTest`，看到连续未知工具停止断言通过）。
- [ ] 用户取消信号能从 orchestrator 传到 Agent Loop，并在下一轮开始前停止后续调用（验证：运行 `mvn test -Dtest=DefaultAgentLoopTest,AgentOrchestratorEventBridgeTest`，看到取消不再进入下一轮断言通过）。
- [ ] Plan Mode 暴露 `AskUserQuestion` 工具，Default 模式不暴露该工具（验证：运行 `mvn test -Dtest=ToolRegistryTest`，看到模式化工具声明断言通过）。
- [ ] `AskUserQuestion` 一次只提出一个问题，并把用户回答作为工具结果回灌（验证：运行 `mvn test -Dtest=AskUserQuestionToolTest,DefaultAgentLoopTest`，看到问题和回答回灌断言通过）。
- [ ] `AskUserQuestion` 不触发权限审批，也不能作为 `/do` 执行确认（验证：运行 `mvn test -Dtest=ToolPermissionGatewayTest,AskUserQuestionToolTest`，看到 AskUserQuestion 权限和语义断言通过）。
- [ ] Plan Mode 下指定 plan file 写入自动放行（验证：运行 `mvn test -Dtest=ToolPermissionGatewayTest`，看到 plan file 写入 ALLOW 断言通过）。
- [ ] Plan Mode 下非 plan 文件写入仍按 Default 矩阵触发 ASK（验证：运行 `mvn test -Dtest=ToolPermissionGatewayTest`，看到非 plan 文件写入 ASK 断言通过）。
- [ ] Plan Mode 下 Bash 仍按 Default 矩阵触发 ASK（验证：运行 `mvn test -Dtest=ToolPermissionGatewayTest`，看到 Bash ASK 断言通过）。

## 集成

- [ ] `ChatProvider` 新接口兼容旧重载，旧 Provider 调用点仍能编译（验证：运行 `mvn test -Dtest=ChatProviderFactoryTest`，看到测试通过）。
- [ ] Anthropic 请求体包含顶层 `system` 字段，并且不影响 messages、tools、thinking（验证：运行 `mvn test -Dtest=AnthropicProviderSystemPromptTest,ProviderRequestBodyTest`，看到请求体断言通过）。
- [ ] OpenAI 请求体第一条消息是 system，并且普通对话消息仍按原格式发送（验证：运行 `mvn test -Dtest=OpenAiProviderSystemPromptTest,ProviderRequestBodyTest`，看到请求体断言通过）。
- [ ] Claude tool_use 流式参数仍能拼接完整 JSON（验证：运行 `mvn test -Dtest=ClaudeToolUseStreamMapperTest`，看到工具 JSON 拼接测试通过）。
- [ ] Conversation API 格式仍保持 tool_use 和 tool_result ID 配对（验证：运行 `mvn test -Dtest=ToolMessageFormatTest`，看到格式转换测试通过）。
- [ ] Orchestrator 能把 AgentEvent 映射到 conversation 和 status（验证：运行 `mvn test -Dtest=AgentOrchestratorEventBridgeTest`，看到事件桥接测试通过）。
- [ ] `/plan` 会进入 Plan Mode 并保留 plan file 上下文（验证：运行 `mvn test -Dtest=AgentOrchestratorEventBridgeTest`，看到 `/plan` 路由测试通过）。
- [ ] `/do` 会切回 Default 模式，并基于已有 plan 上下文执行（验证：运行 `mvn test -Dtest=AgentOrchestratorEventBridgeTest`，看到 `/do` 路由测试通过）。
- [ ] 当存在待回答的 AskUserQuestion 时，用户输入会作为回答提交，不会作为新任务启动（验证：运行 `mvn test -Dtest=AgentOrchestratorEventBridgeTest`，看到澄清回答路由测试通过）。
- [ ] TUI 能显示 waiting_user 状态，引导用户回答澄清问题（验证：启动 LunaCode，触发 AskUserQuestion，观察界面显示澄清问题并等待输入）。
- [ ] TUI 能显示工具名、输入摘要、工具结果和耗时（验证：启动 LunaCode，触发 ReadFile 或 Grep，观察工具状态包含工具名、目标和耗时）。
- [ ] 状态栏能显示累计 token 用量，未知时显示明确的未知状态（验证：启动 LunaCode，观察模型响应期间状态栏 token 更新或显示 `?`）。
- [ ] `/cancel` 或取消按键能让当前 Agent Loop 停止后续轮次（验证：启动 LunaCode，提交长任务后取消，观察状态变为 cancelled 且不再继续调用工具）。
- [ ] 第二章“读取当前项目的 pom.xml 并总结依赖”场景仍能运行（验证：在 LunaCode 中输入该请求，观察读文件工具执行和最终中文总结）。

## 编译与测试

- [ ] 项目编译无错误（验证：运行 `mvn test -DskipTests`，命令退出码为 0）。
- [ ] Agent 模块单元测试全部通过（验证：运行 `mvn test -Dtest=SystemPromptBuilderTest,LoopDecisionMakerTest,StreamingTurnCollectorTest,AgentTurnRunnerTest,DefaultAgentLoopTest`，命令退出码为 0）。
- [ ] Provider 请求体和流解析测试全部通过（验证：运行 `mvn test -Dtest=AnthropicProviderSystemPromptTest,OpenAiProviderSystemPromptTest,ProviderRequestBodyTest,ClaudeToolUseStreamMapperTest`，命令退出码为 0）。
- [ ] 工具分批、权限网关和 AskUserQuestion 测试全部通过（验证：运行 `mvn test -Dtest=ToolBatchPlannerTest,ToolPermissionGatewayTest,AskUserQuestionToolTest`，命令退出码为 0）。
- [ ] Orchestrator 和 TUI 相关测试全部通过（验证：运行 `mvn test -Dtest=AgentOrchestratorEventBridgeTest,DefaultChatOrchestratorTest,ToolOrchestratorTest,InputLineBufferTest`，命令退出码为 0）。
- [ ] 现有工具测试全部通过，确认第二章能力不回退（验证：运行 `mvn test -Dtest=ReadFileToolTest,WriteFileToolTest,EditFileToolTest,BashToolTest,GlobToolTest,GrepToolTest,ToolRegistryTest`，命令退出码为 0）。
- [ ] 全量自动化测试通过（验证：运行 `mvn test`，命令退出码为 0）。
- [ ] 打包通过（验证：运行 `mvn package -DskipTests`，命令退出码为 0，`target` 下生成 jar）。

## 端到端场景

- [ ] 场景 1：普通多轮 Agent Loop。用户输入“检查当前项目里和工具执行有关的测试，并总结哪些测试覆盖了失败路径”，LunaCode 自动搜索、读取多个文件并给出最终中文总结（验证：在 tmux 中启动 LunaCode，观察多轮 tool_use/tool_result 和最终回复）。
- [ ] 场景 2：Plan Mode 需求澄清。用户输入 `/plan 帮我加一个导出功能`，LunaCode 调用 AskUserQuestion 询问导出格式或范围，用户回答后 LunaCode 继续规划（验证：在 tmux 中观察 AskUserQuestion 问题、用户回答回灌和后续计划生成）。
- [ ] 场景 3：Plan Mode 写 plan file。用户完成澄清后，LunaCode 把计划写入指定 plan file，不触发写入确认（验证：在 tmux 中观察计划文件生成，并检查文件内容包含实现方案）。
- [ ] 场景 4：Plan Mode 非 plan 文件写入。模型尝试写入非 plan 文件时，LunaCode 走 Default 权限确认，不静默写入（验证：在 tmux 中观察写入确认提示；拒绝后目标文件未改变）。
- [ ] 场景 5：`/do` 执行计划。用户在已有 plan 后输入 `/do`，LunaCode 切回 Default 模式并基于计划执行工具（验证：在 tmux 中观察 System Prompt 不再包含 Plan Mode 指令，并出现执行类工具调用）。
- [ ] 场景 6：用户取消。用户提交一个需要多轮工具调用的任务，在第一轮结束后触发 `/cancel` 或取消按键，LunaCode 在下一轮开始前停止（验证：在 tmux 中观察 cancelled 状态，后续无新模型调用和工具批次）。
- [ ] 场景 7：连续未知工具。使用测试 Provider 模拟模型连续请求不存在工具，LunaCode 先回灌错误结果，达到阈值后终止循环（验证：运行对应集成测试或测试模式，观察异常状态终止说明）。
- [ ] 场景 8：流式错误。使用测试 Provider 模拟流式错误，当前 assistant 消息进入 error，Agent Loop 停止并显示可理解错误摘要（验证：运行对应集成测试或测试模式，观察 error 状态）。
- [ ] 场景 9：第二章回归。用户输入“读取当前项目的 pom.xml 并总结依赖”，LunaCode 能调用 ReadFile 并用工具结果生成最终中文回复（验证：在 tmux 中观察 ReadFile 工具调用、工具结果和最终回复）。