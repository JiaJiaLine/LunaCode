# Agent Hook 自动化 Plan

## 架构概览

Hook 采用独立运行时模块接入现有 Agent 生命周期。新增 `com.lunacode.hook` 包承载规则模型、条件解析、配置加载、动作执行、日志、一次性执行记录和 system reminder 缓冲；现有 `DefaultAgentLoop`、`AgentToolRunner`、`DefaultContextManager`、`SlashCommandDispatcher` 只在明确生命周期节点构造 `HookContext` 并调用 `HookRuntime`。

配置加载拆成专用 `HookConfigLoader`，不把 Hook 强塞进现有 provider 配置解析流程。它只读取三个固定位置里的 `hooks` 段：项目级 `.lunacode/config.yaml`、用户级 `~/.lunacode/config.yaml`、本地级 `.lunacode/config.local.yaml`。缺失文件忽略，存在文件中的 Hook 按项目级、用户级、本地级追加合并，加载后统一交给 `HookValidator` 校验；任一错误会聚合成启动失败。

动作执行使用 `HookActionExecutor` 分发到四类执行器。命令动作通过抽出的 `ShellCommandRunner` 复用 Bash 工具的工作目录、沙箱、超时、输出截断和敏感信息脱敏能力，并额外注入 Hook 环境变量；HTTP 动作用 JDK `HttpClient`；prompt 动作写入 `HookReminderStore`；sub_agent 动作只写未实现日志。

提示词注入通过扩展现有 `SystemReminderBuilder` / `MessageChannelBuilder` 完成。Hook 产生的 prompt 或 `inject_result: true` 输出先进入 `HookReminderStore`，构建 PromptBundle 时按会话和轮次取出并渲染为 system reminder。`pre_send` 在构建当前请求之前执行，因此能影响当前请求；其它事件写入下一轮。

工具拦截只在 `pre_tool_use` 中走同步路径。`AgentToolRunner` 在工具真正执行前调用 `HookRuntime.runPreToolHooks`，命中 `reject: true` 时立即返回错误 `ToolResult`，后续权限确认和工具执行都不再发生。非拦截 Hook 失败只写日志，继续主流程。

## 核心数据结构

### HookConfig

```java
public record HookConfig(List<HookDefinition> hooks) {
    public static HookConfig empty();
}
```

保存已按来源顺序合并并通过校验的 Hook 列表。

### RawHookDefinition

```java
public record RawHookDefinition(
        HookSource source,
        int order,
        String event,
        String condition,
        Map<String, Object> action,
        Boolean reject,
        Boolean async,
        Boolean once,
        Integer timeoutMs,
        Boolean injectResult
) {}
```

保存 YAML 读取后的原始字段，用于集中校验和错误定位。`HookConfigLoader` 会忽略配置文件中的非 `hooks` 字段，因此 Hook 配置文件可以和其它配置共存。

### HookDefinition

```java
public record HookDefinition(
        String id,
        HookSource source,
        int order,
        HookEventName event,
        Optional<HookCondition> condition,
        HookAction action,
        boolean reject,
        boolean async,
        boolean once,
        Optional<Duration> timeout,
        boolean injectResult
) {}
```

`id` 由来源、文件内顺序和事件生成；`source` 记录项目级、用户级、本地级；`order` 是同文件声明顺序。`reject` 只能用于 `pre_tool_use`，`async` 不能和 `reject` 同时使用。

### HookSource

```java
public record HookSource(
        HookSourceLevel level,
        Path path
) {}

public enum HookSourceLevel {
    PROJECT,
    USER,
    LOCAL
}
```

表示 Hook 来源，并提供执行排序依据。排序固定为 `PROJECT -> USER -> LOCAL`。

### HookEventName

```java
public enum HookEventName {
    STARTUP,
    SHUTDOWN,
    SESSION_START,
    SESSION_END,
    TURN_START,
    TURN_END,
    PRE_SEND,
    POST_RECEIVE,
    PRE_TOOL_USE,
    POST_TOOL_USE,
    ERROR,
    COMPACT,
    PERMISSION_REQUEST,
    FILE_CHANGE,
    COMMAND_EXECUTE
}
```

枚举值和 YAML 中的 snake_case 名称互转，例如 `PRE_TOOL_USE` 对应 `pre_tool_use`。

### HookContext

```java
public record HookContext(
        String eventName,
        String toolName,
        Map<String, String> toolArgs,
        String filePath,
        String message,
        String error
) {}
```

这是条件表达式可访问的稳定上下文。`tool` 是 `toolName` 的兼容别名，`args.<key>` 是 `toolArgs.<key>` 的兼容别名。

### HookExecutionScope

```java
public record HookExecutionScope(
        String sessionId,
        int turnIndex,
        Path workspaceRoot
) {}
```

运行时内部使用，不暴露给条件表达式。它用于日志路径、`once` 记录和动作工作目录。

### HookCondition

```java
public record HookCondition(
        HookConditionMode mode,
        List<HookPredicate> predicates,
        String rawExpression
) {}

public enum HookConditionMode {
    ALL,
    ANY
}
```

`ALL` 来自 `&&`，`ANY` 来自 `||`。表达式未包含组合符时按单条件处理。

### HookPredicate

```java
public record HookPredicate(
        String field,
        HookOperator operator,
        String expected
) {}

public enum HookOperator {
    EQUALS,
    NOT_EQUALS,
    REGEX,
    GLOB
}
```

四种操作符分别对应 `==`、`!=`、`=~`、`~=`。

### HookAction

```java
public sealed interface HookAction permits
        HookAction.Command,
        HookAction.Prompt,
        HookAction.Http,
        HookAction.SubAgent {

    HookActionType type();

    record Command(String command) implements HookAction {}

    record Prompt(String prompt) implements HookAction {}

    record Http(
            URI url,
            String method,
            Map<String, String> headers,
            String body,
            Optional<Duration> timeout
    ) implements HookAction {}

    record SubAgent(String name, String prompt) implements HookAction {}
}
```

`SubAgent` 第一版只校验字段并写日志，不真实运行。

### HookActionType

```java
public enum HookActionType {
    COMMAND,
    PROMPT,
    HTTP,
    SUB_AGENT
}
```

和 YAML 中的 `action.type` 对应。

### HookActionResult

```java
public record HookActionResult(
        boolean success,
        String output,
        Map<String, Object> metadata
) {
    public static HookActionResult success(String output);
    public static HookActionResult failure(String output, Throwable cause);
}
```

动作执行统一返回结构。`reject: true` 使用 `output` 作为拒绝原因，空输出或失败时由运行时补兜底文案。

### HookRejection

```java
public record HookRejection(
        String hookId,
        String toolName,
        String reason
) {}
```

`pre_tool_use` 拦截结果。`AgentToolRunner` 将它转换成错误 `ToolResult`。

### PendingHookReminder

```java
public record PendingHookReminder(
        String hookId,
        String content,
        int availableTurnIndex
) {}
```

保存待注入 system reminder。`pre_send` 生成的 reminder 使用当前 turn，其他事件使用下一次 turn。

## 核心接口

### HookConfigLoader

```java
public final class HookConfigLoader {
    public HookConfig load(Path workspaceRoot, Path userHome);
}
```

读取三层配置文件中的 `hooks` 段，缺失文件跳过，存在文件解析失败时记录错误。加载顺序固定为项目级、用户级、本地级。

### HookValidator

```java
public final class HookValidator {
    public HookConfig validate(List<RawHookDefinition> rawHooks);
}
```

集中校验事件名、动作字段、条件表达式、`reject` 限制、`async` 限制、超时字段和 `sub_agent` 占位字段。返回已规范化的 `HookConfig`，失败时抛出携带所有错误的异常。

### HookConditionParser

```java
public final class HookConditionParser {
    public Optional<HookCondition> parse(String expression);
}
```

空表达式返回空条件。非空表达式禁止同时包含 `&&` 和 `||`。解析时按组合符拆分，每个子条件按空格拆为 `field operator value` 三部分，并去掉字符串值两侧引号。

### HookConditionEvaluator

```java
public final class HookConditionEvaluator {
    public boolean matches(HookCondition condition, HookContext context);
}
```

从 `HookContext` 取字段值后执行精确、反向、正则、glob 匹配。缺失字段按空字符串处理。

### HookRuntime

```java
public interface HookRuntime {
    void emit(HookEventName event, HookContext context, HookExecutionScope scope);

    Optional<HookRejection> runPreToolHooks(HookContext context, HookExecutionScope scope);

    void enqueueReminder(String sessionId, PendingHookReminder reminder);
}
```

普通事件走 `emit`，内部按事件筛选、条件匹配、`once` 判断、同步或异步执行动作。`runPreToolHooks` 是工具拦截专用同步路径。

### HookActionExecutor

```java
public interface HookActionExecutor {
    HookActionResult execute(HookDefinition hook, HookContext context, HookExecutionScope scope);
}
```

根据动作类型分发到命令、prompt、HTTP、sub_agent 执行器。

### ShellCommandRunner

```java
public final class ShellCommandRunner {
    public HookActionResult run(
            String command,
            Duration timeout,
            Map<String, String> environment,
            ToolExecutionContext context
    );
}
```

从 `BashTool` 抽取可复用的命令执行逻辑，让 Bash 工具和 Hook 命令共享沙箱、工作目录、脱敏、超时、输出截断、黑名单处理。Hook 命令额外传入环境变量。

### HookReminderStore

```java
public interface HookReminderStore {
    void add(String sessionId, PendingHookReminder reminder);

    List<SystemReminder> drain(String sessionId, int turnIndex);
}
```

内存保存待注入提醒。`MessageChannelBuilder` 构建消息时读取并清空当前可用提醒。

### HookOnceTracker

```java
public final class HookOnceTracker {
    public boolean markIfFirst(String sessionId, String hookId);
}
```

进程内记录同一会话已经执行过的 `once` Hook，不持久化。

### HookLogWriter

```java
public interface HookLogWriter {
    void log(String sessionId, HookLogEntry entry);
}
```

写入 `.lunacode/tmp/hooks/<sessionId>.log`。日志包含时间、hookId、事件、动作类型、状态、耗时、输出摘要和错误摘要。

## 模块设计

### 配置加载与校验模块

**职责：** 从三层配置读取 `hooks`，转换为 `HookDefinition` 并统一校验。

**对外接口：** `HookConfigLoader.load`、`HookValidator.validate`。

**依赖：** Jackson YAML、`HookConditionParser`、工作区路径、用户主目录。

模块会将 YAML 中的 snake_case 字段规范化，例如 `inject_result`、`timeout_ms`。所有校验错误聚合后一次性抛出，供 `LunaCodeApplication` 打印并终止启动。

### 条件表达式模块

**职责：** 解析和执行 `if` 表达式。

**对外接口：** `HookConditionParser.parse`、`HookConditionEvaluator.matches`。

**依赖：** Java 正则、glob 转正则工具。

解析器只处理 `field operator value` 子条件，不支持括号和优先级。`tool`、`args.<key>` 作为用户友好的别名保留，内部映射到 `toolName` 和 `toolArgs.<key>`。

### Hook 运行时模块

**职责：** 接收生命周期事件、匹配 Hook、处理 `once`、调度同步/异步动作、记录日志、写入 system reminder。

**对外接口：** `HookRuntime.emit`、`HookRuntime.runPreToolHooks`。

**依赖：** `HookConfig`、`HookConditionEvaluator`、`HookActionExecutor`、`HookOnceTracker`、`HookLogWriter`、后台执行线程池。

普通事件失败只写日志。异步 Hook 提交到专用线程池，失败同样只写日志。`pre_tool_use` 拦截路径不允许异步，按命中顺序同步执行，遇到 `reject: true` 立即返回 `HookRejection`。

### 动作执行模块

**职责：** 执行 command、prompt、http、sub_agent 四类动作。

**对外接口：** `HookActionExecutor.execute`。

**依赖：** `ShellCommandRunner`、JDK `HttpClient`、`HookReminderStore`、`HookLogWriter`。

command 动作把上下文字段注入环境变量。HTTP 动作在 `url`、`headers`、`body` 中做轻量变量替换。prompt 动作直接加入 reminder store。sub_agent 动作只返回未实现结果并写日志。

### 提示词注入模块

**职责：** 将 Hook 产生的提醒插入 system reminder。

**对外接口：** 扩展 `SystemReminderBuilder` 和 `MessageChannelBuilder`，新增可选 `HookReminderStore` 依赖。

**依赖：** 现有 `SystemReminder`、`SystemReminderKind`、`PromptContextBuilder`。

构建 PromptBundle 时按当前会话和 turnIndex drain 可用 reminder。`pre_send` 在 PromptBundle 构建前触发，因此写入的 reminder 会进入当前请求。

### 生命周期接入模块

**职责：** 在现有控制流里创建 `HookContext` 并发射事件。

**对外接口：** 通过构造函数把 `HookRuntime` 注入到 `LunaCodeApplication`、`DefaultChatOrchestrator`、`DefaultAgentLoop`、`AgentToolRunner`、`DefaultContextManager`、`SlashCommandDispatcher`。

**依赖：** 当前会话 id、turnIndex、工具调用参数、错误信息、文件路径提取。

每个接入点只做上下文组装和调用，不执行条件判断或动作逻辑，避免业务流程和 Hook 细节耦合。

## 模块交互

启动时：

```text
LunaCodeApplication
  -> HookConfigLoader.load(workspaceRoot, userHome)
  -> HookValidator.validate(...)
  -> DefaultHookRuntime(...)
  -> emit(startup)
  -> SessionService restore/create
  -> emit(session_start)
```

用户消息到模型请求：

```text
DefaultChatOrchestrator.submitUserMessage
  -> commandDispatcher.dispatch(...)
  -> 非命令时 startAgentRequest
  -> DefaultAgentLoop.run
  -> emit(turn_start)
  -> emit(pre_send)
  -> PromptContextBuilder.build(...) 读取 HookReminderStore
  -> AgentTurnRunner.runTurn
  -> emit(post_receive)
  -> emit(turn_end)
```

工具调用：

```text
DefaultAgentLoop
  -> AgentToolRunner.executeToolBatches
  -> runPreToolHooks(pre_tool_use)
     -> 若返回 HookRejection，生成错误 ToolResult 并跳过权限与工具执行
  -> 权限黑名单/权限网关/用户确认
  -> ToolExecutor.execute
  -> emit(post_tool_use)
  -> 若 result.isError，emit(error)
  -> 若 WriteFile/EditFile 成功，emit(file_change)
```

命令动作：

```text
HookRuntime
  -> HookActionExecutor
  -> CommandHookActionExecutor
  -> ShellCommandRunner
  -> CommandSandbox.wrapShellCommand
  -> ProcessBuilder with Hook environment
  -> HookActionResult
  -> HookLogWriter
```

提示词动作：

```text
HookActionExecutor
  -> PromptHookActionExecutor
  -> HookReminderStore.add(sessionId, reminder)
  -> MessageChannelBuilder 下一次 build 时 drain
```

上下文压缩：

```text
DefaultContextManager.runCompaction
  -> emit(compact) before attempt
  -> existing compaction flow
  -> failure also emits error
```

Slash Command：

```text
SlashCommandDispatcher.dispatch
  -> parse and find command
  -> execute handler
  -> emit(command_execute)
```

关闭：

```text
LunaCodeApplication finally
  -> emit(session_end)
  -> emit(shutdown)
  -> close async executors/resources
```

## 文件组织

```text
src/main/java/com/lunacode/
├── app/
│   └── LunaCodeApplication.java
├── agent/
│   ├── DefaultAgentLoop.java
│   └── execution/
│       └── AgentToolRunner.java
├── command/
│   └── SlashCommandDispatcher.java
├── config/
│   ├── ProviderConfig.java
│   └── ConfigLoader.java
├── context/
│   └── DefaultContextManager.java
├── hook/
│   ├── HookAction.java
│   ├── HookActionType.java
│   ├── HookActionExecutor.java
│   ├── HookActionResult.java
│   ├── HookConfig.java
│   ├── HookConfigLoader.java
│   ├── HookCondition.java
│   ├── HookConditionEvaluator.java
│   ├── HookConditionParser.java
│   ├── HookContext.java
│   ├── HookDefinition.java
│   ├── HookEventName.java
│   ├── HookExecutionScope.java
│   ├── HookLogEntry.java
│   ├── HookLogWriter.java
│   ├── HookOnceTracker.java
│   ├── HookRejection.java
│   ├── HookReminderStore.java
│   ├── HookSource.java
│   ├── HookValidator.java
│   ├── DefaultHookRuntime.java
│   ├── NoOpHookRuntime.java
│   ├── FileHookLogWriter.java
│   ├── InMemoryHookOnceTracker.java
│   ├── InMemoryHookReminderStore.java
│   ├── CommandHookActionExecutor.java
│   ├── HttpHookActionExecutor.java
│   ├── PromptHookActionExecutor.java
│   ├── SubAgentPlaceholderActionExecutor.java
│   └── ShellCommandRunner.java
├── prompt/
│   ├── MessageChannelBuilder.java
│   └── SystemReminderBuilder.java
└── tool/
    └── BashTool.java

src/test/java/com/lunacode/
├── hook/
│   ├── HookConfigLoaderTest.java
│   ├── HookConditionParserTest.java
│   ├── HookConditionEvaluatorTest.java
│   ├── DefaultHookRuntimeTest.java
│   ├── CommandHookActionExecutorTest.java
│   └── HttpHookActionExecutorTest.java
├── agent/
│   └── AgentHookIntegrationTest.java
├── command/
│   └── SlashCommandHookTest.java
└── context/
    └── ContextHookIntegrationTest.java
```

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 配置入口 | 新增 `HookConfigLoader` 读取三个固定 Hook 配置来源 | Hook 的三层追加合并语义和现有 provider 配置不同，独立加载更清楚 |
| 配置错误处理 | 任一 Hook 非法则启动失败 | Hook 会影响安全拦截，静默跳过风险高 |
| 条件语法 | 简单解析器，不引入表达式引擎 | 满足 `==`、`!=`、`=~`、`~=` 与单一组合符需求，避免优先级复杂度 |
| 字段别名 | 支持 `tool` 与 `args.<key>` 作为 `toolName` 和 `toolArgs.<key>` 简写 | 与用户示例一致，同时保持内部 `HookContext` 稳定 |
| 工具前拦截 | `pre_tool_use` 专用同步路径 | 拦截必须等待动作结果并能取消工具调用，不能进入异步分支 |
| 命令执行 | 抽 `ShellCommandRunner`，Bash 工具和 Hook 共用 | 复用沙箱、脱敏、超时和输出截断，同时支持 Hook 环境变量 |
| Prompt 注入 | 统一进入 system reminder | 不改写用户原文或模型回复，行为更可追踪 |
| `once` 记录 | 进程内按 `sessionId + hookId` 记录 | 符合第一版不持久化边界 |
| HTTP 动作 | 使用 JDK `HttpClient` | 避免新增依赖，满足最小 HTTP 动作需求 |
| 日志 | 写入 `.lunacode/tmp/hooks/<sessionId>.log` | 方便按会话追踪命中、失败和拒绝原因 |
| sub_agent | 校验配置并写未实现日志 | 保留 YAML 形态，等待 SubAgent 章节对接 |
| 文件变化 | 只由 LunaCode 文件工具成功修改触发 | 避免引入跨平台文件 watcher 复杂度 |

