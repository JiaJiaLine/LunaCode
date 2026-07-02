# LunaCode 斜杠命令注册与分发 Plan

## 架构概览

本阶段新增一个独立的 `com.lunacode.command` 包承载命令基础设施：命令定义、解析、注册、冲突检测、分发和补全。这个包只表达“斜杠命令是什么、如何匹配、如何执行”，不直接依赖 TUI 渲染细节，也不直接调用模型。

`DefaultChatOrchestrator` 继续作为用户输入的主入口，但把当前散落在 `submitUserMessage` 中的 `/compact`、`/session`、`/memory`、`/permissions`、`/cancel`、`/plan`、`/do` 判断迁移到命令注册中心。它实现一组命令运行时接口，向命令提供状态查询、模式切换、权限切换、上下文压缩、会话/记忆处理、发送预设用户消息等能力。

`LanternaLunaTui` 负责终端交互层：回车仍调用 orchestrator 提交输入，Tab 调用命令补全接口，`/clear` 通过命令运行时触发 TUI 的可见输出清理。TUI 只维护输入行、临时补全面板和状态栏渲染，不知道具体业务命令如何执行。

`StatusSnapshot` 扩展 Agent 工作模式字段，让状态栏和 `/status` 能同时展示 `[DEFAULT]/[PLAN]` 与权限模式。权限模式会话从“AgentMode.PLAN 强制推导权限 plan”调整为“当前权限模式即实际运行权限”，由 `/plan` 命令显式切到 `plan`，这样用户在 `[PLAN]` 期间手动执行 `/permission acceptEdits` 后，后续 Agent 运行可以尊重手动修改。

## 核心数据结构

### SlashCommandType

```java
public enum SlashCommandType {
    LOCAL,
    UI_STATE,
    PROMPT
}
```

说明命令执行模式：本地状态读取、界面/模式状态变更、生成预设用户消息交给 Agent。

### SlashCommandDefinition

```java
public record SlashCommandDefinition(
        String name,
        List<String> aliases,
        String description,
        String usage,
        SlashCommandType type,
        String argumentHint,
        boolean hidden,
        SlashCommandHandler handler
) {}
```

`name` 和 `aliases` 都使用带 `/` 的外部形式。构造或注册时统一校验非空、以 `/` 开头，并做小写索引。

### SlashCommandHandler

```java
@FunctionalInterface
public interface SlashCommandHandler {
    void handle(SlashCommandContext context);
}
```

命令处理器通过 `SlashCommandContext` 访问参数、注册中心和运行时能力。处理器不直接持有 TUI 或 provider。

### SlashCommandContext

```java
public record SlashCommandContext(
        SlashCommandInvocation invocation,
        SlashCommandRegistry registry,
        CommandRuntime runtime
) {
    public String args();
}
```

`args()` 返回解析后的参数文本。`/help` 使用 `registry` 查命令详情，业务命令使用 `runtime` 执行本地动作。

### SlashCommandInvocation

```java
public record SlashCommandInvocation(
        String rawInput,
        String rawName,
        String normalizedName,
        String args
) {}
```

保存原始输入、用户输入的命令名、归一化命令名和参数，便于错误提示和测试。

### SlashCommandParseResult

```java
public sealed interface SlashCommandParseResult {
    record NotCommand() implements SlashCommandParseResult {}
    record Command(SlashCommandInvocation invocation) implements SlashCommandParseResult {}
}
```

解析器只判断是否为斜杠命令并切分输入，不判断命令是否存在。

### SlashCommandRegistry

```java
public final class SlashCommandRegistry {
    public void register(SlashCommandDefinition definition);
    public SlashCommandDefinition require(String nameOrAlias);
    public Optional<SlashCommandDefinition> find(String nameOrAlias);
    public List<SlashCommandDefinition> visibleCommands();
    public List<SlashCommandName> visibleNames();
}
```

内部使用 `LinkedHashMap<String, SlashCommandDefinition>` 保存归一化名称索引，保证帮助和补全顺序稳定。注册时发现主名或别名冲突直接抛出 `SlashCommandRegistrationException`。

### SlashCommandName

```java
public record SlashCommandName(
        String value,
        String ownerCommand
) {}
```

补全使用的候选名称。`value` 是 `/h`、`/help` 等可补全文本；`ownerCommand` 用于显示候选所属主命令。

### SlashCommandParser

```java
public final class SlashCommandParser {
    public SlashCommandParseResult parse(String input);
}
```

处理空输入、非斜杠输入、命令名小写归一化和参数切分。

### SlashCommandDispatcher

```java
public final class SlashCommandDispatcher {
    public DispatchResult dispatch(String input, CommandRuntime runtime);
}
```

分发顺序为：解析输入；非命令返回 `NOT_COMMAND`；`/cancel` 优先执行；运行时忙碌或等待时拦截非 `/cancel` 命令；查找命令定义；未命中显示 `/help` 引导；命中后执行 handler。

### DispatchResult

```java
public enum DispatchResult {
    NOT_COMMAND,
    HANDLED
}
```

`DefaultChatOrchestrator` 用它判断是否继续普通 Agent 流程。

### SlashCommandCompletion

```java
public sealed interface SlashCommandCompletion {
    record NoMatch() implements SlashCommandCompletion {}
    record Single(String replacement) implements SlashCommandCompletion {}
    record Multiple(List<SlashCommandName> candidates) implements SlashCommandCompletion {}
}
```

TUI 根据结果修改输入行或显示临时候选菜单。

### SlashCommandCompleter

```java
public final class SlashCommandCompleter {
    public SlashCommandCompletion complete(String input, int cursorIndex);
}
```

只在光标位于第一个 token 内、且 token 以 `/` 开头时补全命令名。参数区不补全。

### CommandRuntime

```java
public interface CommandRuntime {
    boolean isBusy();
    boolean hasPendingUserAnswer();
    boolean hasPendingPermissionAnswer();
    boolean hasPendingDangerousModeConfirmation();
    CommandRuntimeStatus status();

    void showInfo(String message);
    void showWarning(String message);
    void showError(String message);
    void requestRender();

    void cancelCurrentRun();
    void clearVisibleScreen();
    void sendUserMessage(String message);
    void compactContext();

    void enterPlanMode();
    void enterDefaultMode();
    void switchPermissionMode(PermissionMode mode);
    void requestDangerousPermissionMode(PermissionMode mode);
    void runSessionCommand(String rawInput);
    void runMemoryCommand(String rawInput);
}
```

`DefaultChatOrchestrator` 实现运行时能力；`clearVisibleScreen` 通过注入的 UI 控制接口转发给 TUI。

### CommandRuntimeStatus

```java
public record CommandRuntimeStatus(
        AgentMode agentMode,
        PermissionMode permissionMode,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        String state,
        String sessionShortId,
        Boolean memoryAutoUpdateEnabled,
        String memoryLatestState
) {}
```

`/status` 使用该结构格式化完整状态。它由 orchestrator 从 `StatusSnapshot` 和当前模式派生。

### StatusSnapshot

```java
public record StatusSnapshot(
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        String state,
        String errorSummary,
        String toolName,
        String toolSummary,
        AgentMode agentMode,
        PermissionMode permissionMode,
        String sessionShortId,
        Boolean memoryAutoUpdateEnabled,
        String memoryLatestState
) {}
```

保持现有构造器兼容，通过重载构造器默认 `AgentMode.DEFAULT` 和 `PermissionMode.DEFAULT`，避免现有测试一次性大面积重写。

### CommandUiController

```java
public interface CommandUiController {
    void clearVisibleScreen();
}
```

`LanternaLunaTui` 实现该接口。`DefaultChatOrchestrator` 持有一个可替换的 controller，启动时默认为 no-op，TUI 创建后注入真实实现。

### InputLineBuffer

```java
void replaceCommandToken(String replacement);
void replaceAll(String value);
```

补全需要替换第一个 token 并把光标移动到 token 末尾；清屏需要清空输入行。

## 模块设计

### command 核心模块

**职责：** 提供命令定义、解析、注册、补全和分发的通用能力。  
**对外接口：** `SlashCommandRegistry`、`SlashCommandParser`、`SlashCommandDispatcher`、`SlashCommandCompleter`。  
**依赖：** Java 标准库、`runtime.AgentMode`、`permission.PermissionMode`。不依赖 TUI、provider、agent、conversation。

### command 内置命令模块

**职责：** 统一注册 `/help`、`/compact`、`/clear`、`/plan`、`/do`、`/session`、`/memory`、`/permission`、`/status`、`/review`、`/cancel` 及固定别名。  
**对外接口：** `BuiltinSlashCommands.registerAll(SlashCommandRegistry registry)`。  
**依赖：** `command` 核心类型。内置 handler 只通过 `CommandRuntime` 调用业务能力。

### orchestrator 命令运行时模块

**职责：** 在 `DefaultChatOrchestrator` 中实现 `CommandRuntime`，把命令动作映射到现有上下文压缩、会话、记忆、权限、取消和 Agent 提交流程。  
**对外接口：** `submitUserMessage`、`completeSlashCommand`、`setCommandUiController`、`status`。  
**依赖：** 现有 `context`、`session`、`memory`、`permission`、`runtime`、`conversation`、`agent` 模块。

### TUI 命令交互模块

**职责：** 在 `LanternaLunaTui` 中处理 Tab 键、临时候选菜单、清屏和模式标记渲染。  
**对外接口：** `clearVisibleScreen`，以及现有 `requestRender`、`render`。  
**依赖：** `ChatOrchestrator` 的补全接口和 `StatusSnapshot`，不依赖具体命令 handler。

### 状态与模式模块

**职责：** 保存当前 Agent 工作模式、权限模式和 `/plan` 进入前权限模式快照。  
**对外接口：** `enterPlanMode`、`enterDefaultMode`、`switchPermissionMode`、`status`。  
**依赖：** `AgentMode`、`PermissionMode`、`PermissionModeSession`。

### 测试模块

**职责：** 覆盖命令基础设施、orchestrator 集成、TUI 补全/清屏和现有命令兼容行为。  
**对外接口：** JUnit 测试类。  
**依赖：** 现有测试风格中的 fake provider、direct executor、反射测试 TUI 私有渲染方法。

## 模块交互

### 启动注册

```text
LunaCodeApplication
  -> new SlashCommandRegistry()
  -> BuiltinSlashCommands.registerAll(registry)
  -> new SlashCommandDispatcher(registry, parser)
  -> new SlashCommandCompleter(registry)
  -> new DefaultChatOrchestrator(..., dispatcher, completer, ...)
  -> new LanternaLunaTui(...)
  -> orchestrator.setCommandUiController(tui)
```

命令冲突在 `registerAll` 阶段暴露，应用启动失败并打印清晰错误。

### 回车输入分发

```text
LanternaLunaTui.handleKey(Enter)
  -> content = input.consume().strip()
  -> orchestrator.submitUserMessage(content)
      -> dispatcher.dispatch(content, this)
          -> 非命令: NOT_COMMAND
          -> /cancel: cancelCurrentRun()
          -> 忙碌/等待 + 其他命令: showWarning(...)
          -> 未知命令: showError(... /help ...)
          -> 已知命令: handler.handle(context)
      -> NOT_COMMAND 时按现有普通消息/回答流程继续
```

普通输入在等待用户问题、权限确认和危险权限确认时仍按现有回答流程处理；斜杠输入在这些状态下只有 `/cancel` 会执行。

### Tab 补全

```text
LanternaLunaTui.handleKey(Tab)
  -> orchestrator.completeSlashCommand(input.content(), input.cursorIndex())
      -> SlashCommandCompleter.complete(...)
  -> NoMatch: 保持输入不变
  -> Single: input.replaceCommandToken(replacement)
  -> Multiple: showTemporaryCompletionMenu(candidates)
```

临时候选菜单由 TUI 维护，不进入 conversation。任意继续输入、再次 Tab、回车或取消动作先清除旧菜单再重绘输入行。

### `/plan` 与 `/do`

```text
/plan
  -> save previousPermissionMode if not already PLAN session
  -> agentMode = PLAN
  -> permissionMode = PLAN
  -> planPermissionManuallyChanged = false
  -> showInfo(...)

/permission acceptEdits while agentMode == PLAN
  -> permissionMode = ACCEPT_EDITS
  -> planPermissionManuallyChanged = true

/do
  -> agentMode = DEFAULT
  -> if !planPermissionManuallyChanged restore previousPermissionMode
  -> else keep current permissionMode
```

`AgentRunConfig` 使用当前 `agentMode` 和当前 `permissionMode`。`PermissionModeSession.modeFor(AgentMode)` 调整为返回当前权限模式，不再因为 `AgentMode.PLAN` 强制返回 `PLAN`。

### `/review`

```text
/review [args]
  -> build prompt:
     请审查当前 git diff 中的代码变更。重点关注：
     1. 逻辑错误
     2. 安全问题
     3. 性能问题
     4. 代码风格
     [额外关注：args]
  -> runtime.sendUserMessage(prompt)
  -> 进入普通 Agent 流程
```

该命令不直接运行 git，也不静态分析代码；它只生成用户消息。

### `/clear`

```text
/clear
  -> runtime.clearVisibleScreen()
      -> LanternaLunaTui.clearVisibleScreen()
          -> 发送清屏 ANSI
          -> 清空当前输入行
          -> 清除临时候选菜单
          -> 保留 startedMessages/finishedMessages/printedLengths
```

保留已渲染消息集合可避免下一次 render 把历史消息重新打印出来，同时 conversation 历史仍保留给 Agent。

## 文件组织

```text
src/main/java/com/lunacode/
├── command/
│   ├── BuiltinSlashCommands.java
│   ├── CommandRuntime.java
│   ├── CommandRuntimeStatus.java
│   ├── CommandUiController.java
│   ├── DispatchResult.java
│   ├── SlashCommandCompleter.java
│   ├── SlashCommandCompletion.java
│   ├── SlashCommandContext.java
│   ├── SlashCommandDefinition.java
│   ├── SlashCommandDispatcher.java
│   ├── SlashCommandHandler.java
│   ├── SlashCommandInvocation.java
│   ├── SlashCommandName.java
│   ├── SlashCommandParseResult.java
│   ├── SlashCommandParser.java
│   ├── SlashCommandRegistrationException.java
│   ├── SlashCommandRegistry.java
│   └── SlashCommandType.java
├── app/
│   └── LunaCodeApplication.java
├── orchestrator/
│   ├── ChatOrchestrator.java
│   ├── DefaultChatOrchestrator.java
│   └── StatusSnapshot.java
├── permission/
│   └── PermissionModeSession.java
└── tui/
    ├── InputLineBuffer.java
    └── LanternaLunaTui.java

src/test/java/com/lunacode/
├── command/
│   ├── BuiltinSlashCommandsTest.java
│   ├── SlashCommandCompleterTest.java
│   ├── SlashCommandDispatcherTest.java
│   ├── SlashCommandParserTest.java
│   └── SlashCommandRegistryTest.java
├── orchestrator/
│   ├── SlashCommandOrchestratorTest.java
│   └── PermissionCommandTest.java
└── tui/
    ├── InputLineBufferTest.java
    └── LanternaLunaTuiCommandCompletionTest.java
```

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 命令基础设施位置 | 新增 `com.lunacode.command` | 避免继续膨胀 orchestrator，也让解析、注册、补全可以独立测试 |
| 命令执行入口 | `DefaultChatOrchestrator.submitUserMessage` 先走 dispatcher | 保持 TUI 外部调用不变，同时集中处理斜杠命令和普通输入分流 |
| UI 解耦 | 使用 `CommandUiController` 和 `CommandRuntime` | 命令不直接依赖 Lanterna；TUI 只实现清屏和补全展示 |
| 状态模式保存 | `StatusSnapshot` 增加 `AgentMode` | 状态栏和 `/status` 都需要可观测的 `[DEFAULT]/[PLAN]` |
| `/plan` 权限联动 | 命令显式设置权限 `plan`，并调整 `PermissionModeSession.modeFor` | 支持 `/plan` 自动安全收敛，也支持用户在 `[PLAN]` 中手动改权限 |
| `/review` 实现 | 生成固定 prompt 后调用普通 Agent 提交流程 | 满足提示词命令定位，不引入独立 git diff 分析器 |
| `/clear` 行为 | 只清终端可见输出，保留 conversation 和已渲染消息集合 | 满足“清屏不清上下文”，同时避免下一次 render 重刷旧消息 |
| 补全菜单 | TUI 维护临时候选面板，不做方向键选择状态机 | 符合 spec 边界，降低交互复杂度 |
| 短别名顺序 | 注册中心用 `LinkedHashMap` 保存稳定顺序 | `/help` 和 Tab 候选输出可测试、可预测 |
| 现有 session/memory | 保留 handler 语义，通过命令 runtime 转调 | 降低迁移风险，确保兼容已有测试和用户习惯 |
