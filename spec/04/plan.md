# LunaCode 系统提示结构化与缓存策略 Plan

## 架构概览

本阶段把当前单一字符串式 `SystemPromptBuilder` 拆成“提示上下文收集 -> system/tools/messages 分流 -> Provider 请求适配 -> 用量与缓存观测”四层。完整的 Prompt 请求上下文包含七类信息来源：静态 System Prompt、环境上下文、工具描述、项目指令文件、自动记忆、System Reminder、历史上下文。

请求落位规则固定如下：静态 System Prompt 和环境上下文放入 system；工具描述放入 tools；项目指令文件、自动记忆、System Reminder 和历史上下文放入 messages。静态 System Prompt 是稳定规则，环境上下文是动态 system 内容，两者同属 system 语义但必须在内部结构中分离，避免动态环境污染静态缓存块。

Agent Loop 每轮不再只拿一个 `String systemPrompt` 调 Provider，而是先构建一个完整的 `PromptBundle`。Provider 根据自身 API 能力把 `PromptBundle` 映射到 Anthropic 或 OpenAI 请求格式：Anthropic 使用 `system`、`tools`、`messages`；OpenAI 使用等价的 system/developer 消息、tools 和普通 messages 表达同样语义。

缓存策略采用 Provider 能力适配：核心层稳定地区分静态和动态内容；支持缓存的 Provider 尽量只缓存静态 System Prompt 和稳定工具描述；环境上下文、项目指令、自动记忆、System Reminder 和历史上下文不进入静态缓存块。不支持缓存的 Provider 保持现有调用行为，并把缓存状态暴露为未知或不支持。

Plan Mode 保持第三阶段的用户体验，但注入方式从静态 prompt 改为 System Reminder，并放入 messages。权限系统与 Default 模式保持一致，仍然让需要确认的操作返回 ask；唯一特殊处理是指定 plan 文件自动放行。

## 核心数据结构

### PromptBundle

```java
public record PromptBundle(
        SystemChannel system,
        ArrayNode toolDeclarations,
        MessageChannel messages,
        PromptCachePolicy cachePolicy
) {}
```

表示一次模型调用所需的完整提示上下文。`system` 包含静态 System Prompt 和环境上下文；`toolDeclarations` 对应 API 的 tools；`messages` 包含项目指令文件、自动记忆、System Reminder 和历史上下文。

### SystemChannel

```java
public record SystemChannel(
        StaticSystemPrompt staticPrompt,
        EnvironmentContext environmentContext
) {}
```

表示要放入 system 语义位置的内容。`staticPrompt` 稳定、可缓存；`environmentContext` 动态、不可并入静态缓存块。

### MessageChannel

```java
public record MessageChannel(
        Optional<ProjectInstructionContext> projectInstructions,
        Optional<MemoryContext> memory,
        List<SystemReminder> reminders,
        List<ApiMessage> history
) {}
```

表示要放入 messages 的内容。项目指令文件、自动记忆和 System Reminder 以系统级上下文消息或等价消息形式排在历史上下文之前；历史上下文保持原有 user、assistant、tool 顺序。

### StaticSystemPrompt

```java
public record StaticSystemPrompt(
        List<PromptSection> sections
) {
    public String render();
}
```

保存七个固定模块，`render()` 按固定顺序输出：角色设定、行为准则、工具使用指南、代码质量规范、安全边界、任务执行模式、输出风格。输出风格模块包含原创角色 Luna 的语气要求，并明确技术准确性优先于角色扮演。该对象不持有环境、时间、Git 状态、轮次或用户消息。

### PromptSection

```java
public record PromptSection(
        PromptSectionKind kind,
        String title,
        String content
) {}
```

表示静态 System Prompt 的一个模块。`PromptSectionKind` 固定七种枚举，用来保证顺序稳定并便于测试断言。

### EnvironmentContext

```java
public record EnvironmentContext(
        Path workDir,
        String osName,
        Instant now,
        GitStatusSnapshot gitStatus
) {}
```

表示动态环境上下文。它进入 system 语义位置，但不属于 `StaticSystemPrompt`。`GitStatusSnapshot` 只保存可显示摘要，例如是否在 Git 仓库、当前分支、是否有未提交改动；获取失败时保存未知状态，不阻断模型调用。

### ProjectInstructionContext

```java
public record ProjectInstructionContext(
        Path sourcePath,
        String content
) {}
```

表示未来从 `LUNACODE.md` 加载的项目指令。本阶段只预留结构和 messages 落位，不实现文件加载。

### MemoryContext

```java
public record MemoryContext(
        List<String> userPreferences,
        List<String> projectFacts
) {}
```

表示未来自动记忆结果。本阶段只预留结构和 messages 落位，不实现记忆提取、写入或召回。

### SystemReminder

```java
public record SystemReminder(
        SystemReminderKind kind,
        String content,
        int turnIndex
) {}
```

表示运行中动态注入的系统级补充说明，例如 Plan Mode 指令、MCP Server 的使用说明、当前模式提醒、临时约束或环境变化。本阶段不在 spec 层写死标签格式，技术实现中统一由 `SystemReminderRenderer` 渲染后放入 messages。

### ModeInjectionState

```java
public record ModeInjectionState(
        AgentMode mode,
        int turnIndex,
        Path planFile,
        int repeatInterval
) {}
```

表示会话级模式注入状态。用于判断本轮 Plan Mode 应输出完整指令、间隔轮次关键约束，还是精简提醒。

### PromptCachePolicy

```java
public record PromptCachePolicy(
        boolean cacheStaticSystemPrompt,
        boolean cacheToolDeclarations
) {}
```

表示本轮希望缓存哪些稳定内容。Provider 可以根据自身能力采纳或忽略，但不得把环境上下文、项目指令、自动记忆、System Reminder 或历史上下文放入静态缓存块。

### TokenUsage

扩展现有 `TokenUsage`：

```java
public record TokenUsage(
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Integer cacheReadInputTokens,
        Integer cacheCreationInputTokens,
        CacheUsageStatus cacheStatus
) {}
```

保留现有输入、输出、总量字段，增加缓存读取、缓存写入和缓存状态。`cacheStatus` 用来区分 supported、unsupported、unknown。

## 核心接口

### PromptContextBuilder

```java
public final class PromptContextBuilder {
    public PromptBundle build(AgentRunConfig config, int turnIndex, List<ApiMessage> history, ArrayNode tools);
}
```

统一收集七类信息来源并按 system/tools/messages 分流。Agent Loop 每轮只调用这个入口，不直接拼字符串。

### StaticSystemPromptBuilder

```java
public final class StaticSystemPromptBuilder {
    public StaticSystemPrompt build();
}
```

生成七模块静态提示。后续章节加入 `LUNACODE.md`、Skill 或自动记忆时，不修改七模块顺序，只向 MessageChannel 增加对应内容。

### MessageChannelBuilder

```java
public final class MessageChannelBuilder {
    public MessageChannel build(AgentRunConfig config, ModeInjectionState state, List<ApiMessage> history);
}
```

按固定顺序构建 messages：项目指令文件占位、自动记忆占位、System Reminder、历史上下文。本阶段项目指令和记忆为空，但结构存在。

### SystemReminderBuilder

```java
public final class SystemReminderBuilder {
    public List<SystemReminder> build(ModeInjectionState state);
}
```

根据当前模式和轮次生成动态补充消息。Plan Mode 的完整、间隔、精简三类注入在这里集中判断。

### ChatProvider

现有接口从 `String systemPrompt` 演进为结构化上下文：

```java
Stream<StreamEvent> streamChat(PromptBundle promptBundle, ProviderConfig config);
```

保留旧重载作为过渡适配，内部转换为 PromptBundle 或委托新接口，减少一次性改动范围。

### ProviderPromptAdapter

```java
public interface ProviderPromptAdapter {
    String buildRequestBody(PromptBundle promptBundle, ProviderConfig config) throws Exception;
}
```

Provider 内部使用适配器把 PromptBundle 映射成具体 API 请求。Anthropic 适配器负责 system、tools 和 messages；OpenAI 适配器负责 system/developer 消息、tools 和普通 messages 的兼容表达。

## 模块设计

### 静态提示模块

**职责：** 输出七个稳定模块，保证顺序、标题和核心文本稳定。

**对外接口：** `StaticSystemPromptBuilder.build()`。

**依赖：** 无运行时动态依赖，只依赖固定文本常量。

### 输出风格模块

**职责：** 定义 Luna 的原创大小姐型语气：优雅、从容、聪明、略带高傲和适度毒舌，经常称呼用户为“朝日”，但始终以准确解决问题为优先。

**对外接口：** `StaticSystemPromptBuilder.build()` 输出风格 section。

**依赖：** 无运行时动态依赖，只依赖固定文本常量。

**关键约束：** 不直接引用已有作品台词，不声称自己是已有作品角色，不为了角色扮演牺牲技术正确性，不使用露骨、暧昧、成人化或过度恋爱化表达。

### System Channel 模块

**职责：** 把静态 System Prompt 和环境上下文组合成 system 语义内容，同时保留二者的内部边界。

**对外接口：** `SystemChannelRenderer.render(SystemChannel channel, ProviderCapabilities capabilities)`。

**依赖：** `StaticSystemPrompt`、`EnvironmentContext`。

### 环境上下文模块

**职责：** 收集工作目录、操作系统、当前时间和 Git 状态，并作为动态 system 内容传给 Provider。

**对外接口：** `EnvironmentContextCollector.collect(AgentRunConfig config)`。

**依赖：** `AgentRunConfig`、系统属性、Clock、Git 状态探测器。

### Message Channel 模块

**职责：** 按固定顺序生成 messages 中的上下文消息：项目指令文件、自动记忆、System Reminder、历史上下文。

**对外接口：** `MessageChannelBuilder.build(...)`。

**依赖：** `ProjectInstructionContext`、`MemoryContext`、`SystemReminder`、对话历史。

### System Reminder 模块

**职责：** 表达运行时动态指令，包括 Plan Mode、未来 MCP Server 使用说明和临时约束，并放入 messages。

**对外接口：** `SystemReminderBuilder.build(...)` 与 `SystemReminderRenderer.render(...)`。

**依赖：** `ModeInjectionState`。

### Plan Mode 注入模块

**职责：** 根据轮次输出完整、间隔或精简 Plan Mode 指令，并明确 Prompt 约束与权限系统解耦。

**对外接口：** `PlanModeReminderPolicy.createReminder(...)`。

**依赖：** `AgentMode`、turnIndex、planFile、repeatInterval。

### 工具描述强化模块

**职责：** 在工具声明输出时根据工具类型追加稳定描述：只读工具探索优先，写入/替换工具编辑前先读，命令工具优先使用专用工具。

**对外接口：** `ToolDescriptionEnhancer.enhance(Tool tool)`。

**依赖：** `Tool` 元数据、工具名称、只读/破坏性标记。

### Provider 适配模块

**职责：** 把 PromptBundle 映射到具体 Provider 请求格式，负责 system、tools、messages 和缓存标记的 API 差异。

**对外接口：** `ProviderPromptAdapter.buildRequestBody(...)`。

**依赖：** Jackson、ProviderConfig、PromptBundle。

### 缓存用量解析模块

**职责：** 从 Provider 流式响应中解析缓存相关字段，合并到 TokenUsage，并通过现有 usage 事件暴露。

**对外接口：** `AnthropicStreamMapper` 和 `OpenAiStreamMapper` 内部扩展 `usageFrom(...)`。

**依赖：** Provider 原始 JSON usage 字段。

### 权限语义模块

**职责：** 保证 Plan Mode 权限与 Default 一致，只有 plan 文件自动放行；非 plan 写入和命令执行仍返回 ASK。

**对外接口：** `DefaultToolPermissionGateway.decide(...)` 与 `DefaultAgentLoop.executeOne(...)`。

**依赖：** `AgentMode`、`ToolUse`、`Tool`、planFile。

## 模块交互

```text
DefaultAgentLoop
  -> PromptContextBuilder.build(config, turnIndex, history, tools)
     -> StaticSystemPromptBuilder.build()
     -> EnvironmentContextCollector.collect(config)
     -> ToolRegistry.toAPIFormat(mode) + ToolDescriptionEnhancer
     -> MessageChannelBuilder.build(config, modeState, history)
        -> ProjectInstructionContext 占位
        -> MemoryContext 占位
        -> SystemReminderBuilder.build(modeState)
        -> conversation history
  -> AgentTurnRunner.runTurn(promptBundle)
     -> ChatProvider.streamChat(promptBundle, providerConfig)
        -> ProviderPromptAdapter.buildRequestBody(promptBundle, providerConfig)
           -> system: StaticSystemPrompt + EnvironmentContext
           -> tools: toolDeclarations
           -> messages: ProjectInstruction + Memory + SystemReminder + history
        -> Provider HTTP streaming
        -> StreamMapper parses text/tool/usage/cache usage
  -> StreamingTurnCollector emits AgentEvent.UsageUpdated
  -> LoopDecisionMaker decides continue/complete
  -> ToolPermissionGateway decides ALLOW/ASK/DENY with plan file allow rule
```

Plan Mode 注入流程：

```text
turnIndex = 1 -> 完整 Plan Mode System Reminder，放入 messages
turnIndex % repeatInterval == 0 -> 关键约束 System Reminder，放入 messages
其他轮次 -> 精简 Plan Mode System Reminder，放入 messages
/do 切换到 Default -> 不再生成 Plan Mode Reminder
```

## 文件组织

```text
src/main/java/com/lunacode/agent/
├── SystemPromptBuilder.java              # 改为静态提示构建或保留兼容外观
├── StaticSystemPromptBuilder.java        # 新增：七模块静态提示
├── PromptSection.java                    # 新增：提示模块
├── PromptSectionKind.java                # 新增：七模块枚举
├── PromptBundle.java                     # 新增：一次模型调用的完整提示上下文
├── SystemChannel.java                    # 新增：system 内容容器
├── MessageChannel.java                   # 新增：messages 内容容器
├── PromptContextBuilder.java             # 新增：统一收集各来源上下文并分流
├── EnvironmentContext.java               # 新增：动态环境上下文
├── EnvironmentContextCollector.java      # 新增：收集 cwd/os/time/git
├── GitStatusSnapshot.java                # 新增：Git 状态摘要
├── ProjectInstructionContext.java        # 新增：LUNACODE.md 结构预留
├── MemoryContext.java                    # 新增：自动记忆结构预留
├── SystemReminder.java                   # 新增：系统级补充消息
├── SystemReminderKind.java               # 新增：补充消息类型
├── SystemReminderBuilder.java            # 新增：动态补充消息构建
├── SystemReminderRenderer.java           # 新增：补充消息渲染
├── ModeInjectionState.java               # 新增：模式轮次注入状态
├── PlanModeReminderPolicy.java           # 新增：Plan Mode 注入策略
└── PromptCachePolicy.java                # 新增：缓存策略

src/main/java/com/lunacode/provider/
├── ChatProvider.java                     # 修改：支持 PromptBundle
├── AnthropicProvider.java                # 修改：使用 PromptBundle 映射 system/tools/messages
├── OpenAiProvider.java                   # 修改：使用 PromptBundle 映射 system/tools/messages
├── ProviderPromptAdapter.java            # 新增：Provider 请求适配接口
├── AnthropicPromptAdapter.java           # 新增：Anthropic 请求体适配
└── OpenAiPromptAdapter.java              # 新增：OpenAI 请求体适配

src/main/java/com/lunacode/conversation/
└── TokenUsage.java                       # 修改：增加缓存字段和状态

src/main/java/com/lunacode/tool/
├── DefaultToolRegistry.java              # 修改：输出增强后的 description
├── ToolDescriptionEnhancer.java          # 新增：工具描述强化
└── DefaultToolPermissionGateway.java     # 调整/确认：Plan Mode ask 语义与 plan 文件放行

src/test/java/com/lunacode/agent/
├── StaticSystemPromptBuilderTest.java
├── PromptContextBuilderTest.java
├── SystemChannelTest.java
├── MessageChannelBuilderTest.java
├── SystemReminderBuilderTest.java
└── PlanModeReminderPolicyTest.java

src/test/java/com/lunacode/provider/
├── AnthropicPromptAdapterTest.java
├── OpenAiPromptAdapterTest.java
└── ProviderCacheUsageTest.java

src/test/java/com/lunacode/tool/
└── ToolDescriptionEnhancerTest.java
```

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| PromptBundle 顶层分流 | system、tools、messages | 与用户确认的请求上下文落位一致，也更贴近模型 API 的实际结构 |
| system 内容 | 静态 System Prompt + 环境上下文 | 环境是系统级事实，但必须和静态 prompt 分离，防止污染缓存 |
| messages 内容 | 项目指令、自动记忆、System Reminder、历史上下文 | 这些内容有动态性或会话性，适合按消息顺序注入 |
| 静态提示结构 | 七个固定模块，固定顺序渲染 | 满足 spec 的稳定性要求，也方便测试缓存不被动态内容污染 |
| 输出语气 | 原创 Luna 大小姐型语气，但技术准确性优先 | 保留产品人格，同时避免角色扮演伤害解释质量、安全边界或事实正确性 |
| System Reminder 标签 | plan.md 设计具体格式，spec 不写死 | 保持需求层抽象，同时实现层仍能统一渲染 |
| Provider 接口 | 从字符串 systemPrompt 演进为 PromptBundle | 避免后续继续把七类来源混成一个字符串 |
| 缓存接入范围 | 支持缓存的 Provider 启用，不支持者降级 | 符合已确认的需求范围，不绑定所有 Provider 都真实命中 |
| 工具描述强化位置 | ToolRegistry 输出 API 声明时统一增强 | 不侵入每个工具实现，保证 description 稳定一致 |
| Plan Mode 权限 | Prompt 约束行为，权限保持 ask，plan 文件 allow | 符合用户澄清，保留灵活性且不硬性 deny 非计划行为 |
| 缓存用量模型 | 扩展 TokenUsage | 复用现有 usage 事件和状态栏更新路径，减少新事件类型 |
| Git 状态获取 | 摘要化、失败为 unknown | Git 状态是动态 system 内容，获取失败不能阻断 Agent Loop |

## 测试策略

- 静态提示测试：断言七模块顺序、空行分隔、无动态字段、同输入多次渲染一致。
- 输出风格测试：断言输出风格模块包含 Luna 原创语气、称呼“朝日”、适度毒舌但不侮辱、技术准确性优先、禁止成人化和禁止引用已有作品台词。
- PromptBundle 分流测试：断言静态 System Prompt 和环境上下文进入 system，工具描述进入 tools，项目指令、自动记忆、System Reminder 和历史上下文进入 messages。
- 动态上下文测试：改变 cwd、时间、Git 状态时，只改变 system 中的 EnvironmentContext，不改变静态提示文本。
- Message Channel 测试：断言项目指令和自动记忆本阶段为空占位，System Reminder 排在历史上下文之前，历史上下文顺序不变。
- System Reminder 测试：断言 Plan Mode reminder 进入 messages，不进入 static prompt 或 tools。
- Plan Mode 轮次测试：首轮完整、间隔轮次重复关键约束、其他轮次精简，切回 Default 后不再注入。
- 权限测试：Plan Mode 写 plan 文件 ALLOW，写非 plan 文件和 Bash 仍返回 ASK，不自动 DENY。
- Provider 请求体测试：Anthropic 请求正确映射 system/tools/messages；OpenAI 请求用等价结构表达 system/tools/messages。
- 缓存用量测试：Anthropic/OpenAI usage 字段可解析到 TokenUsage；缺失缓存字段时状态为 unknown 或 unsupported。
- 工具描述测试：ReadFile、WriteFile、EditFile、Bash、Glob、Grep 的 description 包含对应强化规则。
- 回归测试：前三阶段纯对话、工具调用、Agent Loop、Plan Mode 测试继续通过。