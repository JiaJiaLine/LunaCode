# SubAgent 与后台任务 Plan

## 架构概览

本章新增 `subagent` 和 `background` 两组基础设施。`subagent` 负责角色定义加载、子 Agent 启动参数解析、工具过滤、模型和权限解析，以及创建可运行的子 Agent 实例；`background` 负责统一后台任务生命周期、完成通知、进度追踪和前台运行接管。现有 `DefaultAgentLoop` 继续承担“跑到底”的 Agent 循环，不重写核心推理逻辑。

`Agent` 工具作为模型可见的唯一入口注册进现有 `ToolRegistry`。工具 schema 固定，包含 `task`、可选 `subagent_type` 和可选 `run_in_background`。未传 `subagent_type` 时走 Fork 路径并强制后台；传入时走定义式路径。工具执行时从当前 Agent 运行上下文读取父对话、父配置、工具策略和嵌套状态，再交给 `SubAgentService` 启动或调度。

后台任务由 `BackgroundTaskManager` 统一管理。显式后台、Fork 隐式后台、自动超时后台、ESC 手动后台都转成同一个 `BackgroundTask`。任务完成后通过通知队列或回调把 task id 推给 `DefaultChatOrchestrator`，由 orchestrator 向主对话追加 `<task-notification>` assistant 消息，并刷新 TUI。

Hook 的 `sub_agent` 动作从占位执行器替换为真实执行器，复用 `SubAgentService` 和 `BackgroundTaskManager`，始终后台运行。Skill fork 第一版保持现有用户可见语义，只确保不被本章改动破坏；底层迁移留出适配点，不作为本章强制重构。

## 核心数据结构

### AgentDefinition

```java
public record AgentDefinition(
        String agentType,
        String whenToUse,
        List<String> tools,
        List<String> disallowedTools,
        SubAgentModelSpec model,
        OptionalInt maxTurns,
        Optional<PermissionMode> permissionMode,
        String systemPrompt,
        Path filePath,
        AgentDefinitionSourceKind source
) {}
```

`agentType` 来自 frontmatter `name`，`whenToUse` 来自 frontmatter `description`。`model` 支持 `inherit`、`sonnet`、`opus`、`haiku` 和后续可扩展的完整模型名。`filePath` 对内置或插件资源可使用可诊断的虚拟路径。

### AgentDefinitionDiagnostic

```java
public record AgentDefinitionDiagnostic(
        DiagnosticLevel level,
        String sourceId,
        String message
) {}
```

用于报告单个定义跳过、同名覆盖、未知工具、非法字段和模型别名缺失。应用启动时打印 warning，不中断主程序。

### AgentToolRequest

```java
public record AgentToolRequest(
        String task,
        Optional<String> subagentType,
        boolean runInBackground
) {}
```

由 `AgentTool` 从 JSON 入参解析。`subagentType` 为空时表示 Fork。`task` 为空时返回参数错误。

### SubAgentLaunchRequest

```java
public record SubAgentLaunchRequest(
        SubAgentKind kind,
        Optional<AgentDefinition> definition,
        String task,
        boolean requestedBackground,
        SubAgentParentContext parentContext,
        SubAgentNotificationPolicy notificationPolicy
) {}
```

`SubAgentKind` 为 `DEFINED` 或 `FORK`。`notificationPolicy` 区分普通 `Agent` 工具、Hook 子任务和兼容 Skill fork 的结果格式。

### SubAgentParentContext

```java
public record SubAgentParentContext(
        ConversationManager parentConversation,
        AgentRunConfig parentConfig,
        ToolAccessPolicy parentToolPolicy,
        boolean parentIsBackground,
        boolean parentIsFork,
        String sessionId,
        Path workspaceRoot
) {}
```

该上下文由 `DefaultAgentLoop` 和 `AgentToolRunner` 在执行工具前建立，供 `AgentTool` 读取。它不进入模型上下文，只在本地运行时传递。

### SubAgentRunHandle

```java
public interface SubAgentRunHandle {
    String id();
    CompletableFuture<SubAgentResult> completion();
    CancellationToken cancellationToken();
    ProgressTracker progress();
    void markAdoptedByBackground(String taskId);
}
```

前台子 Agent 也先包装成 handle。这样超时自动后台和 ESC 手动后台都可以通过同一个 handle 接管，而不是杀掉重跑。

### SubAgentResult

```java
public record SubAgentResult(
        String summary,
        String fullResult,
        TokenUsage usage,
        int toolCallCount,
        boolean reachedMaxTurns,
        Optional<String> failureReason
) {}
```

`summary` 用于状态和通知标题，`fullResult` 写入 `<task-notification>`。失败时 `failureReason` 可见，`fullResult` 可包含错误说明。

### BackgroundTask

```java
public final class BackgroundTask {
    private final String id;
    private final SubAgentRunHandle subAgent;
    private final String task;
    private final Instant startTime;
    private volatile BackgroundTaskStatus status;
    private volatile String result;
    private volatile Instant endTime;
    private final CancellationToken cancellationToken;
    private final ProgressTracker progress;
}
```

`BackgroundTaskStatus` 第一版为 `RUNNING`、`COMPLETED`、`FAILED`。`ProgressTracker` 记录工具调用次数、token 消耗和最近活动文本。

### ForegroundSubAgentTracker

```java
public interface ForegroundSubAgentTracker {
    void setCurrent(SubAgentRunHandle handle);
    Optional<SubAgentRunHandle> current();
    Optional<String> adoptCurrentToBackground();
    void clear(SubAgentRunHandle handle);
}
```

`AgentTool` 前台等待定义式子 Agent 时登记当前 handle。TUI 的 ESC 通过 orchestrator 调用 `adoptCurrentToBackground()`，如果存在前台子 Agent 就转后台，否则保持现有取消主 Agent 行为。

## 核心接口

### AgentDefinitionCatalog

```java
public interface AgentDefinitionCatalog {
    AgentDefinitionCatalogSnapshot snapshot();
    Optional<AgentDefinition> find(String agentType);
    List<AgentDefinitionDiagnostic> diagnostics();
}
```

实现类 `DefaultAgentDefinitionCatalog` 复用 Skill catalog 的思路：按 source priority 发现候选、解析、校验、合并。同名覆盖使用项目级、用户级、内置、插件顺序。单个坏定义只产出 warning。

### AgentDefinitionSource

```java
public interface AgentDefinitionSource {
    List<AgentDefinitionCandidate> discover(Path projectRoot, Path userHome);
}
```

实现包括：

- `FileSystemAgentDefinitionSource.project()`：扫描 `.lunacode/agents/*.md`。
- `FileSystemAgentDefinitionSource.user()`：扫描 `~/.lunacode/agents/*.md`。
- `BuiltinAgentDefinitionSource`：加载内置定义。
- `PluginAgentDefinitionSource`：读取插件贡献的定义；没有插件定义时返回空列表。

### AgentDefinitionParser

```java
public interface AgentDefinitionParser {
    AgentDefinitionParseResult parse(AgentDefinitionCandidate candidate);
}
```

`FrontmatterAgentDefinitionParser` 使用 Jackson YAML 解析 frontmatter。字段校验集中在 parser 和 catalog：`name`、`description` 必填；`tools` 和 `disallowedTools` 必须是字符串数组；`maxTurns` 必须大于 0；`permissionMode` 复用 `PermissionMode.fromConfig`。

### SubAgentService

```java
public interface SubAgentService {
    AgentToolResult launchFromTool(AgentToolRequest request, SubAgentParentContext parentContext);
    String launchFromHook(String subagentType, String task, HookExecutionScope scope);
    SubAgentRunHandle startForeground(SubAgentLaunchRequest request);
    String launchBackground(SubAgentLaunchRequest request);
}
```

`launchFromTool` 负责 `subagent_type` 分流、Fork 强制后台、定义式前台等待、显式后台和自动超时。`launchFromHook` 始终后台，并返回 task id 给 Hook 日志。

### SubAgentRunnerFactory

```java
public interface SubAgentRunnerFactory {
    SubAgentRunHandle start(SubAgentLaunchRequest request);
}
```

实现类创建独立 `ConversationManager`、独立 `ContextManager`、非交互权限 broker、子任务级 `AgentRunConfig` 和子任务级 `AgentToolRunner`。Provider、HookRuntime、ToolRegistry、ToolExecutor 和文件系统基础设施共享。

### BackgroundTaskManager

```java
public interface BackgroundTaskManager {
    String launch(SubAgentLaunchRequest request);
    String adoptRunning(SubAgentRunHandle handle, String task);
    Optional<BackgroundTaskSnapshot> get(String taskId);
    List<BackgroundTaskSnapshot> list();
    void addListener(BackgroundTaskListener listener);
}
```

`launch` 生成 id，登记 `RUNNING`，异步等待 handle 完成；完成后设置 `COMPLETED` 或 `FAILED` 并通知 listener。`adoptRunning` 不重启 Agent，只把已有 handle 放入 task 表并继续等待其 completion。

### TaskNotificationListener

```java
public interface BackgroundTaskListener {
    void onTaskFinished(String taskId);
}
```

`DefaultChatOrchestrator` 实现或注册该 listener，收到通知后读取 task snapshot，格式化 `<task-notification>` 并追加到主 conversation。

## 模块设计

### AgentDefinitionCatalog 模块

**职责：** 发现、解析、校验、合并角色定义，并暴露给 `AgentTool` 和 prompt 描述。  
**对外接口：** `snapshot()`、`find()`、`diagnostics()`。  
**依赖：** 文件系统、Jackson YAML、可用工具名 supplier、模型别名配置。  
**关键行为：** 同名覆盖按来源优先级；单个坏文件跳过；`name` 和 `description` 映射成内部 `agentType` 和 `whenToUse`。

### AgentTool 模块

**职责：** 提供模型可调用的统一 `Agent` 工具。  
**对外接口：** 实现 `Tool`，schema 固定包含 `task`、`subagent_type`、`run_in_background`。  
**依赖：** `SubAgentService`、`AgentExecutionContextHolder`、`AgentDefinitionCatalog`。  
**关键行为：** 未传 `subagent_type` 走 Fork；指定 `subagent_type` 查角色；后台返回 `async_launched` 和 task id；前台完成返回最终结果；非法或被禁止的嵌套返回工具错误。

### AgentExecutionContextHolder 模块

**职责：** 把当前 Agent 的运行上下文传给工具执行层。  
**对外接口：** `withContext(context, runnable)`、`current()`。  
**依赖：** 无外部依赖。  
**关键行为：** `AgentToolRunner` 在执行每个工具前设置 thread-local；并发工具批次在每个 future 内设置同一父上下文。普通工具不感知该上下文。

### SubAgentService 模块

**职责：** 根据工具或 Hook 请求组装子 Agent 启动参数，并选择前台、后台或 Fork 路径。  
**对外接口：** `launchFromTool()`、`launchFromHook()`。  
**依赖：** `AgentDefinitionCatalog`、`SubAgentRunnerFactory`、`BackgroundTaskManager`、`ForegroundSubAgentTracker`、模型解析器。  
**关键行为：** Fork 强制后台；后台 Agent 禁止再 spawn Agent；前台定义式运行超过 `getAutoBackgroundMs()` 后自动交给后台任务管理器。

### SubAgentRunnerFactory 模块

**职责：** 创建真正运行的子 Agent。  
**对外接口：** `start(SubAgentLaunchRequest)`。  
**依赖：** Provider、ToolRegistry、ToolExecutor、HookRuntime、PromptContextBuilder、权限引擎工厂。  
**关键行为：** 为定义式创建空白对话；为 Fork 复制父对话原始消息；创建子任务级 `AgentRunConfig`；使用 `DenyingPermissionConfirmationBroker` 确保非交互。

### ToolPolicyResolver 模块

**职责：** 计算子 Agent 最终工具集。  
**对外接口：** `resolve(parentPolicy, definition, runScope)`。  
**依赖：** `ToolRegistry` 可用工具名。  
**关键行为：** 先取 `tools` 白名单或父工具集，再移除 `disallowedTools`，再应用全局禁止和后台安全禁止；黑名单优先；后台和 Fork 都移除 `Agent`。

### BackgroundTaskManager 模块

**职责：** 管理所有后台任务的生命周期。  
**对外接口：** `launch()`、`adoptRunning()`、`get()`、`list()`、`addListener()`。  
**依赖：** `ExecutorService`、`SubAgentRunnerFactory`、时钟。  
**关键行为：** 异步等待子 Agent 完成；异常转 `FAILED`；完成后通知 listener；不做持久化。

### ForegroundSubAgentTracker 模块

**职责：** 管理当前正在前台等待的子 Agent。  
**对外接口：** `setCurrent()`、`adoptCurrentToBackground()`、`clear()`。  
**依赖：** `BackgroundTaskManager`。  
**关键行为：** ESC 时优先接管前台子 Agent；没有前台子 Agent 时保留原取消当前主 run 的行为。

### TaskNotification 模块

**职责：** 把后台任务完成事件写回主对话。  
**对外接口：** `format(BackgroundTaskSnapshot)`、`inject(taskId)`。  
**依赖：** `ConversationManager`、`BackgroundTaskManager`。  
**关键行为：** 生成 `<task-notification>` 文本，包含 task id、状态、摘要和完整结果；用 `addAssistantMessage` 写入主历史；不自动触发主 Agent 新一轮。

### HookSubAgentExecutor 模块

**职责：** 替换现有 `SubAgentPlaceholderActionExecutor`。  
**对外接口：** 实现 `HookActionExecutor`。  
**依赖：** `SubAgentService`、`HookExecutionScope`。  
**关键行为：** 根据 `HookAction.SubAgent.name` 和 `prompt` 后台启动子 Agent；成功返回 task id；失败写 Hook 日志并返回失败结果。

### Skill Fork 兼容模块

**职责：** 保持现有 fork Skill 可见行为。  
**对外接口：** 继续使用 `SkillForkRunner`。  
**依赖：** 现有 Skill 组件。  
**关键行为：** 本章不强制迁移 Skill fork；如果后续改用 `SubAgentRunnerFactory`，也必须保留只向主历史回流简短总结的语义。

## 模块交互

普通定义式前台调用：

```text
主 Agent -> AgentTool -> SubAgentService
  -> AgentDefinitionCatalog.find(subagent_type)
  -> SubAgentRunnerFactory.start()
  -> ForegroundSubAgentTracker.setCurrent()
  -> 等待 completion 或自动后台超时
  -> 完成: AgentTool 返回 fullResult
  -> 超时/ESC: BackgroundTaskManager.adoptRunning()，AgentTool 返回 async_launched
```

Fork 调用：

```text
主 Agent -> AgentTool(subagent_type 缺省)
  -> SubAgentService 识别 Fork
  -> BackgroundTaskManager.launch()
  -> SubAgentRunnerFactory 复制父原始历史
  -> AgentTool 立即返回 async_launched + taskId
```

后台完成通知：

```text
SubAgentRunHandle.completion()
  -> BackgroundTaskManager 更新状态
  -> BackgroundTaskListener.onTaskFinished(taskId)
  -> DefaultChatOrchestrator 读取任务 snapshot
  -> ConversationManager.addAssistantMessage(<task-notification>)
  -> TUI requestRender()
```

Hook 子 Agent：

```text
HookRuntime -> HookSubAgentExecutor
  -> SubAgentService.launchFromHook()
  -> BackgroundTaskManager.launch()
  -> HookActionResult.success(taskId)
```

ESC 手动切后台：

```text
LanternaLunaTui Esc
  -> ChatOrchestrator.backgroundCurrentSubAgentOrCancel()
  -> ForegroundSubAgentTracker.adoptCurrentToBackground()
  -> 有子 Agent: 状态切回 idle/async_launched
  -> 无子 Agent: 走现有 cancelCurrentRun()
```

## 文件组织

```text
src/main/java/com/lunacode/
├── subagent/
│   ├── AgentDefinition.java
│   ├── AgentDefinitionCatalog.java
│   ├── AgentDefinitionCatalogSnapshot.java
│   ├── AgentDefinitionCandidate.java
│   ├── AgentDefinitionDiagnostic.java
│   ├── AgentDefinitionParser.java
│   ├── AgentDefinitionSource.java
│   ├── AgentDefinitionSourceKind.java
│   ├── BuiltinAgentDefinitionSource.java
│   ├── DefaultAgentDefinitionCatalog.java
│   ├── FileSystemAgentDefinitionSource.java
│   ├── FrontmatterAgentDefinitionParser.java
│   ├── PluginAgentDefinitionSource.java
│   ├── SubAgentKind.java
│   ├── SubAgentLaunchRequest.java
│   ├── SubAgentParentContext.java
│   ├── SubAgentResult.java
│   ├── SubAgentRunHandle.java
│   ├── SubAgentRunnerFactory.java
│   ├── DefaultSubAgentRunnerFactory.java
│   ├── SubAgentService.java
│   ├── DefaultSubAgentService.java
│   ├── ToolPolicyResolver.java
│   ├── SubAgentModelResolver.java
│   ├── AgentExecutionContextHolder.java
│   └── DenyingPermissionConfirmationBroker.java
├── background/
│   ├── BackgroundTask.java
│   ├── BackgroundTaskManager.java
│   ├── BackgroundTaskSnapshot.java
│   ├── BackgroundTaskStatus.java
│   ├── BackgroundTaskListener.java
│   ├── DefaultBackgroundTaskManager.java
│   ├── ForegroundSubAgentTracker.java
│   ├── DefaultForegroundSubAgentTracker.java
│   ├── ProgressTracker.java
│   └── TaskNotificationFormatter.java
├── tool/
│   └── AgentTool.java
├── hook/
│   └── RealSubAgentHookActionExecutor.java
├── runtime/
│   └── AgentRunConfig.java
├── skill/
│   └── ToolAccessPolicy.java
├── config/
│   ├── AgentConfig.java
│   └── ConfigLoader.java
├── orchestrator/
│   ├── ChatOrchestrator.java
│   └── DefaultChatOrchestrator.java
└── tui/
    └── LanternaLunaTui.java

src/test/java/com/lunacode/
├── subagent/
├── background/
├── tool/
└── hook/
```

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 子 Agent 运行核心 | 复用 `DefaultAgentLoop` | 保持工具调用、上下文压缩、Hook、token 统计等行为一致 |
| Agent 工具上下文 | 通过 `AgentExecutionContextHolder` 传递父运行上下文 | 不改变所有 Tool 的接口，局部改造 `AgentToolRunner` 即可 |
| Fork 历史继承 | 复制父 `ConversationCompactionAccess.fullSnapshot()` 原始消息 | 尽量保证 prompt cache 命中和消息顺序一致 |
| 定义式历史 | 新建空白 `DefaultConversationManager` | 满足上下文隔离，避免父历史污染角色任务 |
| 前台转后台 | 先启动 handle，再等待完成或接管 | 支持自动超时和 ESC，不需要重启子 Agent |
| 后台通知写法 | 追加 assistant 文本消息 `<task-notification>` | 能被用户和下一轮主 Agent 看到，避免伪造二次 tool result |
| 权限确认 | 子 Agent 使用拒绝型确认 broker | 非交互后台不会卡死，也符合独立权限追踪 |
| 工具过滤 | 扩展 `ToolAccessPolicy` 支持 deny 集合 | 可同时覆盖声明层和执行层，黑名单优先 |
| 模型别名 | 在 `AgentConfig` 增加模型别名映射和自动后台阈值 | 与子 Agent 行为相关，配置集中在 agent 节点 |
| Hook 接入 | 替换 `SubAgentPlaceholderActionExecutor` | 保持 Hook 配置格式，升级为真实执行 |
| Skill fork | 第一版保持兼容，不强制迁移 | 降低风险，避免同时改变 Skill 用户可见行为 |

