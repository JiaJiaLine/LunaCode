# LunaCode 系统提示结构化与缓存策略 Checklist

> 每一项都通过运行代码、检查请求体、观察事件或端到端行为来验证，聚焦系统行为，不依赖逐行阅读实现。

## 实现完整性

- [ ] 静态 System Prompt 能按固定顺序输出七个模块：角色设定、行为准则、工具使用指南、代码质量规范、安全边界、任务执行模式、输出风格（验证：运行 `StaticSystemPromptBuilderTest`，断言模块顺序和标题完整）。
- [ ] 静态 System Prompt 模块之间使用空行分隔，且相同输入多次渲染结果完全一致（验证：运行 `StaticSystemPromptBuilderTest`，比较两次渲染文本相等）。
- [ ] 静态 System Prompt 不包含工作目录、操作系统、当前时间、Git 状态、会话轮次、本轮用户输入、最近工具结果或 plan 文件路径（验证：运行 `StaticSystemPromptBuilderTest`，断言这些动态值不在静态文本中）。
- [ ] 输出风格模块包含原创 Luna 语气规则：优雅、从容、聪明、略高傲、适度毒舌但不侮辱、本质温柔、可称呼用户为“朝日”（验证：运行 `StaticSystemPromptBuilderTest`，断言输出风格模块包含对应关键词）。
- [ ] 输出风格模块包含技术准确性优先规则，不允许为了角色扮演牺牲正确性、工程判断或安全边界（验证：运行 `StaticSystemPromptBuilderTest`，断言包含“技术准确性优先”或等价规则）。
- [ ] 输出风格模块禁止直接引用已有作品台词、禁止声称自己是已有作品角色、禁止露骨/暧昧/成人化/过度恋爱化表达（验证：运行 `StaticSystemPromptBuilderTest`，断言包含这些禁止项）。
- [ ] 环境上下文能收集工作目录、操作系统、当前时间和 Git 状态（验证：运行 `SystemChannelTest`，断言 `EnvironmentContext` 字段完整）。
- [ ] Git 状态获取失败时不会中断 Agent Loop，而是返回 unknown 状态（验证：运行 `SystemChannelTest`，模拟 Git 探测失败，断言仍生成 EnvironmentContext）。
- [ ] `ProjectInstructionContext` 和 `MemoryContext` 已作为结构预留存在，但默认不读取 `LUNACODE.md`，不生成或召回记忆（验证：运行 `MessageChannelBuilderTest`，断言项目指令和记忆为空）。
- [ ] System Reminder 能渲染为系统级补充上下文，并明确不是用户请求（验证：运行 `SystemReminderBuilderTest`，断言渲染文本包含系统级提示语且不为空）。
- [ ] Plan Mode 首轮生成完整 System Reminder，包含先澄清需求、再探索代码、再写计划、避免实际修改（验证：运行 `PlanModeReminderPolicyTest`，检查第 1 轮内容）。
- [ ] Plan Mode 间隔轮次生成关键约束提醒，普通轮次生成精简提醒（验证：运行 `PlanModeReminderPolicyTest`，分别检查间隔轮次和普通轮次）。
- [ ] Default 模式不生成 Plan Mode 强约束，切回 Default 后不再携带规划禁令（验证：运行 `PlanModeReminderPolicyTest`，断言 Default 返回空或非 Plan reminder）。
- [ ] PromptBundle 能完整承载 system、tools、messages 和缓存策略（验证：运行 `PromptContextBuilderTest`，断言四个顶层字段存在）。
- [ ] PromptBundle 中 system 包含静态 System Prompt 和环境上下文，且两者在对象层面保持分离（验证：运行 `SystemChannelTest`，断言 staticPrompt 与 environmentContext 分别可取）。
- [ ] PromptBundle 中 tools 只包含工具 JSON Schema 和 description，不混入 System Reminder 或历史消息（验证：运行 `PromptContextBuilderTest`，断言 toolDeclarations 独立存在）。
- [ ] PromptBundle 中 messages 包含项目指令占位、自动记忆占位、System Reminder 和历史上下文，且历史上下文顺序不变（验证：运行 `MessageChannelBuilderTest`，检查消息顺序）。
- [ ] 工具描述包含双重强化规则：只读工具探索优先，写入/替换工具编辑前先读，Bash 优先让位给专用工具（验证：运行 `ToolDescriptionEnhancerTest`，检查 ReadFile、WriteFile、EditFile、Bash、Glob、Grep 的 description）。

## 集成

- [ ] Agent Loop 每轮调用 PromptContextBuilder 构建 PromptBundle，而不是直接拼接单个 systemPrompt 字符串（验证：运行 `DefaultAgentLoopTest`，使用测试 Provider 捕获 PromptBundle）。
- [ ] Anthropic 请求体正确映射 system、tools、messages：静态提示和环境上下文进入 system，工具描述进入 tools，项目指令/记忆/System Reminder/历史进入 messages（验证：运行 `AnthropicPromptAdapterTest`，检查 JSON 请求体）。
- [ ] OpenAI 请求体用等价结构表达 system、tools、messages，且工具调用格式保持可用（验证：运行 `OpenAiPromptAdapterTest`，检查 JSON 请求体和 tools 格式）。
- [ ] 支持缓存的 Provider 只把静态 System Prompt 和稳定工具描述作为缓存候选，不把环境上下文、System Reminder、项目指令、记忆或历史上下文放入静态缓存块（验证：运行 `AnthropicPromptAdapterTest`，检查缓存标记位置）。
- [ ] 不支持缓存或未返回缓存字段的 Provider 不会导致请求失败，缓存状态显示 unknown 或 unsupported（验证：运行 `ProviderCacheUsageTest`，传入无缓存字段 usage）。
- [ ] TokenUsage 能合并输入 token、输出 token、总 token、缓存读取 token、缓存写入 token 和缓存状态（验证：运行 `ProviderCacheUsageTest`，检查 merge 结果）。
- [ ] Anthropic 流式 usage 中的缓存字段能被解析并通过 usage 事件暴露（验证：运行 `ProviderCacheUsageTest`，输入包含缓存读写字段的 Anthropic 流事件）。
- [ ] OpenAI 流式 usage 缺少缓存字段时仍能解析普通 token 用量，并保留缓存状态 unknown 或 unsupported（验证：运行 `ProviderCacheUsageTest`，输入 OpenAI usage 事件）。
- [ ] Plan Mode 写指定 plan 文件时权限决策为 ALLOW（验证：运行 `ToolPermissionGatewayTest`，传入 WriteFile/EditFile 到 planFile）。
- [ ] Plan Mode 写非 plan 文件、执行 Bash 或其他需要确认的操作时权限决策保持 ASK，而不是 DENY（验证：运行 `ToolPermissionGatewayTest` 和 `DefaultAgentLoopTest`，断言 metadata 为 permission_required 而非 permission_denied）。
- [ ] AskUserQuestion 仍只在 Plan Mode 自动 ALLOW，Default 模式不可用或被拒绝（验证：运行 `ToolPermissionGatewayTest`）。
- [ ] 纯对话、工具调用、多轮 Agent Loop、Plan Mode 现有行为没有回退（验证：运行前三阶段已有测试类和 `mvn test`）。

## 编译与测试

- [ ] 项目编译无错误（验证：运行 `mvn test`，编译阶段通过）。
- [ ] 静态提示相关测试通过（验证：运行 `mvn -Dtest=StaticSystemPromptBuilderTest,SystemPromptBuilderTest test`，退出码为 0）。
- [ ] PromptBundle 分流相关测试通过（验证：运行 `mvn -Dtest=PromptContextBuilderTest,SystemChannelTest,MessageChannelBuilderTest test`，退出码为 0）。
- [ ] System Reminder 和 Plan Mode 轮次策略测试通过（验证：运行 `mvn -Dtest=SystemReminderBuilderTest,PlanModeReminderPolicyTest test`，退出码为 0）。
- [ ] Provider 请求体映射测试通过（验证：运行 `mvn -Dtest=AnthropicPromptAdapterTest,OpenAiPromptAdapterTest,AnthropicProviderSystemPromptTest,OpenAiProviderSystemPromptTest test`，退出码为 0）。
- [ ] 缓存用量解析测试通过（验证：运行 `mvn -Dtest=ProviderCacheUsageTest test`，退出码为 0）。
- [ ] 工具描述和权限测试通过（验证：运行 `mvn -Dtest=ToolDescriptionEnhancerTest,ToolPermissionGatewayTest test`，退出码为 0）。
- [ ] 全量单元测试通过（验证：运行 `mvn test`，退出码为 0）。

## 端到端场景

- [ ] 场景 1：普通纯文本对话仍流式显示（验证：在 tmux 中启动 LunaCode，输入“你好，简单介绍一下你能做什么”，观察 TUI 中 assistant 文本逐步显示且最终中文回复）。
- [ ] 场景 2：读取文件并总结依赖仍能完成工具调用和最终回复（验证：在 tmux 中输入“读取当前项目的 pom.xml 并总结依赖”，观察模型调用 ReadFile，工具结果回灌后生成中文总结）。
- [ ] 场景 3：Prompt 请求上下文按 system/tools/messages 分流（验证：使用测试日志或调试输出观察同一轮请求中 system 包含静态提示和环境上下文，tools 包含工具声明，messages 包含 System Reminder 和历史上下文）。
- [ ] 场景 4：改变当前时间或 Git 状态不会改变静态 System Prompt（验证：连续两轮普通对话，仅时间或 Git 状态变化，调试输出中静态提示文本一致，环境上下文变化）。
- [ ] 场景 5：支持缓存的 Provider 返回缓存命中或缓存读取指标（验证：使用支持缓存的 Provider 连续发送两轮相同静态提示请求，观察状态栏、日志或 AgentEvent 中出现 cache read/cache creation 等指标）。
- [ ] 场景 6：不支持缓存或未返回缓存字段时 Agent Loop 不报错（验证：使用不返回缓存字段的 Provider 运行普通对话，观察缓存状态为 unknown/unsupported 且回复正常完成）。
- [ ] 场景 7：Plan Mode 首轮通过 messages 注入完整规划约束（验证：在 tmux 中进入 Plan Mode 并输入规划请求，观察调试请求或行为表现为先澄清/探索/写计划，不把 Plan Mode 写进静态提示）。
- [ ] 场景 8：Plan Mode 写 plan 文件自动放行（验证：在 tmux 中让 Agent 生成计划文件，观察写入指定 plan 文件时不弹出确认或不返回权限错误）。
- [ ] 场景 9：Plan Mode 写非 plan 文件仍触发正常权限确认（验证：在 tmux 中让 Agent 在规划过程中尝试写非 plan 文件，观察出现与 Default 模式一致的 ask 权限确认或 permission_required 结果）。
- [ ] 场景 10：编辑任务优先读文件再修改（验证：在 tmux 中输入“修改某个文件中的函数实现”，观察模型先调用 ReadFile，再调用 EditFile/WriteFile，而不是直接盲改）。
- [ ] 场景 11：搜索任务优先使用专用工具（验证：在 tmux 中输入“搜索项目里某个类的用途”，观察模型优先使用 Grep/Glob，而不是直接使用 Bash 泛化命令）。
- [ ] 场景 12：输出语气遵守 Luna 规则且不牺牲技术正确性（验证：在 tmux 中输入一个技术问题，观察回复可带“朝日”等角色语气，但正文技术解释准确、清楚、无成人化或已有作品角色声明）。

## 人工对比评估

- [ ] 对比编辑前读文件行为：新旧提示下同样输入“修改一个文件中的函数实现”，新提示更稳定地先读文件再编辑（验证：记录两次工具调用顺序）。
- [ ] 对比专用工具优先行为：新旧提示下同样输入“搜索某个类的用途”，新提示更稳定地使用 Grep/Glob（验证：记录工具调用名称）。
- [ ] 对比 Plan Mode 遵守情况：新旧提示下同样进入 Plan Mode 规划改动，新提示更稳定地先澄清/探索/写计划（验证：记录模型动作顺序）。
- [ ] 对比输出风格：新提示生成的回复有 Luna 风格但不影响技术解释，新旧提示均不出现不安全或成人化表达（验证：人工阅读两组回复）。
- [ ] 对比缓存稳定性：新提示在动态环境变化时静态块保持一致，旧提示可能因为环境拼接导致整体变化（验证：比较两轮请求中的静态提示文本）。