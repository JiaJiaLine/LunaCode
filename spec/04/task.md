# LunaCode 系统提示结构化与缓存策略 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `src/main/java/com/lunacode/agent/PromptSectionKind.java` | 定义静态 System Prompt 七模块枚举 |
| 新建 | `src/main/java/com/lunacode/agent/PromptSection.java` | 表示静态提示模块 |
| 新建 | `src/main/java/com/lunacode/agent/StaticSystemPrompt.java` | 持有并渲染静态 System Prompt |
| 新建 | `src/main/java/com/lunacode/agent/StaticSystemPromptBuilder.java` | 构建七模块静态提示文本 |
| 修改 | `src/main/java/com/lunacode/agent/SystemPromptBuilder.java` | 保留兼容外观或委托新静态提示构建器 |
| 新建 | `src/main/java/com/lunacode/agent/EnvironmentContext.java` | 表示动态环境上下文 |
| 新建 | `src/main/java/com/lunacode/agent/GitStatusSnapshot.java` | 表示 Git 状态摘要 |
| 新建 | `src/main/java/com/lunacode/agent/EnvironmentContextCollector.java` | 收集工作目录、操作系统、时间、Git 状态 |
| 新建 | `src/main/java/com/lunacode/agent/SystemChannel.java` | 承载 system 内容：静态提示和环境上下文 |
| 新建 | `src/main/java/com/lunacode/agent/ProjectInstructionContext.java` | 预留 `LUNACODE.md` 项目指令上下文 |
| 新建 | `src/main/java/com/lunacode/agent/MemoryContext.java` | 预留自动记忆上下文 |
| 新建 | `src/main/java/com/lunacode/agent/SystemReminderKind.java` | 定义 System Reminder 类型 |
| 新建 | `src/main/java/com/lunacode/agent/SystemReminder.java` | 表示系统级补充消息 |
| 新建 | `src/main/java/com/lunacode/agent/ModeInjectionState.java` | 表示模式轮次注入状态 |
| 新建 | `src/main/java/com/lunacode/agent/PlanModeReminderPolicy.java` | 生成 Plan Mode 完整/间隔/精简指令 |
| 新建 | `src/main/java/com/lunacode/agent/SystemReminderBuilder.java` | 生成本轮 System Reminder 列表 |
| 新建 | `src/main/java/com/lunacode/agent/SystemReminderRenderer.java` | 把 System Reminder 渲染为 messages 内容 |
| 新建 | `src/main/java/com/lunacode/agent/MessageChannel.java` | 承载 messages 内容：项目指令、记忆、Reminder、历史 |
| 新建 | `src/main/java/com/lunacode/agent/MessageChannelBuilder.java` | 按固定顺序构建 messages 内容 |
| 新建 | `src/main/java/com/lunacode/agent/PromptCachePolicy.java` | 表示静态提示和工具描述缓存策略 |
| 新建 | `src/main/java/com/lunacode/agent/PromptBundle.java` | 表示完整 Prompt 请求上下文 |
| 新建 | `src/main/java/com/lunacode/agent/PromptContextBuilder.java` | 收集七类来源并分流到 system/tools/messages |
| 修改 | `src/main/java/com/lunacode/agent/AgentTurnInput.java` | 将 `systemPrompt` 字符串改为或兼容 `PromptBundle` |
| 修改 | `src/main/java/com/lunacode/agent/AgentTurnRunner.java` | 调用新的 `ChatProvider.streamChat(PromptBundle, ...)` |
| 修改 | `src/main/java/com/lunacode/agent/DefaultAgentLoop.java` | 每轮构建 PromptBundle，并修正 Plan Mode ASK 执行语义 |
| 新建 | `src/main/java/com/lunacode/provider/ProviderPromptAdapter.java` | 定义 Provider 请求体适配接口 |
| 新建 | `src/main/java/com/lunacode/provider/AnthropicPromptAdapter.java` | 映射 PromptBundle 到 Anthropic system/tools/messages |
| 新建 | `src/main/java/com/lunacode/provider/OpenAiPromptAdapter.java` | 映射 PromptBundle 到 OpenAI 等价 system/tools/messages |
| 修改 | `src/main/java/com/lunacode/provider/ChatProvider.java` | 增加 PromptBundle 调用入口并保留兼容重载 |
| 修改 | `src/main/java/com/lunacode/provider/AnthropicProvider.java` | 使用 AnthropicPromptAdapter 构建请求体 |
| 修改 | `src/main/java/com/lunacode/provider/OpenAiProvider.java` | 使用 OpenAiPromptAdapter 构建请求体 |
| 修改 | `src/main/java/com/lunacode/conversation/TokenUsage.java` | 增加缓存读取/写入 token 和缓存状态 |
| 新建 | `src/main/java/com/lunacode/conversation/CacheUsageStatus.java` | 表示缓存状态：SUPPORTED、UNSUPPORTED、UNKNOWN |
| 修改 | `src/main/java/com/lunacode/stream/AnthropicStreamMapper.java` | 解析 Anthropic 缓存用量字段 |
| 修改 | `src/main/java/com/lunacode/stream/OpenAiStreamMapper.java` | 兼容 OpenAI usage 缓存字段缺失或等价字段 |
| 新建 | `src/main/java/com/lunacode/tool/ToolDescriptionEnhancer.java` | 统一强化工具 description |
| 修改 | `src/main/java/com/lunacode/tool/DefaultToolRegistry.java` | 输出增强后的工具描述 |
| 修改 | `src/main/java/com/lunacode/tool/DefaultToolPermissionGateway.java` | 确认 Plan Mode plan 文件自动放行，其他操作 ASK |
| 新建 | `src/test/java/com/lunacode/agent/StaticSystemPromptBuilderTest.java` | 测试七模块静态提示和输出语气规则 |
| 新建 | `src/test/java/com/lunacode/agent/PromptContextBuilderTest.java` | 测试 PromptBundle 分流 |
| 新建 | `src/test/java/com/lunacode/agent/SystemChannelTest.java` | 测试 system 内容边界 |
| 新建 | `src/test/java/com/lunacode/agent/MessageChannelBuilderTest.java` | 测试 messages 顺序和占位上下文 |
| 新建 | `src/test/java/com/lunacode/agent/SystemReminderBuilderTest.java` | 测试 System Reminder 注入 |
| 新建 | `src/test/java/com/lunacode/agent/PlanModeReminderPolicyTest.java` | 测试 Plan Mode 轮次策略 |
| 新建 | `src/test/java/com/lunacode/provider/AnthropicPromptAdapterTest.java` | 测试 Anthropic 请求体映射 |
| 新建 | `src/test/java/com/lunacode/provider/OpenAiPromptAdapterTest.java` | 测试 OpenAI 请求体映射 |
| 新建 | `src/test/java/com/lunacode/provider/ProviderCacheUsageTest.java` | 测试缓存用量解析 |
| 新建 | `src/test/java/com/lunacode/tool/ToolDescriptionEnhancerTest.java` | 测试工具描述强化 |
| 修改 | `src/test/java/com/lunacode/agent/SystemPromptBuilderTest.java` | 调整旧测试到新静态提示语义或兼容外观 |
| 修改 | `src/test/java/com/lunacode/agent/DefaultAgentLoopTest.java` | 覆盖 PromptBundle 调用与 Plan Mode ASK 语义 |
| 修改 | `src/test/java/com/lunacode/provider/AnthropicProviderSystemPromptTest.java` | 更新为 system/tools/messages 映射断言 |
| 修改 | `src/test/java/com/lunacode/provider/OpenAiProviderSystemPromptTest.java` | 更新为 system/tools/messages 映射断言 |

## T1: 定义静态提示模块基础类型

**文件：** `src/main/java/com/lunacode/agent/PromptSectionKind.java`、`src/main/java/com/lunacode/agent/PromptSection.java`、`src/main/java/com/lunacode/agent/StaticSystemPrompt.java`

**依赖：** 无

**步骤：**
1. 新建 `PromptSectionKind` 枚举，按顺序定义角色设定、行为准则、工具使用指南、代码质量规范、安全边界、任务执行模式、输出风格。
2. 新建 `PromptSection` record，包含 kind、title、content。
3. 新建 `StaticSystemPrompt` record，保存 `List<PromptSection>`。
4. 实现 `render()`，按枚举顺序输出各模块，模块之间用空行分隔。
5. 在构造或渲染时拒绝空标题、空内容和缺失模块。

**验证：** 新增或运行 `StaticSystemPromptBuilderTest` 中的基础渲染断言，期望七模块顺序稳定且模块之间有空行。

## T2: 实现七模块静态提示构建器

**文件：** `src/main/java/com/lunacode/agent/StaticSystemPromptBuilder.java`、`src/main/java/com/lunacode/agent/SystemPromptBuilder.java`

**依赖：** T1

**步骤：**
1. 新建 `StaticSystemPromptBuilder`，输出七个固定模块。
2. 在角色设定中说明 LunaCode 是终端 AI 编程助手。
3. 在行为准则中说明先读、分析、行动、验证的工作方式。
4. 在工具使用指南中写入专用工具优先、工具结果必须作为观察依据。
5. 在代码质量规范中写入小步修改、中文注释、测试和可维护性要求。
6. 在安全边界中写入工作区边界、敏感信息保护、不得编造未观察结果。
7. 在任务执行模式中写入 Default 与 Plan Mode 的高层差异，但不注入本轮 Plan Mode 动态指令。
8. 在输出风格中写入中文回复、简洁、有证据的最终说明。
9. 在输出风格中写入原创角色 Luna 的语气要求：优雅、从容、聪明、略带高傲、适度毒舌但不侮辱、本质温柔，经常称呼用户为“朝日”。
10. 在输出风格中写入技术准确性优先规则：不得为了角色扮演牺牲正确性，不直接引用已有作品台词，不声称自己是已有作品角色，不使用露骨、暧昧、成人化或过度恋爱化表达。
11. 将旧 `SystemPromptBuilder` 改为兼容外观，委托 `StaticSystemPromptBuilder` 或只用于旧测试过渡。

**验证：** 运行 `StaticSystemPromptBuilderTest`，期望静态提示包含七个模块且不包含工作目录、时间、Git 状态和 plan 文件路径；输出风格模块包含 Luna 语气、称呼“朝日”、技术准确性优先和禁止成人化/引用已有作品台词等规则。

## T3: 增加动态环境上下文

**文件：** `src/main/java/com/lunacode/agent/EnvironmentContext.java`、`src/main/java/com/lunacode/agent/GitStatusSnapshot.java`、`src/main/java/com/lunacode/agent/EnvironmentContextCollector.java`

**依赖：** 无

**步骤：**
1. 新建 `EnvironmentContext` record，包含 workDir、osName、now、gitStatus。
2. 新建 `GitStatusSnapshot` record，包含是否在 Git 仓库、分支名、是否有改动、状态摘要。
3. 实现 `EnvironmentContextCollector.collect(AgentRunConfig config)`。
4. 从 `AgentRunConfig` 和系统属性获取工作目录、操作系统、当前时间。
5. 用轻量方式获取 Git 状态；获取失败时返回 unknown 状态，不抛出到 Agent Loop。

**验证：** 运行 `SystemChannelTest` 中的环境上下文断言，期望 workDir/os/now/gitStatus 可被收集，Git 探测失败时仍返回上下文。

## T4: 建立 SystemChannel

**文件：** `src/main/java/com/lunacode/agent/SystemChannel.java`

**依赖：** T1、T3

**步骤：**
1. 新建 `SystemChannel` record，包含 `StaticSystemPrompt` 和 `EnvironmentContext`。
2. 保证 `StaticSystemPrompt` 与 `EnvironmentContext` 在对象层面分开保存。
3. 提供简单校验，禁止 staticPrompt 或 environmentContext 为空。

**验证：** 运行 `SystemChannelTest`，期望 system 中同时有静态提示和环境上下文，且静态提示渲染结果不包含环境字段。

## T5: 预留项目指令和自动记忆上下文

**文件：** `src/main/java/com/lunacode/agent/ProjectInstructionContext.java`、`src/main/java/com/lunacode/agent/MemoryContext.java`

**依赖：** 无

**步骤：**
1. 新建 `ProjectInstructionContext` record，包含 sourcePath 和 content。
2. 新建 `MemoryContext` record，包含 userPreferences 和 projectFacts。
3. 不实现 `LUNACODE.md` 文件读取，不实现记忆写入或召回。
4. 在类注释或测试名中明确本阶段只是结构预留。

**验证：** 运行 `MessageChannelBuilderTest`，期望默认 MessageChannel 中项目指令和记忆为空，不访问文件系统。

## T6: 实现 System Reminder 类型和渲染

**文件：** `src/main/java/com/lunacode/agent/SystemReminderKind.java`、`src/main/java/com/lunacode/agent/SystemReminder.java`、`src/main/java/com/lunacode/agent/SystemReminderRenderer.java`

**依赖：** 无

**步骤：**
1. 新建 `SystemReminderKind`，至少包含 ENVIRONMENT、PLAN_MODE、MCP_HINT、TEMPORARY_CONSTRAINT。
2. 新建 `SystemReminder` record，包含 kind、content、turnIndex。
3. 新建 `SystemReminderRenderer`，把 reminder 渲染成系统级上下文文本。
4. 采用实现层统一标签格式，但保持内部可替换，不把格式散落在各模块。
5. 确保渲染文本明确“这是系统级补充上下文，不是用户请求”。

**验证：** 运行 `SystemReminderBuilderTest`，期望渲染结果包含系统级提示语，不会生成空 reminder 文本。

## T7: 实现 Plan Mode 轮次策略

**文件：** `src/main/java/com/lunacode/agent/ModeInjectionState.java`、`src/main/java/com/lunacode/agent/PlanModeReminderPolicy.java`、`src/main/java/com/lunacode/agent/SystemReminderBuilder.java`

**依赖：** T6

**步骤：**
1. 新建 `ModeInjectionState` record，包含 mode、turnIndex、planFile、repeatInterval。
2. 新建 `PlanModeReminderPolicy`。
3. 实现首轮完整 Plan Mode 指令，包含先澄清、再探索、再写计划、避免实际修改。
4. 实现间隔轮次关键约束提醒。
5. 实现其他轮次精简提醒。
6. 实现 Default 模式不生成 Plan Mode reminder。
7. 用 `SystemReminderBuilder` 汇总本轮 reminders。

**验证：** 运行 `PlanModeReminderPolicyTest`，期望 turn 1 完整、间隔轮次包含关键约束、普通轮次精简、Default 为空。

## T8: 建立 MessageChannel

**文件：** `src/main/java/com/lunacode/agent/MessageChannel.java`、`src/main/java/com/lunacode/agent/MessageChannelBuilder.java`

**依赖：** T5、T6、T7

**步骤：**
1. 新建 `MessageChannel` record，包含 projectInstructions、memory、reminders、history。
2. 新建 `MessageChannelBuilder`。
3. 默认不加载 `LUNACODE.md`，projectInstructions 为 empty。
4. 默认不加载记忆，memory 为 empty。
5. 将 System Reminder 排在历史上下文之前。
6. 保持历史上下文中 user、assistant、tool 消息原有顺序。

**验证：** 运行 `MessageChannelBuilderTest`，期望 messages 顺序为项目指令占位、记忆占位、reminder、history，且默认不读取任何项目指令文件。

## T9: 建立 PromptBundle 和缓存策略

**文件：** `src/main/java/com/lunacode/agent/PromptCachePolicy.java`、`src/main/java/com/lunacode/agent/PromptBundle.java`、`src/main/java/com/lunacode/agent/PromptContextBuilder.java`

**依赖：** T2、T3、T4、T8

**步骤：**
1. 新建 `PromptCachePolicy`，包含 cacheStaticSystemPrompt 和 cacheToolDeclarations。
2. 新建 `PromptBundle`，包含 SystemChannel、toolDeclarations、MessageChannel、PromptCachePolicy。
3. 新建 `PromptContextBuilder`，统一构建 PromptBundle。
4. 在 builder 中调用静态提示构建、环境收集、MessageChannel 构建。
5. 将工具声明原样放入 tools 位置，不放入 messages。
6. 默认打开静态提示和工具声明的缓存意图。

**验证：** 运行 `PromptContextBuilderTest`，期望静态提示和环境上下文在 system，工具描述在 tools，System Reminder 和历史在 messages。

## T10: 强化工具描述

**文件：** `src/main/java/com/lunacode/tool/ToolDescriptionEnhancer.java`、`src/main/java/com/lunacode/tool/DefaultToolRegistry.java`

**依赖：** 无

**步骤：**
1. 新建 `ToolDescriptionEnhancer`。
2. 对 ReadFile、Glob、Grep 等只读工具追加探索优先说明。
3. 对 WriteFile、EditFile 追加编辑前先读、基于当前内容修改说明。
4. 对 Bash 追加优先使用专用工具、缺少专用工具时再用命令说明。
5. 在 `DefaultToolRegistry.toAPIFormat(...)` 输出 description 时调用 enhancer。
6. 保持工具名称和 input_schema 不变。

**验证：** 运行 `ToolDescriptionEnhancerTest`，期望各类工具 description 包含对应强化规则。

## T11: 扩展 Provider 接口

**文件：** `src/main/java/com/lunacode/provider/ChatProvider.java`、`src/main/java/com/lunacode/agent/AgentTurnInput.java`、`src/main/java/com/lunacode/agent/AgentTurnRunner.java`

**依赖：** T9

**步骤：**
1. 在 `ChatProvider` 增加 `streamChat(PromptBundle promptBundle, ProviderConfig config)`。
2. 保留旧的字符串重载，内部适配为兼容路径，减少现有测试一次性崩坏。
3. 修改 `AgentTurnInput`，携带 PromptBundle 或同时兼容旧 systemPrompt 字段。
4. 修改 `AgentTurnRunner`，优先调用 PromptBundle 入口。
5. 保持流事件收集逻辑不变。

**验证：** 运行 `DefaultAgentLoopTest` 中与模型调用相关的测试，期望 AgentTurnRunner 能把 PromptBundle 传给 Provider。

## T12: 修改 Agent Loop 构建 PromptBundle

**文件：** `src/main/java/com/lunacode/agent/DefaultAgentLoop.java`

**依赖：** T9、T11

**步骤：**
1. 用 `PromptContextBuilder` 替换每轮 `SystemPromptBuilder.build(...)`。
2. 把 turnIndex、AgentRunConfig、conversation history、tool declarations 传入 builder。
3. 确保每轮都重新收集环境上下文，但静态提示文本保持稳定。
4. 保持 LoopDecisionMaker、工具执行和历史回灌流程不变。

**验证：** 运行 `DefaultAgentLoopTest`，期望每轮模型调用获得 PromptBundle，且已有多轮工具循环行为不回退。

## T13: 修正 Plan Mode ASK 权限执行语义

**文件：** `src/main/java/com/lunacode/tool/DefaultToolPermissionGateway.java`、`src/main/java/com/lunacode/agent/DefaultAgentLoop.java`

**依赖：** 无

**步骤：**
1. 确认 `DefaultToolPermissionGateway` 对 Plan Mode 写 plan 文件返回 ALLOW。
2. 确认 Plan Mode 写非 plan 文件和 Bash 返回 ASK，而不是 DENY。
3. 修改 `DefaultAgentLoop.executeOne(...)` 中对 ASK 的处理，避免只在 Default 模式下执行 ASK 语义。
4. 如果当前版本没有真实权限确认 UI，则保持返回“需要用户确认”的结构化结果，但不把 Plan Mode 的 ASK 变成 DENY。
5. 保持 AskUserQuestion 只在 Plan Mode 自动 ALLOW。

**验证：** 运行 `ToolPermissionGatewayTest` 和 `DefaultAgentLoopTest`，期望 Plan Mode plan 文件 ALLOW，非 plan 写入和 Bash 为 ASK/permission_required，不是 permission_denied。

## T14: 实现 Anthropic Prompt 适配器

**文件：** `src/main/java/com/lunacode/provider/ProviderPromptAdapter.java`、`src/main/java/com/lunacode/provider/AnthropicPromptAdapter.java`、`src/main/java/com/lunacode/provider/AnthropicProvider.java`

**依赖：** T9、T11

**步骤：**
1. 新建 `ProviderPromptAdapter` 接口。
2. 新建 `AnthropicPromptAdapter`。
3. 将 StaticSystemPrompt 和 EnvironmentContext 映射到 Anthropic `system`。
4. 将 toolDeclarations 映射到 Anthropic `tools`。
5. 将项目指令、记忆、System Reminder 和历史上下文映射到 `messages`。
6. 在支持缓存时，为静态 System Prompt 和稳定工具声明添加 Anthropic 可识别的缓存标记。
7. 修改 `AnthropicProvider` 使用 adapter 构建请求体。

**验证：** 运行 `AnthropicPromptAdapterTest` 和 `AnthropicProviderSystemPromptTest`，期望请求体中 system/tools/messages 三类内容落位正确。

## T15: 实现 OpenAI Prompt 适配器

**文件：** `src/main/java/com/lunacode/provider/OpenAiPromptAdapter.java`、`src/main/java/com/lunacode/provider/OpenAiProvider.java`

**依赖：** T9、T11、T14

**步骤：**
1. 新建 `OpenAiPromptAdapter`。
2. 将 StaticSystemPrompt 和 EnvironmentContext 映射为 OpenAI 等价 system/developer 消息。
3. 将 toolDeclarations 映射为 OpenAI `tools`。
4. 将项目指令、记忆、System Reminder 和历史上下文映射到普通 messages。
5. 对缓存不支持或未返回缓存字段的情况设置 unsupported/unknown 状态，不让请求失败。
6. 修改 `OpenAiProvider` 使用 adapter 构建请求体。

**验证：** 运行 `OpenAiPromptAdapterTest` 和 `OpenAiProviderSystemPromptTest`，期望请求体中 system/tools/messages 语义正确，旧工具调用格式不回退。

## T16: 扩展缓存用量模型

**文件：** `src/main/java/com/lunacode/conversation/CacheUsageStatus.java`、`src/main/java/com/lunacode/conversation/TokenUsage.java`

**依赖：** 无

**步骤：**
1. 新建 `CacheUsageStatus` 枚举，包含 SUPPORTED、UNSUPPORTED、UNKNOWN。
2. 扩展 `TokenUsage` 字段，增加 cacheReadInputTokens、cacheCreationInputTokens、cacheStatus。
3. 保持 `unknown()` 可用。
4. 更新 `merge(...)`，让新字段按较新非空值合并。
5. 保持现有 input/output/total token 行为兼容。

**验证：** 运行现有 TokenUsage 相关测试和 `ProviderCacheUsageTest`，期望旧用量合并不变，新缓存字段可合并。

## T17: 解析 Provider 缓存字段

**文件：** `src/main/java/com/lunacode/stream/AnthropicStreamMapper.java`、`src/main/java/com/lunacode/stream/OpenAiStreamMapper.java`

**依赖：** T16

**步骤：**
1. 在 Anthropic usage 解析中读取 cache read / cache creation 等等价字段。
2. 在 OpenAI usage 解析中兼容可能的缓存字段；缺失时设置 UNKNOWN 或 UNSUPPORTED。
3. 保持没有 usage 字段时返回 `TokenUsage.unknown()`。
4. 确保 MessageStart、MessageDelta、MessageStop 的 usage 合并路径不丢失缓存字段。

**验证：** 运行 `ProviderCacheUsageTest`，期望含缓存字段的 JSON 能解析，缺失字段时 Agent Loop 不报错。

## T18: 更新 Provider 和 Agent 旧测试

**文件：** `src/test/java/com/lunacode/agent/SystemPromptBuilderTest.java`、`src/test/java/com/lunacode/provider/AnthropicProviderSystemPromptTest.java`、`src/test/java/com/lunacode/provider/OpenAiProviderSystemPromptTest.java`

**依赖：** T11、T14、T15

**步骤：**
1. 将旧 system prompt 测试调整为静态提示不含环境上下文。
2. 增加输出风格断言，确认静态提示包含 Luna 原创语气、称呼“朝日”、适度毒舌但不侮辱、技术准确性优先。
3. 增加 system channel 测试，确认环境上下文仍进入 Provider system 语义位置。
4. 更新 Anthropic 请求体断言，确认 system/tools/messages 分流。
5. 更新 OpenAI 请求体断言，确认等价 system/tools/messages 分流。
6. 删除或替换与旧单字符串 systemPrompt 绑定的断言。

**验证：** 运行上述三个测试类，期望全部通过。

## T19: 增加 PromptBundle 分流测试

**文件：** `src/test/java/com/lunacode/agent/PromptContextBuilderTest.java`、`src/test/java/com/lunacode/agent/SystemChannelTest.java`、`src/test/java/com/lunacode/agent/MessageChannelBuilderTest.java`

**依赖：** T3、T4、T8、T9

**步骤：**
1. 测试静态提示和环境上下文都进入 SystemChannel。
2. 测试工具描述只进入 toolDeclarations。
3. 测试 System Reminder 和历史上下文进入 MessageChannel。
4. 测试默认不会读取 `LUNACODE.md`。
5. 测试默认不会产生自动记忆内容。
6. 测试改变时间或 Git 状态不改变静态提示文本。

**验证：** 运行三个测试类，期望分流断言全部通过。

## T20: 增加 System Reminder 和 Plan Mode 测试

**文件：** `src/test/java/com/lunacode/agent/SystemReminderBuilderTest.java`、`src/test/java/com/lunacode/agent/PlanModeReminderPolicyTest.java`

**依赖：** T6、T7

**步骤：**
1. 测试 Default 模式不生成 Plan Mode reminder。
2. 测试 Plan Mode 首轮生成完整指令。
3. 测试间隔轮次生成关键约束指令。
4. 测试其他轮次生成精简提醒。
5. 测试 reminder 渲染后明确不是用户请求。
6. 测试切回 Default 后不再携带 Plan Mode 强约束。

**验证：** 运行两个测试类，期望所有轮次策略断言通过。

## T21: 运行回归测试

**文件：** 全项目测试

**依赖：** T1-T20

**步骤：**
1. 运行 `mvn test`。
2. 如果出现编译错误，先修复接口迁移造成的调用点问题。
3. 如果出现旧断言失败，判断是否应更新为新 system/tools/messages 语义。
4. 确认前三阶段相关测试仍通过。

**验证：** `mvn test` 退出码为 0。

## T22: 运行端到端验收准备场景

**文件：** `spec/04/checklist.md` 生成后配合执行

**依赖：** T21、checklist.md

**步骤：**
1. 在 tmux 中启动 LunaCode。
2. 输入普通对话，观察流式回复是否正常。
3. 输入读取文件并总结的请求，观察工具调用和最终中文回复。
4. 输入 Plan Mode 规划请求，观察 Plan Mode reminder、plan 文件写入和权限行为。
5. 输入需要修改文件的请求，观察模型是否先读再改。
6. 对照 checklist.md 逐项记录结果。

**验证：** tmux 端到端场景中能观察到 system/tools/messages 分流后核心行为不回退，并按 checklist.md 逐项通过。

## 执行顺序

```text
T1 -> T2
T3 -> T4
T5 -> T6 -> T7 -> T8
T2 + T4 + T8 -> T9
T10
T9 + T10 -> T11 -> T12
T13
T11 -> T14 -> T15
T16 -> T17
T14 + T15 -> T18
T9 -> T19
T7 -> T20
T1-T20 -> T21 -> T22
```