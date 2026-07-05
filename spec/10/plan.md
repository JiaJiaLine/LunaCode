# Skill 系统 Plan

## 架构概览

Skill 系统拆成六层：定义解析、来源发现、目录缓存、调用入口、运行时作用域、Prompt/工具集成。定义解析负责把 Markdown frontmatter 和正文转换成稳定数据结构；来源发现负责合并项目级、用户级和内置级 Skill；目录缓存负责保存上一次成功解析的版本并输出诊断；调用入口把有效 Skill 暴露给 `/help`、补全、斜杠命令和自然语言两阶段加载；运行时作用域负责 `$ARGUMENTS` 替换、临时工具白名单、临时模型覆盖和 fork 隔离；Prompt/工具集成负责把 Skill 摘要注入模型上下文，并提供系统级 `LoadSkill` 工具按需加载完整 SOP。

现有 `command`、`prompt`、`tool`、`agent`、`orchestrator` 会保留职责边界。新增 `skill` 包承载 Skill 定义、解析、目录和执行计划；`command` 只做命令分发；`prompt` 只消费 Skill 摘要和本次调用的临时上下文；`tool` 只注册 `LoadSkill` 并根据调用级作用域过滤工具；`orchestrator` 负责任务启动、忙碌状态、fork 子对话和主历史回流。

完整 SOP 不作为会话长期状态保存。斜杠调用时，主历史记录用户可见的 Skill 调用描述，模型本轮实际看到渲染后的 prompt；自然语言触发 `LoadSkill` 时，完整 SOP 先作为工具结果进入本轮循环，循环结束后被替换为短标记，避免后续请求继续看到完整指令。

## 核心数据结构

### SkillDefinition

```java
public record SkillDefinition(
        String name,
        String description,
        SkillExecutionMode mode,
        SkillContextPolicy context,
        Optional<String> model,
        List<String> tools,
        String body,
        SkillOrigin origin,
        Optional<Path> resourceRoot
) {}
```

表示一次成功解析后的完整 Skill。`body` 是 frontmatter 之后的 Markdown SOP；`resourceRoot` 仅目录型 Skill 存在，用于告诉 Agent 附属资源所在根目录。

### SkillSummary

```java
public record SkillSummary(
        String name,
        String description,
        SkillOrigin origin,
        SkillExecutionMode mode
) {}
```

表示注入上下文、`/help` 和补全所需的轻量信息，不包含完整 SOP。

### SkillOrigin

```java
public record SkillOrigin(
        SkillSourceKind kind,
        String sourceId,
        Optional<Path> path,
        int priority
) {}
```

标记 Skill 来自项目级、用户级或内置级。`priority` 用于同名覆盖，项目级最高，用户级次之，内置级最低。

### SkillSourceKind

```java
public enum SkillSourceKind {
    PROJECT,
    USER,
    BUILTIN
}
```

### SkillExecutionMode

```java
public enum SkillExecutionMode {
    INLINE,
    FORK
}
```

### SkillContextPolicy

```java
public enum SkillContextPolicy {
    FULL,
    RECENT,
    NONE
}
```

`FULL`、`RECENT`、`NONE` 只在 `FORK` 模式下生效。

### SkillParseResult

```java
public sealed interface SkillParseResult permits SkillParseResult.Success, SkillParseResult.Failure {
    record Success(SkillDefinition definition) implements SkillParseResult {}
    record Failure(SkillOrigin origin, String reason) implements SkillParseResult {}
}
```

用于区分解析成功和单个 Skill 解析失败，失败不会中断整体扫描。

### SkillCatalogSnapshot

```java
public record SkillCatalogSnapshot(
        List<SkillSummary> summaries,
        List<SkillDiagnostic> diagnostics
) {}
```

表示当前可见的 Skill 摘要和最近一次扫描诊断。

### SkillDiagnostic

```java
public record SkillDiagnostic(
        SkillDiagnosticLevel level,
        String sourceId,
        String message
) {}
```

用于向启动日志、状态栏或命令结果报告坏文件、冲突、白名单错误和缓存回退。

### SkillInvocationRequest

```java
public record SkillInvocationRequest(
        String name,
        String rawArguments,
        SkillInvocationTrigger trigger
) {}
```

表示一次来自斜杠命令或 `LoadSkill` 的 Skill 调用。`rawArguments` 原样替换 `$ARGUMENTS`。

### SkillInvocationPlan

```java
public record SkillInvocationPlan(
        SkillDefinition definition,
        String renderedPrompt,
        ToolAccessPolicy toolAccessPolicy,
        Optional<String> modelOverride
) {}
```

是执行前生成的调用计划，包含替换 `$ARGUMENTS` 后的 prompt、临时工具白名单和临时模型覆盖。

### ToolAccessPolicy

```java
public record ToolAccessPolicy(
        Optional<Set<String>> allowedTools,
        Set<String> alwaysVisibleTools
) {}
```

`allowedTools` 为空表示不收窄；`alwaysVisibleTools` 固定包含 `LoadSkill`，确保系统级加载工具不被 Skill 白名单隐藏。运行时通过该策略判断某个工具名是否允许出现在声明和执行阶段。

### LoadedSkillContext

```java
public record LoadedSkillContext(
        String skillName,
        String renderedPrompt,
        Optional<Path> resourceRoot
) {}
```

表示本轮通过 `LoadSkill` 加载出来的临时完整 SOP。该上下文只在当前 Agent 调用内有效。

### SkillForkResult

```java
public record SkillForkResult(
        String skillName,
        String userRequest,
        String summary,
        List<String> artifactPaths,
        List<String> nextSteps
) {}
```

表示 fork 子对话结束后回流到主历史的简短总结。

## 核心接口

### SkillParser

```java
public interface SkillParser {
    SkillParseResult parseSingleFile(Path markdownFile, SkillOrigin origin);
    SkillParseResult parseDirectory(Path skillDirectory, SkillOrigin origin);
    SkillParseResult parseBuiltin(String resourceName, String content, SkillOrigin origin);
}
```

负责 frontmatter 分割、YAML 解析、字段默认值、字段校验和正文提取。

### SkillSource

```java
public interface SkillSource {
    List<SkillCandidate> discover(Path projectRoot, Path userHome);
}
```

一个来源返回一组候选 Skill。项目级扫描 `<项目>/.lunacode/skills/`，用户级扫描 `~/.lunacode/skills/`，内置级返回编译进程序的资源清单。

### SkillCatalog

```java
public interface SkillCatalog {
    SkillCatalogSnapshot snapshot();
    Optional<SkillDefinition> loadForExecution(String name);
    List<SkillDiagnostic> diagnostics();
}
```

`snapshot()` 返回最近一次成功缓存的摘要；`loadForExecution` 每次执行前重新解析对应入口，失败时按缓存策略回退。

### SkillInvocationPlanner

```java
public interface SkillInvocationPlanner {
    SkillInvocationPlan plan(SkillInvocationRequest request);
}
```

负责执行前重新加载 Skill、替换 `$ARGUMENTS`、校验工具白名单、生成模型覆盖和工具访问策略。

### SkillCommandRegistrar

```java
public interface SkillCommandRegistrar {
    void registerSkillCommands(SlashCommandRegistry registry, SkillCatalog catalog, CommandRuntime runtime);
}
```

把有效 Skill 摘要转成斜杠命令定义，命令 handler 调用运行时的 Skill 执行入口。

### SkillPromptContextLoader

```java
public interface SkillPromptContextLoader {
    SkillPromptContext loadSummaries();
    Optional<LoadedSkillContext> currentLoadedSkill();
}
```

给 `PromptContextBuilder` 提供轻量 Skill 列表和本次调用级的临时完整 SOP。

### SkillForkRunner

```java
public interface SkillForkRunner {
    SkillForkResult runFork(SkillInvocationPlan plan, AgentRunConfig parentConfig, ConversationManager parentConversation, CancellationToken token);
}
```

负责构建隔离子对话、运行 Agent、生成回流总结。

## 模块设计

### `com.lunacode.skill` 定义与解析模块

**职责：** 解析单文件和目录型 Skill，校验 frontmatter，生成 `SkillDefinition` 和 `SkillSummary`。

**对外接口：** `SkillParser`、`SkillCatalog`、`SkillInvocationPlanner`。

**依赖：** Jackson YAML、文件系统、工具注册表快照、内置 Skill 资源。

解析规则：单文件候选是 Skill 根目录下的 `.md` 文件；目录型候选是包含 `SKILL.md` 的一级子目录。frontmatter 必须由文件开头的 `---` 包裹。`name` 和 `description` 必填；`mode` 缺省为 `inline`；`context` 缺省为 `full`；`tools` 缺省为空；`model` 缺省为空。正文可以为空白之外的任意 Markdown，执行前只做 `$ARGUMENTS` 简单字符串替换。

### `com.lunacode.skill` 来源与缓存模块

**职责：** 扫描项目级、用户级、内置级 Skill，按优先级合并同名定义，保存上一次成功解析的缓存，输出诊断。

**对外接口：** `SkillSource`、`ProjectSkillSource`、`UserSkillSource`、`BuiltinSkillSource`、`DefaultSkillCatalog`。

**依赖：** 项目根目录、用户 home、内置资源清单、`SkillParser`。

合并策略：先解析内置级，再解析用户级，最后解析项目级；高优先级有效定义覆盖低优先级定义。解析失败的高优先级候选不会覆盖低优先级有效定义，除非该候选已有缓存且可回退。首次解析失败且无缓存的候选只产生诊断，不注册命令。

### `com.lunacode.skill` 内置样板模块

**职责：** 提供编译进程序的 `commit` 和 `test` 两个样板 Skill。

**对外接口：** `BuiltinSkillSource` 返回固定资源列表。

**依赖：** `src/main/resources/lunacode/skills/commit.md`、`src/main/resources/lunacode/skills/test.md`。

内置 `review` 不提供，现有硬编码 `/review` 保持原行为。若用户级或项目级提供 `commit` 或 `test`，按优先级覆盖内置版本。

### `com.lunacode.command` Skill 命令模块

**职责：** 把有效 Skill 注册为斜杠命令，参与 `/help`、解析和补全。

**对外接口：** 扩展 `SlashCommandRegistry` 支持动态注册或重建；`SkillCommandRegistrar` 生成 `SlashCommandDefinition`。

**依赖：** `SkillCatalog`、`CommandRuntime`。

命令名固定为 `/<skill.name>`，无别名。命令类型为 `PROMPT`。handler 不直接把 prompt 文本塞进主历史，而是调用运行时新增的 `submitSkillInvocation`，由 orchestrator 根据 `mode` 选择 inline 或 fork。

### `com.lunacode.prompt` Skill 上下文模块

**职责：** 在每轮请求中注入轻量 Skill 列表，并在本次调用需要时注入临时完整 SOP。

**对外接口：** 新增 `SkillPromptContext`，扩展 `MessageChannel` 和 `PromptContextBuilder`。

**依赖：** `SkillPromptContextLoader`、现有 provider prompt adapter。

轻量列表以独立系统/开发者上下文出现，内容只包含 Skill 名称和说明，以及提示模型需要完整 SOP 时调用 `LoadSkill`。完整 SOP 仅来自本次 `LoadSkill` 或显式 Skill 调用，不写入持久项目指令、记忆或普通环境上下文。

### `com.lunacode.tool` LoadSkill 工具模块

**职责：** 注册系统级 `LoadSkill` 工具，让模型在普通自然语言对话中按需加载完整 SOP。

**对外接口：** `LoadSkillTool implements Tool`。

**依赖：** `SkillInvocationPlanner`、本轮 `LoadedSkillContext` 存储。

输入 schema 包含 `name` 和可选 `arguments`。工具执行时重新解析 Skill，生成渲染后的 SOP，并返回给模型。`LoadSkill` 是系统级工具，不受 Skill `tools` 白名单限制。循环结束后，`DefaultAgentLoop` 把包含完整 SOP 的 `LoadSkill` 工具结果替换成短标记，避免下一次请求继续看到完整 SOP。

### `com.lunacode.tool` 工具白名单模块

**职责：** 根据 `SkillInvocationPlan` 临时过滤模型可见工具，并在执行时拒绝白名单外工具。

**对外接口：** 扩展 `ToolRegistry.declarationsForModel` 支持 `ToolAccessPolicy`；`AgentRunConfig` 携带可选 `ToolAccessPolicy`。

**依赖：** `DefaultToolRegistry`、`AgentToolRunner`。

声明阶段只输出白名单允许的工具和 `alwaysVisibleTools`。执行阶段 `AgentToolRunner` 再按同一策略校验工具名，防止模型手写隐藏工具名绕过声明。

### `com.lunacode.agent` 调用级上下文模块

**职责：** 支持本次调用级 prompt 覆盖、工具范围、模型覆盖和 LoadSkill 结果清理。

**对外接口：** 扩展 `AgentRequest` 和 `AgentRunConfig`。

**依赖：** `ConversationManager`、`PromptContextBuilder`、`ProviderConfig`。

`AgentRequest` 增加用户可见消息和模型可见消息。普通对话二者相同；Skill inline 调用时，主历史保存用户可见的 `/name 参数` 或等价描述，模型本轮看到渲染后的 SOP。`AgentRunConfig` 增加工具访问策略、模型覆盖和本轮 Skill 上下文。`DefaultAgentLoop` 在构建首轮 prompt 时用模型可见消息替换本轮最后一条用户消息，不把完整 SOP 长期写入主历史。

### `com.lunacode.orchestrator` Skill 运行模块

**职责：** 接收斜杠 Skill 调用，管理 busy 状态，分派 inline/fork 执行，回流 fork 总结。

**对外接口：** 扩展 `CommandRuntime`，新增 `submitSkillInvocation(SkillInvocationRequest request)`。

**依赖：** `SkillInvocationPlanner`、`SkillForkRunner`、`AgentLoop`、`ConversationManager`。

inline 模式复用现有 Agent loop，但使用 Skill 调用级 `AgentRequest`。fork 模式创建隔离 `DefaultConversationManager`，按 `context` 带入上下文，运行同样的 Agent loop，完成后从子对话生成 `SkillForkResult` 并向主历史插入一条 assistant 总结。

### `com.lunacode.skill` fork 上下文模块

**职责：** 为 fork 子对话生成初始上下文。

**对外接口：** `SkillForkContextBuilder`。

**依赖：** 主 `ConversationManager`、现有摘要模型能力、`ProviderConfig`。

`none` 生成空子对话；`recent` 复制最近 5 条可发送 API 消息；`full` 先生成完整主对话摘要，再把摘要作为子对话的第一条上下文消息。若摘要生成失败，fork 调用失败并向主界面报告错误，不回退为完整原文。

### `com.lunacode.app` 装配模块

**职责：** 创建 Skill catalog、注册 `LoadSkill`、注册 Skill 命令、把 Skill 资源根接入工具路径策略。

**对外接口：** 修改应用启动装配流程。

**依赖：** 工作区路径、用户 home、工具注册表、沙箱根目录、命令注册中心。

项目级 Skill 根天然在工作区内；用户级 Skill 根需要作为受控只读资源根加入路径策略，让目录型 Skill 的附属资源能通过 `ReadFile` 按需读取。Bash 执行用户级附属脚本仍经过现有权限确认和危险命令拦截。

## 模块交互

### 启动和目录构建

1. 应用启动时创建 `DefaultSkillCatalog`，传入项目根、用户 home、内置 Skill 资源清单和工具注册表。
2. catalog 扫描三层来源，解析有效 Skill，校验命名、命令冲突、工具白名单和字段取值。
3. catalog 输出 `SkillCatalogSnapshot`，有效 Skill 交给 `SkillCommandRegistrar` 注册为斜杠命令。
4. `PromptContextBuilder` 每轮读取 snapshot，把 `name` 和 `description` 注入模型上下文。
5. 启动诊断写入 stderr 或状态消息，单个坏 Skill 不阻断应用启动。

### 显式斜杠调用

1. 用户输入 `/commit 重点关注安全问题`。
2. `SlashCommandDispatcher` 找到由 Skill 注册的 `/commit` 命令。
3. handler 调用 `submitSkillInvocation(name=commit, rawArguments=重点关注安全问题, trigger=SLASH)`。
4. `SkillInvocationPlanner` 重新解析 commit Skill，失败则尝试缓存回退；成功后替换所有 `$ARGUMENTS`。
5. 若 mode 为 `inline`，orchestrator 构造调用级 `AgentRequest`，主历史保存用户可见调用，模型本轮看到渲染后的 prompt。
6. 若 mode 为 `fork`，orchestrator 创建隔离子对话，前台阻塞运行，完成后把简短总结写回主历史。

### 自然语言两阶段加载

1. 用户正常输入自然语言请求。
2. Prompt 中只有 Skill 摘要和 `LoadSkill` 工具。
3. Agent 判断需要某个 Skill 时调用 `LoadSkill`。
4. `LoadSkillTool` 重新解析 Skill 并返回完整渲染 SOP。
5. 后续 turn 使用该 SOP 完成任务。
6. Agent loop 结束后，包含完整 SOP 的工具结果被替换为短标记，下一次用户请求不会再看到完整 SOP。

### 工具白名单

1. Skill 设置 `tools` 时，`SkillInvocationPlanner` 校验所有工具都存在。
2. 本次调用的 `AgentRunConfig` 携带 `ToolAccessPolicy`。
3. `PromptContextBuilder` 请求工具声明时只拿到白名单工具和 `LoadSkill`。
4. `AgentToolRunner` 执行工具前再次检查 `ToolAccessPolicy`，白名单外工具返回工具不存在或不可用。
5. 调用结束后丢弃该 `AgentRunConfig`，后续普通对话恢复全局工具集合。

### 模型覆盖

1. Skill 设置 `model` 时，`SkillInvocationPlanner` 把模型名放入 `SkillInvocationPlan`。
2. `DefaultAgentLoop` 每个 turn 使用派生 `ProviderConfig`，仅替换 `model` 字段，其余 provider 配置保持不变。
3. 状态栏在本次调用期间显示有效模型。
4. 调用结束后恢复默认 `ProviderConfig`。

### 目录型 Skill 资源访问

1. 目录型 Skill 的 `SKILL.md` 被解析为 SOP，目录根记录到 `resourceRoot`。
2. `LoadSkill` 或显式调用返回 SOP 时附带资源根路径提示。
3. 附属文件不自动进入 prompt。
4. Agent 按 SOP 指引使用 `ReadFile` 读取示例、模板或参考文档，或使用 `Bash` 执行脚本。
5. 用户级 Skill 根作为受控资源根加入路径策略；Bash 仍走权限模式和命令安全检查。

## 文件组织

```text
src/main/java/com/lunacode/skill/
├── BuiltinSkillSource.java
├── DefaultSkillCatalog.java
├── DefaultSkillInvocationPlanner.java
├── DirectorySkillParser.java
├── FileSystemSkillSource.java
├── FrontmatterSkillParser.java
├── SkillCatalog.java
├── SkillCatalogSnapshot.java
├── SkillCandidate.java
├── SkillContextPolicy.java
├── SkillDefinition.java
├── SkillDiagnostic.java
├── SkillDiagnosticLevel.java
├── SkillExecutionMode.java
├── SkillForkContextBuilder.java
├── SkillForkResult.java
├── SkillForkRunner.java
├── SkillInvocationPlan.java
├── SkillInvocationRequest.java
├── SkillInvocationTrigger.java
├── SkillOrigin.java
├── SkillParser.java
├── SkillPromptContext.java
├── SkillSource.java
├── SkillSourceKind.java
└── ToolAccessPolicy.java

src/main/java/com/lunacode/tool/
└── LoadSkillTool.java

src/main/resources/lunacode/skills/
├── commit.md
└── test.md

src/main/java/com/lunacode/command/
├── SkillCommandRegistrar.java
└── SlashCommandRegistry.java              # 扩展动态 Skill 命令注册/重建能力

src/main/java/com/lunacode/prompt/
├── MessageChannel.java                    # 增加 SkillPromptContext
├── PromptContextBuilder.java              # 注入 Skill 摘要和本次临时 SOP
└── SkillPromptRenderer.java

src/main/java/com/lunacode/provider/
├── AnthropicPromptAdapter.java            # 渲染 SkillPromptContext 到 system blocks
└── OpenAiPromptAdapter.java               # 渲染 SkillPromptContext 到 developer messages

src/main/java/com/lunacode/agent/
├── AgentRequest.java                      # 增加用户可见消息与模型可见消息
└── DefaultAgentLoop.java                  # 支持调用级模型、工具策略、LoadSkill 清理

src/main/java/com/lunacode/runtime/
└── AgentRunConfig.java                    # 增加 ToolAccessPolicy、modelOverride、SkillPromptContext

src/main/java/com/lunacode/orchestrator/
└── DefaultChatOrchestrator.java           # 装配 Skill 调用、fork 运行和主历史回流

src/test/java/com/lunacode/skill/
├── FrontmatterSkillParserTest.java
├── DefaultSkillCatalogTest.java
├── SkillInvocationPlannerTest.java
└── SkillForkContextBuilderTest.java
```

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| YAML 解析 | 使用项目已有 `jackson-dataformat-yaml` | `pom.xml` 已包含依赖，避免新增解析库 |
| 内置 Skill 存放 | 使用 classpath resources 加固定清单 | 打包进 jar，满足编译进程序；固定清单避免 jar 内资源枚举不稳定 |
| 同名覆盖 | 项目级 > 用户级 > 内置级 | 符合 spec，允许项目定制覆盖个人和内置默认 |
| 热更新 | 执行前重新解析，失败回退缓存 | 满足改文件后下次调用立即生效，同时避免坏编辑搞挂命令 |
| 完整 SOP 生命周期 | 调用级临时可见，结束后清理或替换 | 满足两阶段加载和不污染后续对话 |
| 斜杠 inline 历史 | 主历史保存用户可见调用，模型本轮使用临时 prompt 覆盖 | 避免完整 SOP 永久进入主历史，同时保留可读操作记录 |
| 自然语言加载 | 使用系统级 `LoadSkill` 工具 | 符合两阶段加载，模型可按需取完整 SOP |
| 工具白名单 | 声明阶段过滤 + 执行阶段二次校验 | 防止模型手写隐藏工具名绕过声明 |
| `LoadSkill` 白名单例外 | 加入 `alwaysVisibleTools` | 保证系统级加载工具不被 Skill 白名单限制 |
| fork 执行 | 前台阻塞，隔离 `ConversationManager` | 行为简单可预测，避免本章引入后台任务复杂度 |
| fork `full` 上下文 | 生成摘要后带入子对话 | 符合 spec 的完整对话摘要，控制 token 使用 |
| 用户级附属资源访问 | 将有效 Skill 根作为受控资源根接入路径策略 | 保证目录型 Skill 的资源能被已有 ReadFile/Bash 按需访问 |
| `/review` 冲突 | 保留现有硬编码 `/review`，内置 Skill 只提供 `commit` 和 `test` | 符合已批准 spec，避免行为回归 |

## 风险与约束

- `LoadSkill` 工具结果需要在当前循环结束后替换为短标记，否则完整 SOP 会留在会话历史里影响后续请求。
- fork `full` 依赖摘要能力，摘要失败时应直接报告错误，不能偷偷把完整历史原文塞进子对话。
- 用户级 Skill 资源根扩大了可读范围，需要保持只读访问默认，并让 Bash 脚本执行继续经过权限确认。
- 动态注册 Skill 命令时必须先检测内置命令冲突，否则会破坏现有 `/review`、`/help` 等行为。
- 工具白名单必须同时影响工具声明和执行，否则只能降低模型选择概率，不能真正约束调用。

