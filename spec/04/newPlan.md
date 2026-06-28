# LunaCode 系统提示结构化与缓存策略 NewPlan

## 架构概览

本章的新方案把系统提示、动态上下文、System Reminder 和缓存策略从 `agent` 中剥离，统一放入 `prompt` 包。Prompt 是模型请求上下文，不是 Agent Loop 本体；Provider 依赖 `prompt.PromptBundle`，Agent Loop 只调用 `PromptContextBuilder` 获取本轮请求上下文。

新的依赖方向是：

```text
agent -> prompt
provider -> prompt
prompt -> runtime / conversation
tool -> runtime / interaction
```

明确禁止：

```text
provider -> agent
tool -> agent
prompt -> agent
```

## PromptBundle 分层

`PromptBundle` 是一次模型调用的完整提示上下文：

- `SystemChannel system`
- `ArrayNode toolDeclarations`
- `MessageChannel messages`
- `PromptCachePolicy cachePolicy`

### SystemChannel

包含：

- `StaticSystemPrompt staticPrompt`
- `EnvironmentContext environmentContext`

静态 System Prompt 和动态环境上下文同属 system 语义，但对象层面保持分离，避免当前时间、工作目录、Git 状态污染缓存稳定块。

### MessageChannel

包含：

- 项目指令占位：`ProjectInstructionContext`
- 自动记忆占位：`MemoryContext`
- 系统级补充消息：`SystemReminder`
- 对话历史：`List<ApiMessage>`

本阶段不自动读取 `LUNACODE.md`，不实现长期记忆，只保留结构和落位。

### PromptCachePolicy

表达本轮缓存意图：

- 静态 System Prompt 可缓存。
- 稳定工具描述可缓存。
- 环境上下文、System Reminder、项目指令、记忆、历史消息不可进入静态缓存块。

## 静态 System Prompt

`StaticSystemPromptBuilder` 固定输出七个模块，顺序稳定：

1. 角色设定
2. 行为准则
3. 工具使用指南
4. 代码质量规范
5. 安全边界
6. 任务执行模式
7. 输出风格

静态提示必须满足：

- 不包含当前时间、工作目录、Git 状态、会话轮次、用户本轮输入、最近工具结果或 plan file 路径。
- 强化专用工具优先、编辑前先读、修改后验证、遵守工作区边界、不编造未观察事实。
- 输出风格包含原创 Luna 语气，但技术准确性优先，不为了角色表达牺牲事实、工程判断或安全边界。

## 动态上下文

### EnvironmentContext

由 `EnvironmentContextCollector` 收集：

- 当前工作目录
- 操作系统
- 当前时间
- Git 状态摘要

Git 获取失败时返回 unknown，不阻断 Agent Loop。

### SystemReminder

由 `SystemReminderBuilder` 和 `PlanModeReminderPolicy` 生成。

Plan Mode 注入策略：

- 首轮：完整 Plan Mode 约束。
- 间隔轮次：重复关键约束。
- 其他轮次：精简提醒。
- 切回 Default 后：不再生成 Plan Mode 禁令。

System Reminder 放入 messages，不并入静态 System Prompt。

## Provider 适配

`provider.ChatProvider` 使用 `PromptBundle` 作为结构化入口。

### Anthropic

`AnthropicPromptAdapter` 映射：

- system：静态提示块 + 环境上下文块。
- tools：工具声明；支持缓存时添加 cache_control。
- messages：System Reminder + 历史上下文。
- thinking：沿用 ProviderConfig。

### OpenAI

`OpenAiPromptAdapter` 映射：

- system：静态提示。
- developer 或等价系统消息：环境上下文。
- system/developer 补充消息：System Reminder。
- tools：转换为 OpenAI function tools 格式。
- messages：历史上下文。

Provider 不再 import `com.lunacode.agent.*`。

## 工具描述强化

`ToolDescriptionEnhancer` 在 `DefaultToolRegistry` 输出工具声明时统一增强 description：

- 只读工具强调探索优先。
- 写入和替换工具强调编辑前必须读过目标内容。
- Bash 强调优先使用专用工具，缺少专用工具时再使用命令。

强化发生在工具声明输出层，不污染工具执行实现。

## 文件组织

```text
src/main/java/com/lunacode/
├── prompt/
│   ├── PromptBundle.java
│   ├── PromptContextBuilder.java
│   ├── StaticSystemPromptBuilder.java
│   ├── EnvironmentContextCollector.java
│   ├── MessageChannelBuilder.java
│   ├── SystemReminderBuilder.java
│   └── PlanModeReminderPolicy.java
├── provider/
│   ├── AnthropicPromptAdapter.java
│   ├── OpenAiPromptAdapter.java
│   └── ChatProvider.java
├── runtime/
└── conversation/
```

## 测试与验收

- `StaticSystemPromptBuilderTest` / `SystemPromptBuilderTest`：七模块顺序、稳定渲染、无动态字段、Luna 输出风格规则。
- `PromptContextBuilderTest`：system/tools/messages/cachePolicy 完整分流。
- `SystemChannelTest`：静态提示与环境上下文分离，Git unknown 不阻断。
- `MessageChannelBuilderTest`：项目指令和记忆为空占位，System Reminder 位于历史前。
- `PlanModeReminderPolicyTest`：首轮、间隔轮次、精简轮次、Default 切换。
- `AnthropicPromptAdapterTest` / `OpenAiPromptAdapterTest`：Provider 请求体映射。
- `ProviderCacheUsageTest`：缓存 usage 字段解析和缺失兼容。
- `ToolDescriptionEnhancerTest`：工具描述强化。
- `PackageDependencyTest`：验证 `provider/tool/prompt` 不依赖 `agent`。
- 全量回归：运行 `mvn test`。

