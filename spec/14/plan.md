# Agent Team 与 Coordinator Mode Plan

## 架构概览

本章新增 `team` 协作层，负责长期团队、成员、任务清单、邮箱、成员上下文恢复和团队合并辅助。`TeamManager` 是核心入口：`/team` 命令、团队协作工具、`Agent` 工具的团队成员派生路径都通过它读写团队状态。团队状态按“仓库身份 + 团队名”持久化到用户目录，避免不同仓库同名团队互相覆盖。

成员运行复用现有子 Agent 和后台任务基础设施。第一版只实现 `SAME_PROCESS` 后端：成员在当前进程内以后台任务或协程方式运行完整 Agent loop；`TERMINAL_PANE` 后端只保留抽象、探测和明确失败结果。成员默认创建独立 worktree，并把 worktree 路径写入成员运行配置，文件工具和 Bash 继续沿用上一章的 explicit cwd 机制。

任务和消息是团队协作的两个共享通道。任务清单由 `TaskBoardService` 管理，支持依赖图、状态更新、负责人分配和原子认领。消息由 `MailboxService` 管理。`Agent` 工具的 `name` 参数非空时会向 `AgentNameRegistry` 注册 `name -> agentId`，团队队员创建时都带有成员名，因此天然完成注册。`SendMessage.to` 支持队友名称、Agent ID 和 `"*"` 广播；名称先通过 `AgentNameRegistry` 解析成 Agent ID，再用锁文件追加到目标邮箱。纯文本消息必须提供 5-10 个词的 `summary` 作为 UI 预览；结构化协议消息由消息类型和 payload 约束生命周期、审批和退出语义。

团队工具通过现有 `ToolAccessPolicy` 和新增 `TeamRuntimeContext` 控制可见性。普通主入口和普通子 Agent 没有团队上下文，因此看不到 `TaskCreate`、`TaskGet`、`TaskList`、`TaskUpdate`、`SendMessage`、`TeamCreate`、`TeamDelete` 等协作工具。Team Lead 和团队成员在运行配置里携带团队身份，工具声明和执行层都会检查该身份。

Coordinator Mode 是运行配置上的一层可选约束，不绑定团队是否存在。`CoordinatorModeResolver` 同时检查 `FeatureGate.COORDINATOR_MODE` 和环境变量 `LUNACODE_COORDINATOR_MODE`；只有双开时，主 Agent 的工具策略被替换为 coordinator 白名单，并注入 coordinator 系统提示词。该模式保留 Bash，用于 Git 合并和诊断；写文件和编辑文件工具不暴露给 Lead。

`Agent` 工具保留现有子 Agent 语义，并扩展团队成员派生语义：在 Team Lead 上下文中，调用方可以给出成员名、可选 `subagent_type`、可选任务 ID、后端和 `planModeRequired`。指定 `subagent_type` 时走定义式角色；省略时，如果 `FORK_SUBAGENT` 开启则 Fork Lead 上下文，否则使用默认 `general-purpose` agent。成员空闲后，Lead 再发消息或再分配任务时由 `TeamMemberService` 从磁盘恢复成员上下文继续执行。

合并阶段不新增模型可见的“自动解决冲突”工具。Lead 通过 Bash 执行 `git merge`、通过 ReadFile 检查冲突文件，并由自身推理决定顺序和解决策略。`/team merge` 和内部 `TeamMergeService` 只负责提供候选成员分支、依赖顺序、变更摘要和保护检查；真正合并仍由 Lead 的 Bash/ReadFile 流程完成。

## 核心数据结构

### TeamRepositoryIdentity

```java
public record TeamRepositoryIdentity(
        Path repoRoot,
        String canonicalPath,
        Optional<String> remoteUrl,
        String identityHash
) {}
```

`identityHash` 由规范化仓库路径和可用 remote 信息生成，用于用户目录下分桶。不同仓库即使团队名相同，也会落到不同目录。

### TeamRecord

```java
public record TeamRecord(
        String name,
        TeamRepositoryIdentity repository,
        String leadId,
        Path teamDir,
        Instant createdAt,
        Instant updatedAt,
        Optional<String> description
) {}
```

团队的顶层元数据。`leadId` 第一版使用当前 LunaCode 会话或固定 `lead` 身份，供任务和消息记录来源。

### TeamMemberRecord

```java
public record TeamMemberRecord(
        String name,
        String agentId,
        TeamMemberRole role,
        TeamMemberBackendType backendType,
        TeamMemberLaunchMode launchMode,
        Optional<String> agentType,
        boolean planModeRequired,
        TeamMemberStatus status,
        Path worktreePath,
        String worktreeBranch,
        Path contextFile,
        Optional<String> backgroundTaskId,
        Optional<String> currentTaskId,
        Optional<String> lastSummary,
        Instant createdAt,
        Instant lastActiveAt
) {}
```

成员长期状态。`contextFile` 保存成员对话快照和恢复信息；`status` 至少支持 `IDLE`、`RUNNING`、`WAITING_APPROVAL`、`FAILED`、`STOPPED`。

### TeamMemberBackendType

```java
public enum TeamMemberBackendType {
    SAME_PROCESS,
    TERMINAL_PANE
}
```

第一版只实现 `SAME_PROCESS`。请求 `TERMINAL_PANE` 时，后端选择器探测并返回 `UNAVAILABLE` 或 `NOT_IMPLEMENTED`，不得静默降级。

### TeamMemberLaunchMode

```java
public enum TeamMemberLaunchMode {
    DEFINED_AGENT,
    FORK,
    DEFAULT_GENERAL_PURPOSE
}
```

指定 `subagent_type` 时为 `DEFINED_AGENT`；省略 `subagent_type` 且 `FORK_SUBAGENT` 开启时为 `FORK`；省略且 flag 关闭时为 `DEFAULT_GENERAL_PURPOSE`。

### TeamRuntimeContext

```java
public record TeamRuntimeContext(
        String teamName,
        TeamActorRole actorRole,
        String actorName,
        Path teamDir,
        Optional<String> memberName
) {}
```

写入 `AgentRunConfig` 和 `ToolExecutionContext`。工具声明和执行都通过它判断当前调用者是 Lead、成员还是普通 Agent。

### TeamTaskRecord

```java
public record TeamTaskRecord(
        String id,
        String title,
        String description,
        TeamTaskStatus status,
        Optional<String> assignee,
        Set<String> blockedBy,
        Set<String> blocks,
        String createdBy,
        Optional<String> claimedBy,
        Optional<String> resultSummary,
        Instant createdAt,
        Instant updatedAt
) {}
```

`blockedBy` 和 `blocks` 表示系统级依赖图；文本依赖保留在 `description` 中，不参与强制判断。

### TeamTaskStatus

```java
public enum TeamTaskStatus {
    TODO,
    READY,
    CLAIMED,
    RUNNING,
    BLOCKED,
    WAITING_APPROVAL,
    DONE,
    FAILED,
    CANCELLED
}
```

`TaskList` 输出时动态计算 `BLOCKED` 视图：只要 `blockedBy` 中存在未完成任务，即使持久化状态是 `TODO`，也展示为被阻塞。

### TaskUpdatePatch

```java
public record TaskUpdatePatch(
        Optional<String> title,
        Optional<String> description,
        Optional<TeamTaskStatus> status,
        Optional<String> assignee,
        boolean claim,
        Set<String> addBlocks,
        Set<String> removeBlocks,
        Set<String> addBlockedBy,
        Set<String> removeBlockedBy,
        Optional<String> resultSummary
) {}
```

`claim=true` 表示由当前成员原子认领任务。认领成功会设置 `claimedBy`、`assignee` 和 `CLAIMED/RUNNING` 状态；失败返回当前持有人。

### MailboxMessage

```java
public record MailboxMessage(
        String id,
        String from,
        String to,
        MailboxMessageType type,
        String body,
        JsonNode payload,
        Instant timestamp,
        boolean read,
        String summary
) {}
```

落盘时自动补 `id`、`timestamp`、`read=false` 和摘要。`payload` 承载计划审批、任务通知、完成报告等结构化内容。

### MailboxMessageType

```java
public enum MailboxMessageType {
    TEXT,
    TASK_NOTIFICATION,
    PLAN_APPROVAL_REQUEST,
    PLAN_APPROVAL_RESPONSE,
    COMPLETION_REPORT,
    IDLE_NOTIFICATION,
    SHUTDOWN_REQUEST,
    SHUTDOWN_RESPONSE
}
```

`PLAN_APPROVAL_RESPONSE` 的 payload 包含 `decision`、`taskId`、`feedback` 和 `permissionMode`，其中 `decision` 只能是 `approve` 或 `reject`，且只有 Lead 可以发送。`SHUTDOWN_RESPONSE` 只能发给 Lead，payload 包含 `decision` 和 `reason`。

### SendMessageRequest

```java
public record SendMessageRequest(
        String to,
        MailboxMessageType type,
        String summary,
        String message,
        JsonNode payload
) {}
```

`to` 支持队友名称、Agent ID 和 `"*"`。`type=TEXT` 时 `summary` 必填且必须是 5-10 个词；协议消息根据类型校验 payload 和调用者身份。

### MailboxLockPolicy

```java
public record MailboxLockPolicy(
        Duration retryInterval,
        Duration maxWait,
        Duration staleAfter
) {}
```

第一版默认 `retryInterval=50ms`、`maxWait=2s`、`staleAfter=30s`。过期锁恢复时写入 warning 事件。

### FeatureGate

```java
public enum FeatureGate {
    FORK_SUBAGENT,
    COORDINATOR_MODE
}
```

由配置文件控制。`COORDINATOR_MODE` 还必须叠加环境变量判断；`FORK_SUBAGENT` 只影响省略 `subagent_type` 的团队成员创建路径。

### CoordinatorModeState

```java
public record CoordinatorModeState(
        boolean enabled,
        boolean featureEnabled,
        boolean envEnabled,
        Set<String> allowedTools
) {}
```

用于诊断 `/status` 和测试。`enabled` 只有在 feature 和 env 都为真时才为真。

## 核心接口

### TeamManager

```java
public interface TeamManager {
    TeamRecord createTeam(String name, Optional<String> description);
    TeamDeleteResult deleteTeam(String name, boolean force);
    List<TeamRecord> listTeams();
    TeamRecord useTeam(String name);
    Optional<TeamRecord> currentTeam();
    TeamSnapshot status(String name);
}
```

负责团队顶层生命周期和当前团队选择。删除团队时委托成员、任务、邮箱和 worktree 保护检查。

### TeamDirectoryResolver

```java
public interface TeamDirectoryResolver {
    Path userTeamsRoot();
    TeamRepositoryIdentity identify(Path repoRoot);
    Path teamDir(TeamRepositoryIdentity repository, String teamName);
}
```

把当前仓库映射到用户目录下的稳定路径，例如 `~/.lunacode/teams/<repoHash>/<teamName>/`。

### TeamStore

```java
public interface TeamStore {
    Optional<TeamRecord> loadTeam(Path teamDir);
    void saveTeam(TeamRecord team);
    List<TeamRecord> listTeams(TeamRepositoryIdentity repository);
    void markCurrentTeam(TeamRepositoryIdentity repository, Optional<String> teamName);
    Optional<String> currentTeamName(TeamRepositoryIdentity repository);
}
```

JSON 持久化使用临时文件加原子替换。当前团队选择是仓库级状态。

### TeamMemberService

```java
public interface TeamMemberService {
    TeamMemberRecord addMember(TeamRecord team, TeamMemberCreateRequest request);
    TeamMemberRecord spawnOrResume(TeamRecord team, TeamMemberDispatchRequest request);
    TeamMemberRecord markIdle(TeamRecord team, String memberName, Optional<String> summary);
    TeamMemberRecord stopMember(TeamRecord team, String memberName);
    List<TeamMemberRecord> listMembers(TeamRecord team);
    TeamMemberRecord getMember(TeamRecord team, String memberName);
}
```

成员创建时分配 worktree、后端和上下文文件。恢复成员时加载 `contextFile`，追加新消息或任务提示，再交给成员后端运行。

### TeamMemberBackend

```java
public interface TeamMemberBackend {
    TeamMemberBackendType type();
    BackendAvailability probe();
    TeamMemberRunHandle launch(TeamMemberRunRequest request);
    Optional<WakeupResult> wake(TeamMemberRecord member, MailboxMessage message);
}
```

`SameProcessTeamMemberBackend` 复用 `DefaultSubAgentRunnerFactory`/`BackgroundTaskManager`；`TerminalPaneTeamMemberBackend` 第一版 `probe()` 返回不可用或未实现，并在请求时明确失败。

### TeamTaskBoardService

```java
public interface TeamTaskBoardService {
    TeamTaskRecord create(TeamRecord team, TaskCreateRequest request, TeamRuntimeContext actor);
    TeamTaskRecord get(TeamRecord team, String taskId);
    List<TeamTaskView> list(TeamRecord team, TaskListFilter filter, TeamRuntimeContext actor);
    TeamTaskRecord update(TeamRecord team, String taskId, TaskUpdatePatch patch, TeamRuntimeContext actor);
    TeamTaskRecord claim(TeamRecord team, String taskId, String memberName);
}
```

所有写操作持有任务清单锁。`addBlocks` 和 `addBlockedBy` 双向维护依赖边；认领检查任务未分配、未完成且系统级依赖已满足。

### TeamTaskStore

```java
public interface TeamTaskStore {
    List<TeamTaskRecord> load(Path teamDir);
    void save(Path teamDir, List<TeamTaskRecord> tasks);
    <T> T withTaskLock(Path teamDir, Supplier<T> body);
}
```

任务清单存为 `tasks.json`。锁文件为 `tasks.lock`，同样支持过期锁恢复。

### MailboxService

```java
public interface MailboxService {
    SendMessageResult send(TeamRecord team, SendMessageRequest request, TeamRuntimeContext actor);
    SendMessageResult broadcast(TeamRecord team, BroadcastMessageRequest request, TeamRuntimeContext actor);
    List<MailboxMessage> readInbox(TeamRecord team, String memberName, boolean unreadOnly);
    void markRead(TeamRecord team, String memberName, Set<String> messageIds);
}
```

`send()` 根据 `to` 决定路由：`"*"` 展开为团队广播，Agent ID 直接查注册表，队友名称先解析成 Agent ID。`TEXT` 消息先校验 5-10 词 `summary`，再锁定目标邮箱追加消息。发送给已停止或空闲但有持久化上下文的成员时，会请求后端 `wake()`；同进程后端通过 `TeamMemberService.spawnOrResume()` 恢复。

### AgentNameRegistry

```java
public interface AgentNameRegistry {
    void register(TeamRecord team, String name, String agentId, TeamMemberRecord member);
    Optional<TeamMemberRecord> resolve(TeamRecord team, String nameOrAgentId);
    Optional<String> agentIdForName(TeamRecord team, String name);
    List<TeamMemberRecord> all(TeamRecord team);
    void unregister(TeamRecord team, String nameOrAgentId);
}
```

注册表落盘为 `registry.json`，维护“成员名称 -> Agent ID”和“Agent ID -> 元数据/邮箱”的唯一索引。`Agent` 工具在 `name` 参数非空时写入映射；团队队员创建时以成员名调用该注册流程。所有成员名和 Agent ID 路由都必须经过它。

### PlanApprovalService

```java
public interface PlanApprovalService {
    PlanApprovalRequest requestApproval(TeamRecord team, String memberName, String taskId, String planText);
    PlanApprovalDecision handleResponse(TeamRecord team, MailboxMessage response);
    boolean canModify(TeamRecord team, String memberName, String taskId);
}
```

成员 `planModeRequired=true` 时，第一次修改前进入 `WAITING_APPROVAL`。批准后记录任务授权和 Lead 权限模式；驳回后继续保持禁止修改状态。

### TeamToolPolicyResolver

```java
public interface TeamToolPolicyResolver {
    ToolAccessPolicy resolve(AgentRunConfig config, ToolAccessPolicy basePolicy);
}
```

没有 `TeamRuntimeContext` 时移除团队协作工具；Lead 允许团队管理、任务和消息工具；同进程团队队员除自身干活工具外，只额外允许 `TaskCreate`、`TaskGet`、`TaskList`、`TaskUpdate` 和 `SendMessage`；Coordinator Mode 再叠加 coordinator 白名单。

### CoordinatorModeResolver

```java
public interface CoordinatorModeResolver {
    CoordinatorModeState resolve(Environment environment, FeatureGateService featureGateService);
    ToolAccessPolicy applyPolicy(ToolAccessPolicy basePolicy, CoordinatorModeState state);
    Optional<String> systemPrompt(CoordinatorModeState state);
}
```

`applyPolicy` 允许的工具为 `Agent`、`SendMessage`、`TaskCreate`、`TaskGet`、`TaskList`、`TaskUpdate`、`TeamCreate`、`TeamDelete`、`ReadFile`、`Glob`、`Grep`、`Bash`。写文件和编辑文件不在白名单内。

### FeatureGateService

```java
public interface FeatureGateService {
    boolean isEnabled(FeatureGate gate);
}
```

从 LunaCode 配置读取显式 feature flag。配置缺失默认关闭。

### TeamMergeService

```java
public interface TeamMergeService {
    TeamMergePlan planMerge(TeamRecord team, TeamMergeRequest request);
    TeamMergeProtection checkProtection(TeamRecord team);
}
```

只生成合并候选、建议顺序、成员分支、任务结果和保护检查，不替 Lead 执行语义合并。Lead 仍通过 Bash/ReadFile 完成 `git merge`、冲突检查、`git add`、`git commit` 或 `git merge --abort`。

### TeamCommandHandler

```java
public interface TeamCommandHandler {
    CommandResult handle(String rawInput, boolean busy);
}
```

解析 `/team create|delete|list|use|status|member|task|message|merge`。命令结果只进入 UI，不进入模型历史。

## 模块设计

### team 核心模块

**职责：** 管理团队、成员、任务、邮箱和仓库身份。  
**对外接口：** `TeamManager`、`TeamMemberService`、`TeamTaskBoardService`、`MailboxService`。  
**依赖：** 文件系统、Jackson、`WorktreeManager`、`BackgroundTaskManager`、`SubAgentRunnerFactory`、`FeatureGateService`。  
**关键行为：** 创建团队时初始化目录；添加成员时创建 worktree 和邮箱；成员运行结束时保存上下文、更新任务、发通知；删除团队前执行保护检查。

### team store 模块

**职责：** 读写 `team.json`、`members/*.json`、`tasks.json`、`registry.json`、`mailboxes/*.jsonl`、`contexts/*.json`。  
**对外接口：** `TeamStore`、`TeamMemberStore`、`TeamTaskStore`、`MailboxStore`、`AgentNameRegistry`。  
**依赖：** 文件系统锁、原子写、JSON/JSONL。  
**关键行为：** JSON 对象用原子替换；邮箱用 append-only JSONL；任务和邮箱写入都持锁；锁过期恢复时产生 warning。

### member backend 模块

**职责：** 把团队成员运行映射到具体执行后端。  
**对外接口：** `TeamMemberBackend`、`TeamMemberBackendSelector`。  
**依赖：** `SubAgentService`/`SubAgentRunnerFactory`、`BackgroundTaskManager`。  
**关键行为：** `SAME_PROCESS` 启动或恢复 Agent loop；`TERMINAL_PANE` 只做探测和明确失败；后端选择不允许静默降级。

### Agent 工具团队扩展模块

**职责：** 让 Lead 通过 `Agent` 工具创建或恢复团队成员。  
**对外接口：** 扩展 `AgentToolRequest` 和 `AgentTool` 输入 schema。  
**依赖：** `TeamRuntimeContext`、`TeamMemberService`、`FeatureGateService`、现有 `SubAgentService`。  
**关键行为：** 没有团队字段时保持现有子 Agent 行为；`name` 非空时把 `name -> agentId` 写入 `AgentNameRegistry`；创建团队成员时 `name` 必须等于成员名；有团队成员字段但当前不是 Team Lead 时拒绝；指定 `subagent_type` 走定义式；省略时按 `FORK_SUBAGENT` 决定 Fork 或默认 `general-purpose`。

### 团队协作工具模块

**职责：** 注册模型可调用的团队协作工具。  
**对外接口：** `TaskCreateTool`、`TaskGetTool`、`TaskListTool`、`TaskUpdateTool`、`SendMessageTool`、`TeamCreateTool`、`TeamDeleteTool`。  
**依赖：** `TeamManager`、`TeamTaskBoardService`、`MailboxService`、`TeamRuntimeContext`。  
**关键行为：** 工具执行时二次检查团队身份；工具声明通过 `ToolAccessPolicy` 过滤；同进程团队队员只额外看到 `TaskCreate`、`TaskGet`、`TaskList`、`TaskUpdate` 和 `SendMessage`；成员不能调用团队创建/删除工具。

### Coordinator Mode 模块

**职责：** 在双重开关开启后收窄 Lead 工具并注入 coordinator 提示。  
**对外接口：** `CoordinatorModeResolver`、`FeatureGateService`、`CoordinatorPromptContributor`。  
**依赖：** 配置、环境变量、`ToolAccessPolicy`、`PromptContextBuilder`。  
**关键行为：** feature flag 和环境变量任一关闭都不生效；生效时只允许白名单工具；提示词强调 Lead 自己 synthesis，不能把理解任务委托出去。

### 任务依赖模块

**职责：** 维护系统级任务依赖图，并生成可读任务视图。  
**对外接口：** `TeamTaskBoardService.list()`、`TaskDependencyGraph`。  
**依赖：** `TeamTaskStore`。  
**关键行为：** `addBlocks` 和 `addBlockedBy` 写入时双向同步；禁止任务依赖自己；检测简单环路并拒绝写入；列表时标记 blocked/ready。

### 邮箱模块

**职责：** 提供名称/Agent ID 注册表解析、邮箱追加、读取、标记已读和广播。  
**对外接口：** `MailboxService`、`MailboxStore`、`AgentNameRegistry`。  
**依赖：** 文件锁、团队目录。  
**关键行为：** 纯文本消息必须校验 5-10 词摘要；每条消息追加到目标邮箱；`to="*"` 广播逐个目标写入；空闲或已停止但可恢复的目标会触发 wake；`shutdown_response` 只能发给 Lead，`plan_approval_response` 只能由 Lead 发送；消息失败不影响其他目标，最终结果汇总。

### 计划审批模块

**职责：** 约束 `planModeRequired` 成员在修改前先请求 Lead 审批。  
**对外接口：** `PlanApprovalService`、`TeamModificationGuard`。  
**依赖：** `MailboxService`、`TeamMemberStore`、`ToolPermissionGateway`。  
**关键行为：** 审批前成员运行在受限权限模式；修改类工具执行前检查授权；批准后写入 Lead 权限模式并恢复任务；驳回后保留反馈并继续等待。

### /team 命令模块

**职责：** 提供用户手动管理团队的入口。  
**对外接口：** `TeamCommandHandler`，并在 `BuiltinSlashCommands` 注册 `/team`。  
**依赖：** `TeamManager`、`TeamTaskBoardService`、`MailboxService`、`TeamMergeService`。  
**关键行为：** `/team use` 设置当前团队；`/team status` 汇总成员/任务/邮箱/worktree；`/team merge` 生成合并候选和保护提示。

### 合并辅助模块

**职责：** 给 Lead 提供成员分支和任务结果上下文，辅助 Bash 合并。  
**对外接口：** `TeamMergeService.planMerge()`。  
**依赖：** `TeamMemberStore`、`TeamTaskStore`、`WorktreeManager`、Git 状态读取。  
**关键行为：** 只读地收集成员分支、任务完成顺序、依赖图和未提交修改摘要；保护未完成、未提交、未读重要消息；不替 Lead 执行最终合并。

## 模块交互

创建并使用团队：

```text
用户输入 /team create refactor
  -> SlashCommandDispatcher
  -> TeamCommandHandler
  -> TeamDirectoryResolver.identify(repoRoot)
  -> TeamManager.createTeam()
  -> TeamStore.saveTeam()
  -> TeamStore.markCurrentTeam(refactor)
  -> UI 显示团队目录和当前团队
```

Lead 拆任务：

```text
Lead 调用 TaskCreate 创建 A/B/C/D
  -> TaskCreateTool
  -> TeamRuntimeContext(role=LEAD)
  -> TeamTaskBoardService.create()
  -> TeamTaskStore.withTaskLock()

Lead 调用 TaskUpdate(taskID=C, addBlockedBy=[A])
  -> TeamTaskBoardService.update()
  -> TaskDependencyGraph 校验依赖
  -> 写入 C.blockedBy=A 和 A.blocks=C
```

Lead 派生成员：

```text
Lead 调用 Agent(name="alice", team_member=alice, subagent_type=backend-engineer, task_id=A)
  -> AgentTool 检查 TeamRuntimeContext(role=LEAD)
  -> AgentTool 生成 agentId，并注册 AgentNameRegistry: alice -> agentId
  -> TeamMemberService.spawnOrResume()
  -> TeamMemberBackendSelector.select(SAME_PROCESS)
  -> WorktreeManager.create(AGENT/TEAM_MEMBER)
  -> SameProcessTeamMemberBackend.launch()
  -> SubAgentRunnerFactory.start()
  -> 成员 AgentRunConfig 带 TeamRuntimeContext(role=MEMBER, member=alice)
```

省略 `subagent_type` 的成员创建：

```text
Lead 调用 Agent(team_member=bob, task_id=B)
  -> FeatureGateService.isEnabled(FORK_SUBAGENT)
  -> true: launchMode=FORK，复制 Lead 历史
  -> false: launchMode=DEFAULT_GENERAL_PURPOSE，使用内置 general-purpose 定义式角色
```

成员认领任务：

```text
成员调用 TaskList
  -> 返回未阻塞、未分配任务
成员调用 TaskUpdate(taskID=X, claim=true)
  -> TeamTaskStore.withTaskLock()
  -> 检查 X 未分配且依赖完成
  -> 写入 assignee=成员名、claimedBy=成员名、status=CLAIMED
```

点对点消息：

```text
alice 调用 SendMessage(to="bob", type=TEXT, summary="接口签名变更通知", message="...")
  -> MailboxService.send()
  -> 校验 TEXT summary 为 5-10 个词
  -> AgentNameRegistry.resolveName(bob) -> agentId
  -> MailboxStore.withMailboxLock(agentId)
  -> 追加 mailboxes/<agentId>.jsonl
  -> 如果目标 status=IDLE/STOPPED 且 contextFile 有效，则 TeamMemberBackend.wake(target)

alice 调用 SendMessage(to="*")
  -> 展开为所有队友 Agent ID
  -> 对每个目标执行同一追加流程并汇总结果
```

计划审批：

```text
planModeRequired 成员准备修改
  -> TeamModificationGuard.canModify() 返回 false
  -> PlanApprovalService.requestApproval()
  -> SendMessage(to=lead, type=PLAN_APPROVAL_REQUEST)
  -> 成员状态 WAITING_APPROVAL

Lead 回复 SendMessage(to=alice, type=PLAN_APPROVAL_RESPONSE, approved=true)
  -> PlanApprovalService.handleResponse()
  -> 写入批准记录和 Lead permissionMode
  -> TeamMemberService.spawnOrResume(alice)
  -> 成员继续执行修改
```

Coordinator Mode 启动：

```text
应用构建主 AgentRunConfig
  -> CoordinatorModeResolver.resolve()
  -> feature(COORDINATOR_MODE)=true 且 env truthy
  -> AgentRunConfig.withCoordinatorMode(true)
  -> ToolAccessPolicy restricted(COORDINATOR_ALLOWED_TOOLS)
  -> PromptContextBuilder 注入 coordinator prompt
```

成员完成并恢复：

```text
成员 Agent loop 自然停下
  -> TeamMemberRunHandle completion
  -> TeamMemberContextStore.save(conversation snapshot)
  -> TaskUpdate status=DONE/resultSummary
  -> MailboxService.send(to=lead, type=COMPLETION_REPORT)
  -> TeamMemberService.markIdle()

Lead 后续 SendMessage(to=alice)
  -> MailboxService 追加消息
  -> SameProcessTeamMemberBackend.wake()
  -> TeamMemberContextStore.load()
  -> 追加新消息提示
  -> 继续运行 alice
```

合并辅助：

```text
Lead 或用户执行 /team merge refactor
  -> TeamMergeService.planMerge()
  -> 读取 DONE 任务、成员分支、worktree 变更摘要
  -> 输出建议顺序和 Git 命令提示

Lead 使用 Bash 执行 git merge worktree-...
  -> 如有冲突，ReadFile 读取冲突文件
  -> Lead 可解决则 git add/commit
  -> Lead 不确定则 git merge --abort 并报告用户
```

## 文件组织

```text
src/main/java/com/lunacode/
├── team/
│   ├── TeamRepositoryIdentity.java
│   ├── TeamDirectoryResolver.java
│   ├── DefaultTeamDirectoryResolver.java
│   ├── TeamRecord.java
│   ├── TeamSnapshot.java
│   ├── TeamManager.java
│   ├── DefaultTeamManager.java
│   ├── TeamStore.java
│   ├── JsonTeamStore.java
│   ├── TeamNameValidator.java
│   ├── TeamRuntimeContext.java
│   ├── TeamRuntimeContextHolder.java
│   ├── TeamActorRole.java
│   ├── TeamDeleteResult.java
│   ├── member/
│   │   ├── TeamMemberRecord.java
│   │   ├── TeamMemberStatus.java
│   │   ├── TeamMemberRole.java
│   │   ├── TeamMemberBackendType.java
│   │   ├── TeamMemberLaunchMode.java
│   │   ├── TeamMemberCreateRequest.java
│   │   ├── TeamMemberDispatchRequest.java
│   │   ├── TeamMemberService.java
│   │   ├── DefaultTeamMemberService.java
│   │   ├── TeamMemberStore.java
│   │   ├── JsonTeamMemberStore.java
│   │   ├── TeamMemberContextStore.java
│   │   ├── JsonTeamMemberContextStore.java
│   │   ├── TeamMemberBackend.java
│   │   ├── TeamMemberBackendSelector.java
│   │   ├── SameProcessTeamMemberBackend.java
│   │   └── TerminalPaneTeamMemberBackend.java
│   ├── task/
│   │   ├── TeamTaskRecord.java
│   │   ├── TeamTaskStatus.java
│   │   ├── TeamTaskView.java
│   │   ├── TaskCreateRequest.java
│   │   ├── TaskUpdatePatch.java
│   │   ├── TaskListFilter.java
│   │   ├── TeamTaskBoardService.java
│   │   ├── DefaultTeamTaskBoardService.java
│   │   ├── TeamTaskStore.java
│   │   ├── JsonTeamTaskStore.java
│   │   └── TaskDependencyGraph.java
│   ├── mailbox/
│   │   ├── MailboxMessage.java
│   │   ├── MailboxMessageType.java
│   │   ├── MailboxService.java
│   │   ├── DefaultMailboxService.java
│   │   ├── MailboxStore.java
│   │   ├── JsonlMailboxStore.java
│   │   ├── MailboxLockPolicy.java
│   │   ├── AgentNameRegistry.java
│   │   └── JsonAgentNameRegistry.java
│   ├── approval/
│   │   ├── PlanApprovalService.java
│   │   ├── DefaultPlanApprovalService.java
│   │   ├── PlanApprovalRequest.java
│   │   ├── PlanApprovalDecision.java
│   │   └── TeamModificationGuard.java
│   ├── merge/
│   │   ├── TeamMergeService.java
│   │   ├── DefaultTeamMergeService.java
│   │   ├── TeamMergePlan.java
│   │   ├── TeamMergeRequest.java
│   │   └── TeamMergeProtection.java
│   └── tool/
│       ├── TaskCreateTool.java
│       ├── TaskGetTool.java
│       ├── TaskListTool.java
│       ├── TaskUpdateTool.java
│       ├── SendMessageTool.java
│       ├── TeamCreateTool.java
│       ├── TeamDeleteTool.java
│       └── TeamToolPolicyResolver.java
├── coordinator/
│   ├── CoordinatorModeResolver.java
│   ├── DefaultCoordinatorModeResolver.java
│   ├── CoordinatorModeState.java
│   └── CoordinatorPromptContributor.java
├── config/
│   ├── FeatureGate.java
│   └── FeatureGateService.java
├── command/
│   ├── TeamCommandHandler.java
│   └── DefaultTeamCommandHandler.java
├── runtime/
│   └── AgentRunConfig.java
├── tool/
│   ├── AgentTool.java
│   ├── ToolExecutionContext.java
│   └── DefaultToolRegistry.java
└── subagent/
    ├── AgentToolRequest.java
    └── DefaultSubAgentService.java

src/test/java/com/lunacode/
├── team/
├── coordinator/
├── command/
├── tool/
└── integration/
```

持久化布局：

```text
~/.lunacode/
└── teams/
    └── <repoHash>/
        ├── current_team.json
        └── refactor/
            ├── team.json
            ├── registry.json
            ├── tasks.json
            ├── tasks.lock
            ├── members/
            │   ├── alice.json
            │   └── bob.json
            ├── mailboxes/
            │   ├── alice.jsonl
            │   ├── alice.lock
            │   ├── bob.jsonl
            │   └── bob.lock
            └── contexts/
                ├── alice.json
                └── bob.json
```

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 团队持久化位置 | 用户目录下按 repoHash/teamName 分桶 | 满足长期团队和跨仓库同名隔离 |
| 团队用户入口 | `/team` 命令族 | 与 `/worktree`、`/session` 的本地命令风格一致 |
| 模型协作工具 | `TaskCreate/Get/List/Update`、`SendMessage`、`TeamCreate/Delete` | 保持工具数量少，覆盖 Lead 和成员核心协作 |
| 队员协作工具 | 同进程队员固定额外获得 `TaskCreate`、`TaskGet`、`TaskList`、`TaskUpdate`、`SendMessage` | 队员能协作但不能管理团队生命周期 |
| 成员派生入口 | 扩展现有 `Agent` 工具 | 复用 SubAgent 语义和用户已理解的派生入口 |
| 省略 agentType | `FORK_SUBAGENT` 开时 Fork，关时默认 general-purpose | 符合用户显式 feature flag 边界 |
| 成员默认隔离 | 每个成员一个 worktree | 复用 spec/13，避免并行文件覆盖 |
| 第一版运行后端 | `SAME_PROCESS` 实现，`TERMINAL_PANE` 明确失败 | 先复用现有后台任务，避免伪隔离和静默降级 |
| 任务依赖 | 结构化依赖 + 描述文本共存 | 核心依赖可计算，补充意图保留 LLM 协作弹性 |
| 任务认领 | 通过 `TaskUpdate claim=true` 原子完成 | 不新增工具，同时解决并发抢占 |
| 邮箱格式 | append-only JSONL + lock 文件 | 易追加、易恢复、并发安全 |
| 消息寻址 | `AgentNameRegistry` + 邮箱文件，`"*"` 表示广播 | `Agent.name` 注册可读名称，Agent ID 便于接续通知，广播覆盖团队通知 |
| 计划审批 | 修改前 guard + mailbox 结构化回复 | 让审批成为可持久化协议，而不是临时提示 |
| 成员恢复 | 保存成员对话快照，空闲后按消息唤醒 | 支持长期成员，不需要每次重新 spawn |
| Coordinator Mode 开关 | feature flag + 环境变量双锁 | 开发者控制能力开放，用户显式 opt-in |
| Coordinator 工具收窄 | `ToolAccessPolicy` 白名单 + 执行层检查 | 既不展示写工具，也防止执行绕过 |
| 合并方式 | Lead 用 Bash/ReadFile 推理执行，服务只给合并计划 | 保留 Lead 判断力，避免过度自动化冲突处理 |
| 团队删除 | 默认保护运行中、未合并、未提交、未读重要消息 | 宁可多留状态，也不丢团队成果 |
