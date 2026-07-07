# Worktree 隔离 Plan

## 架构概览

本章新增 `worktree` 基础设施包，集中管理 Git Worktree 生命周期、名称校验、会话持久化、环境初始化、删除保护和后台清理。`WorktreeManager` 是核心编排入口：手动 `/worktree` 命令、定义式子 Agent 自动隔离和后台清理都通过它创建、恢复、进入、退出和删除 worktree。

现有工具执行链增加“有效工作目录”概念。仓库根目录仍然是 LunaCode 的进程级根目录，worktree 会话只改变当前 Agent 运行配置中的 `workDir`，不改变进程 cwd。主 Agent 的 `AgentRunConfig.workDir` 由 `WorktreeSessionManager.effectiveWorkDir()` 决定；子 Agent 如果声明 `isolation: worktree`，会在启动前获得自己的 worktree 路径并把该路径写入子 Agent 的运行配置。工具执行时通过每个 Agent 的运行配置设置本次调用的执行目录，文件工具、搜索工具、Bash、权限检查和 Hook 命令都从这个目录解析路径。

`/worktree` 命令族复用现有 Slash Command 基础设施。`BuiltinSlashCommands` 只注册入口和帮助信息，具体解析和业务由 `WorktreeCommandHandler` 完成。命令结果通过 `CommandRuntime` 显示给用户，不进入模型历史；进入 worktree 后，后续普通用户消息触发的 Agent 运行会在 worktree 下执行。

子 Agent 集成发生在定义式子 Agent 启动链路。角色 frontmatter 新增 `isolation` 字段，只有值为 `worktree` 时才启用隔离。`SubAgentService` 在创建 `SubAgentLaunchRequest` 时申请自动 worktree，`DefaultSubAgentRunnerFactory` 使用 worktree 路径构建子 Agent 运行配置，并在子任务结束后调用自动清理。Fork 式子 Agent 和 Hook 子 Agent 保持现有行为，不默认创建 worktree。

环境初始化由 `WorktreeEnvironmentInitializer` 在新建 worktree 后执行，快速恢复路径跳过该步骤。初始化包括复制本地配置、配置 hooks、按 `settings.worktree.symlinkDirectories` 创建依赖软链，以及按 `.worktreeinclude` 复制被忽略但运行需要的文件。所有初始化步骤都返回 warning，不阻断创建成功。

## 核心数据结构

### WorktreeKind

```java
public enum WorktreeKind {
    MANUAL,
    AGENT,
    WORKFLOW
}
```

用于区分手动、子 Agent 和工作流来源。后台自动清理只处理 `AGENT` 和 `WORKFLOW`，且名称必须匹配对应自动模式。

### WorktreeRecord

```java
public record WorktreeRecord(
        String name,
        WorktreeKind kind,
        Path repoRoot,
        Path path,
        String branchName,
        String baseHeadCommit,
        Optional<String> originalBranch,
        String headCommit,
        Instant createdAt,
        Instant lastUsedAt,
        List<String> warnings
) {}
```

`baseHeadCommit` 是创建或恢复时用于删除保护的基线。`headCommit` 是当前解析到的 HEAD。`warnings` 记录 dirty 主仓库、配置复制失败、软链失败、`.worktreeinclude` 失败等非阻断问题。

### WorktreeSession

```java
public record WorktreeSession(
        Path repoRoot,
        Path originalCwd,
        Path worktreePath,
        String worktreeName,
        String worktreeBranch,
        Optional<String> originalBranch,
        String originalHeadCommit,
        String sessionId,
        Instant enteredAt
) {}
```

手动 `/worktree enter` 写入 `.lunacode/worktree_session.json`。该对象不表示进程 cwd 已切换，只表示主 Agent 后续运行使用 `worktreePath` 作为有效工作目录。

### WorktreeCreateRequest

```java
public record WorktreeCreateRequest(
        String name,
        WorktreeKind kind,
        boolean allowFastRestore
) {}
```

手动创建传 `MANUAL`；定义式子 Agent 自动创建传 `AGENT`；工作流预留传 `WORKFLOW`。第一版不包含 base 参数，统一使用 repo 当前 `HEAD`。

### WorktreeCreateResult

```java
public record WorktreeCreateResult(
        WorktreeRecord record,
        boolean restored,
        List<String> warnings
) {}
```

`restored=true` 表示命中快速恢复，没有调用 `git worktree add`，也没有执行创建后初始化。

### WorktreeChanges

```java
public record WorktreeChanges(
        int uncommitted,
        int newCommits,
        String statusSummary
) {
    public boolean hasChanges() {
        return uncommitted > 0 || newCommits > 0;
    }
}
```

删除保护和自动清理都使用这个结构。`newCommits` 只比较 `baseHeadCommit..HEAD`，不判断 upstream 和 push 状态。

### WorktreeRemoveRequest

```java
public record WorktreeRemoveRequest(
        String name,
        boolean discardChanges,
        boolean automatic
) {}
```

`automatic=true` 仅用于子 Agent 完成清理和后台过期清理。自动清理不能删除手动 worktree，也不能绕过变更保护。

### WorktreeSettings

```java
public record WorktreeSettings(
        List<String> localConfigFiles,
        List<String> symlinkDirectories
) {}
```

从仓库根目录 `settings.local.json` 读取 `settings.worktree.symlinkDirectories`。`localConfigFiles` 第一版默认包含 `settings.local.json`。配置缺失时使用空软链列表。

### ToolExecutionScope

```java
public record ToolExecutionScope(
        Path workDir,
        Path repoRoot,
        Optional<WorktreeSession> worktreeSession
) {}
```

每次 Agent 工具调用前写入线程本地上下文，工具执行器、路径解析器、Bash 和 Hook 命令都从这里获取本次调用的有效工作目录。没有 scope 时回退到进程启动时的仓库根目录。

### WorktreeCleanupPolicy

```java
public record WorktreeCleanupPolicy(
        Duration ttl,
        Pattern agentNamePattern,
        Pattern workflowNamePattern
) {}
```

第一版默认 ttl 为 24 小时，后台清理只处理超过 ttl 且通过三层过滤的自动 worktree。

## 核心接口

### WorktreeManager

```java
public interface WorktreeManager {
    WorktreeCreateResult create(WorktreeCreateRequest request);
    WorktreeSession enter(String name, String sessionId);
    void exit();
    WorktreeRemoveResult remove(WorktreeRemoveRequest request);
    List<WorktreeSnapshot> list();
    Optional<WorktreeRecord> find(String name);
    Optional<WorktreeSession> currentSession();
    Path effectiveWorkDir();
    WorktreeCleanupResult cleanupExpired();
}
```

负责聚合名称校验、Git 操作、状态持久化、会话持久化、环境初始化和清理策略。`effectiveWorkDir()` 是主 Agent 构建 `AgentRunConfig` 的入口。

### WorktreeNameValidator

```java
public interface WorktreeNameValidator {
    ValidWorktreeName validate(String rawName, WorktreeKind kind);
}
```

校验字符集、长度、段规则、Windows 保留名和自动命名模式。`ValidWorktreeName` 同时提供目录相对路径和分支 slug。

### GitWorktreeClient

```java
public interface GitWorktreeClient {
    GitRepositoryState inspectRepository(Path repoRoot);
    Optional<FastRestoredHead> tryReadHead(Path worktreePath);
    void addWorktree(Path repoRoot, Path worktreePath, String branchName, String baseCommit);
    WorktreeChanges countChanges(Path worktreePath, String baseHeadCommit);
    void removeWorktree(Path repoRoot, Path worktreePath);
    void deleteBranch(Path repoRoot, String branchName);
    void configureHooksPath(Path worktreePath, Path hooksPath);
    List<Path> ignoredFiles(Path repoRoot);
}
```

所有 Git 子进程统一在实现里设置 `GIT_TERMINAL_PROMPT=0`、`GIT_ASKPASS=`，并关闭 stdin。`tryReadHead` 是纯文件系统读取，不调用 Git。

### WorktreeStateStore

```java
public interface WorktreeStateStore {
    WorktreeState load();
    void save(WorktreeState state);
}
```

持久化 active worktree 记录到 `.lunacode/worktree_state.json`。写入使用临时文件加原子替换，避免进程退出留下半截 JSON。

### WorktreeSessionStore

```java
public interface WorktreeSessionStore {
    Optional<WorktreeSession> load();
    void save(Optional<WorktreeSession> session);
}
```

读写 `.lunacode/worktree_session.json`。启动时由 `WorktreeManager` 校验目录、`.git` 指针和 HEAD，有效才恢复。

### WorktreeEnvironmentInitializer

```java
public interface WorktreeEnvironmentInitializer {
    List<String> initialize(Path repoRoot, Path worktreePath);
}
```

新建后执行，快速恢复不执行。返回 warning 列表，不抛出非致命异常。

### WorktreeIncludeMatcher

```java
public interface WorktreeIncludeMatcher {
    List<Path> selectIncludedIgnoredFiles(Path repoRoot, List<Path> ignoredFiles);
}
```

读取 `.worktreeinclude` 并按 gitignore 风格模式过滤 `git ls-files --others --ignored --exclude-standard --directory` 的输出。支持空行、注释、`!` 否定、`/` 根匹配、目录匹配、`*`、`?` 和 `**`。

### WorktreeCommandHandler

```java
public interface WorktreeCommandHandler {
    CommandResult handle(String rawInput, boolean busy);
}
```

解析 `/worktree create|list|enter|exit|remove`。运行中有 pending 用户回答或权限确认时拒绝状态切换类命令；`list` 可以在空闲时展示当前记录。

### ToolExecutionScopeHolder

```java
public final class ToolExecutionScopeHolder {
    public static <T> T withScope(ToolExecutionScope scope, Supplier<T> body);
    public static Optional<ToolExecutionScope> current();
}
```

`AgentToolRunner` 在执行单个工具和相关 Hook 时写入 scope。并发工具批次每个 future 都写入相同 scope，避免后台子 Agent 与主 Agent 互相污染。

## 模块设计

### WorktreeManager 模块

**职责：** 对外提供 worktree 生命周期 API，维护 active 状态和当前 session。  
**对外接口：** `create()`、`enter()`、`exit()`、`remove()`、`list()`、`effectiveWorkDir()`、`cleanupExpired()`。  
**依赖：** `WorktreeNameValidator`、`GitWorktreeClient`、`WorktreeStateStore`、`WorktreeSessionStore`、`WorktreeEnvironmentInitializer`、时钟。  
**关键行为：** 创建时先校验名称，再检查目标目录快速恢复；恢复失败才执行 Git 创建；新建后初始化环境；删除前统一检查变更；启动时恢复 session。

### GitWorktreeClient 模块

**职责：** 封装 Git 命令和快速 HEAD 读取。  
**对外接口：** `inspectRepository()`、`tryReadHead()`、`addWorktree()`、`countChanges()`、`removeWorktree()`、`deleteBranch()`、`ignoredFiles()`。  
**依赖：** `ProcessBuilder`、文件系统。  
**关键行为：** Git 命令统一无交互；`addWorktree` 使用 `git worktree add -B <branch> <path> <baseHead>`；删除时先 remove worktree，再短暂等待 lock 释放，再删分支；快速恢复只读取 `.git`、`HEAD`、`refs` 和 `packed-refs`。

### WorktreeNameValidator 模块

**职责：** 把用户或系统生成的名称转换为安全目录路径和分支名。  
**对外接口：** `validate()`。  
**依赖：** Windows 保留名列表。  
**关键行为：** 手动名称允许 `/` 嵌套；分支名把 `/` 替换为 `+` 并加 `worktree-`；自动 Agent 名称生成 `agent-a` 加 7 位 hex；工作流名称使用 `wf_` 加固定格式 hex；所有输出都必须在 `.lunacode/worktrees/` 内。

### WorktreeStateStore 模块

**职责：** 保存和恢复 active worktree 记录。  
**对外接口：** `load()`、`save()`。  
**依赖：** Jackson JSON、文件系统。  
**关键行为：** 状态文件放在 `.lunacode/worktree_state.json`；读失败时返回空状态并给出 warning；写入使用临时文件。

### WorktreeSessionStore 模块

**职责：** 保存和恢复当前 worktree 会话。  
**对外接口：** `load()`、`save()`。  
**依赖：** Jackson JSON、文件系统。  
**关键行为：** session 文件放在 `.lunacode/worktree_session.json`；启动恢复时只接受 `GitWorktreeClient.tryReadHead()` 成功的 session；无效 session 会被清除。

### WorktreeEnvironmentInitializer 模块

**职责：** 新建 worktree 后补齐运行环境。  
**对外接口：** `initialize(repoRoot, worktreePath)`。  
**依赖：** `WorktreeSettingsLoader`、`GitWorktreeClient`、`WorktreeIncludeMatcher`。  
**关键行为：** 复制 `settings.local.json`；优先设置 `.husky/` 为 hooksPath，没有则回退 `.git/hooks`；为 `settings.worktree.symlinkDirectories` 声明的目录创建软链；按 `.worktreeinclude` 复制被忽略文件。所有失败进入 warning。

### WorktreeSettingsLoader 模块

**职责：** 从本地配置读取 worktree 初始化规则。  
**对外接口：** `load(repoRoot)`。  
**依赖：** Jackson JSON。  
**关键行为：** 读取 `settings.local.json` 中 `settings.worktree.symlinkDirectories`；字段缺失时返回空列表；字段类型非法时返回 warning 并忽略该配置。

### WorktreeIncludeMatcher 模块

**职责：** 按 `.worktreeinclude` 过滤被 Git 忽略的文件。  
**对外接口：** `selectIncludedIgnoredFiles()`。  
**依赖：** 文件系统。  
**关键行为：** 使用 gitignore 风格匹配；匹配结果只复制文件，目录记录会递归展开到文件；错误不阻断初始化。

### WorktreeCommandHandler 模块

**职责：** 执行 `/worktree` 命令族。  
**对外接口：** `handle(rawInput, busy)`。  
**依赖：** `WorktreeManager`。  
**关键行为：** `create <name>` 创建手动 worktree；`list` 展示 name、kind、path、branch、head、current、changes；`enter <name>` 持久化 session；`exit` 清 session；`remove <name> [--discardChanges]` 删除并执行保护。

### CommandRuntime 集成模块

**职责：** 把 `/worktree` 接入现有 slash command 运行时。  
**对外接口：** 在 `CommandRuntime` 增加 `runWorktreeCommand(String rawInput)`。  
**依赖：** `WorktreeCommandHandler`。  
**关键行为：** `BuiltinSlashCommands` 注册 `/worktree`；`DefaultChatOrchestrator` 调用 handler 并把结果映射到 `showInfo`、`showWarning` 或 `showError`。

### Effective WorkDir 模块

**职责：** 让主 Agent 在当前 worktree 会话下运行，而不修改进程 cwd。  
**对外接口：** `WorktreeManager.effectiveWorkDir()`。  
**依赖：** `DefaultChatOrchestrator`。  
**关键行为：** `DefaultChatOrchestrator.runConfig()` 使用 effective workDir；没有 session 时返回 repoRoot；`lastPlanFile` 按 effective workDir 解析；`EnvironmentContext` 渲染当前 workdir，并在存在 worktree session 时额外渲染 worktree 名称和分支。

### Tool Execution Scope 模块

**职责：** 让工具、Hook 和权限检查使用每个 Agent 自己的 workDir。  
**对外接口：** `ToolExecutionScopeHolder.withScope()`。  
**依赖：** `AgentToolRunner`、`DefaultToolExecutor`、`WorkspacePathResolver`、`ShellCommandRunner`。  
**关键行为：** `AgentToolRunner` 在每次工具执行前用 `config.workDir()` 设置 scope；`DefaultToolExecutor` 基于 scope 派生 `ToolExecutionContext`；`WorkspacePathResolver` 从 context 或 scope 获取当前根目录；Bash 和 Hook 命令的 `ProcessBuilder.directory` 使用 scope workDir。

### WorkspacePathResolver 模块

**职责：** 按当前有效工作目录解析文件路径。  
**对外接口：** 保留旧构造器，新增基于 context/scope 的解析能力。  
**依赖：** `PathSandbox`。  
**关键行为：** 读写工具、Glob 和 Grep 都用有效 workDir 解析和相对化；缓存或 metadata 输出使用绝对路径或相对当前 effective workDir 的路径。

### AgentDefinition 隔离字段模块

**职责：** 解析角色 frontmatter 中的 `isolation`。  
**对外接口：** `AgentDefinition.isolation()`。  
**依赖：** `FrontmatterAgentDefinitionParser`。  
**关键行为：** 仅允许空值或 `worktree`；非法值跳过该角色并输出诊断；默认空值表示不隔离。

### SubAgent Worktree 模块

**职责：** 为声明 `isolation: worktree` 的定义式子 Agent 自动创建和清理 worktree。  
**对外接口：** 扩展 `SubAgentLaunchRequest`，携带可选 `WorktreeRecord`。  
**依赖：** `WorktreeManager`、`DefaultSubAgentService`、`DefaultSubAgentRunnerFactory`。  
**关键行为：** 定义式子 Agent 启动前创建 `AGENT` worktree；子 Agent 的 `AgentRunConfig.workDir` 使用 worktree 路径；子 Agent 系统提示追加 worktree 路径说明；完成后无变更自动删除，有变更保留并在结果中返回路径和分支。

### Worktree Cleanup 模块

**职责：** 后台定期清理过期自动 worktree。  
**对外接口：** `cleanupExpired()`。  
**依赖：** `ScheduledExecutorService`、`WorktreeManager`。  
**关键行为：** 启动后定期扫描状态记录；必须同时满足管理目录内、状态记录存在、名称匹配自动模式、分支 `worktree-` 前缀、超过 ttl、无未提交修改、无新增 commit 才删除。

## 模块交互

手动创建：

```text
用户输入 /worktree create my-feature
  -> SlashCommandDispatcher
  -> WorktreeCommandHandler
  -> WorktreeManager.create(MANUAL)
  -> WorktreeNameValidator.validate()
  -> GitWorktreeClient.inspectRepository()
  -> GitWorktreeClient.tryReadHead()
  -> 未恢复: GitWorktreeClient.addWorktree()
  -> WorktreeEnvironmentInitializer.initialize()
  -> WorktreeStateStore.save()
  -> CommandRuntime.showInfo()
```

手动进入并执行工具：

```text
/worktree enter my-feature
  -> WorktreeManager.enter()
  -> WorktreeSessionStore.save()

下一轮用户消息
  -> DefaultChatOrchestrator.runConfig()
  -> WorktreeManager.effectiveWorkDir() = worktreePath
  -> AgentToolRunner.with ToolExecutionScope(worktreePath)
  -> DefaultToolExecutor / WorkspacePathResolver / ShellCommandRunner
  -> 文件和命令都在 worktreePath 下执行
```

快速恢复：

```text
WorktreeManager.create()
  -> 目标目录存在
  -> GitWorktreeClient.tryReadHead()
  -> 读取 .git 指针 -> gitdir -> HEAD -> refs/heads 或 packed-refs
  -> 成功: 保存 WorktreeRecord(restored=true)，跳过 Git 子进程和环境初始化
  -> 失败: 执行 git worktree add -B
```

删除保护：

```text
/worktree remove name
  -> WorktreeManager.remove(discardChanges=false)
  -> GitWorktreeClient.countChanges(path, baseHeadCommit)
  -> 有 uncommitted 或 newCommits: 返回拒绝
  -> 无变更: git worktree remove --force path
  -> 等待 lock 释放
  -> git branch -D worktree-name
  -> WorktreeStateStore.save()
```

子 Agent 自动隔离：

```text
主 Agent 调用 Agent 工具，subagent_type 指向定义式角色
  -> SubAgentService 查到 AgentDefinition.isolation=WORKTREE
  -> WorktreeManager.create(AGENT, agent-aXXXXXXX)
  -> SubAgentLaunchRequest 携带 WorktreeRecord
  -> DefaultSubAgentRunnerFactory childConfig.workDir = worktree.path
  -> 子 Agent 跑到底
  -> completion 后 WorktreeManager.remove(automatic=true)
      -> 无变更: 删除
      -> 有变更: 保留并把 path/branch 写入 SubAgentResult
```

启动恢复：

```text
LunaCodeApplication 启动
  -> 创建 WorktreeManager
  -> WorktreeSessionStore.load()
  -> GitWorktreeClient.tryReadHead(session.worktreePath)
  -> 有效: currentSession = session
  -> 无效: WorktreeSessionStore.save(empty)，打印 warning
```

后台清理：

```text
ScheduledWorktreeCleaner tick
  -> WorktreeManager.cleanupExpired()
  -> 遍历 WorktreeStateStore.active
  -> 三层过滤：管理目录 + 状态记录 + 自动命名/分支前缀
  -> countChanges()
  -> 无变更才 remove
```

## 文件组织

```text
src/main/java/com/lunacode/
├── worktree/
│   ├── GitRepositoryState.java
│   ├── GitWorktreeClient.java
│   ├── ProcessGitWorktreeClient.java
│   ├── FastRestoredHead.java
│   ├── ValidWorktreeName.java
│   ├── WorktreeNameValidator.java
│   ├── DefaultWorktreeNameValidator.java
│   ├── WorktreeKind.java
│   ├── WorktreeRecord.java
│   ├── WorktreeSnapshot.java
│   ├── WorktreeState.java
│   ├── WorktreeStateStore.java
│   ├── JsonWorktreeStateStore.java
│   ├── WorktreeSession.java
│   ├── WorktreeSessionStore.java
│   ├── JsonWorktreeSessionStore.java
│   ├── WorktreeCreateRequest.java
│   ├── WorktreeCreateResult.java
│   ├── WorktreeRemoveRequest.java
│   ├── WorktreeRemoveResult.java
│   ├── WorktreeChanges.java
│   ├── WorktreeSettings.java
│   ├── WorktreeSettingsLoader.java
│   ├── WorktreeIncludeMatcher.java
│   ├── GitignoreWorktreeIncludeMatcher.java
│   ├── WorktreeEnvironmentInitializer.java
│   ├── DefaultWorktreeEnvironmentInitializer.java
│   ├── WorktreeCleanupPolicy.java
│   ├── WorktreeCleanupResult.java
│   ├── WorktreeManager.java
│   ├── DefaultWorktreeManager.java
│   ├── WorktreeCommandHandler.java
│   └── DefaultWorktreeCommandHandler.java
├── tool/
│   ├── ToolExecutionScope.java
│   ├── ToolExecutionScopeHolder.java
│   ├── ToolExecutionContext.java
│   ├── DefaultToolExecutor.java
│   └── WorkspacePathResolver.java
├── subagent/
│   ├── AgentDefinition.java
│   ├── FrontmatterAgentDefinitionParser.java
│   ├── SubAgentLaunchRequest.java
│   ├── DefaultSubAgentService.java
│   └── DefaultSubAgentRunnerFactory.java
├── command/
│   ├── BuiltinSlashCommands.java
│   └── CommandRuntime.java
├── orchestrator/
│   └── DefaultChatOrchestrator.java
├── prompt/
│   ├── EnvironmentContext.java
│   └── EnvironmentContextCollector.java
└── app/
    └── LunaCodeApplication.java

src/test/java/com/lunacode/
├── worktree/
├── tool/
├── subagent/
├── command/
└── orchestrator/
```

持久化文件：

```text
.lunacode/
├── worktree_state.json
├── worktree_session.json
└── worktrees/
    ├── my-feature/
    ├── team-refactor/alice/
    └── agent-a3f2b1c/
```

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| worktree 管理目录 | 固定 `.lunacode/worktrees/` | 满足仓库内不追踪位置，减少配置和路径安全面 |
| 分支命名 | `worktree-` 加 slug，`/` 替换为 `+` | `git branch` 中一眼可识别 LunaCode 分支，同时保留嵌套语义 |
| base ref | 当前 `HEAD` SHA | 子 Agent 看到派活瞬间的稳定快照，不受分支移动影响 |
| 主仓库 dirty | 允许创建并警告 | 不扩散半成品修改，同时不阻塞并行分析 |
| 快速恢复 | 纯文件系统读取 `.git` 和 HEAD/ref | 避免反复进入同一 worktree 时调用慢速 Git 创建流程 |
| Git 子进程 | 统一禁用 terminal prompt、askpass 和 stdin | 防止凭证或交互输入导致 LunaCode 挂起 |
| 创建命令 | `git worktree add -B` | 可覆盖残留同名分支，降低上次清理不完整带来的失败率 |
| 环境初始化 | 新建执行，快速恢复跳过 | 快速恢复目标是低延迟复用，避免重复软链和复制 |
| 初始化失败处理 | best-effort warning | 不让环境补齐问题阻断 worktree 创建，但保留诊断信息 |
| 工具 cwd | AgentRunConfig + ToolExecutionScope | 支持主 Agent、前台子 Agent、后台子 Agent 并发隔离，不依赖全局 cwd |
| 手动 enter | 只持久化 session，不 chdir | 符合显式 cwd 架构，避免全局状态污染 |
| 缓存策略 | 继续使用绝对路径 key，不清缓存 | 主仓库和 worktree 路径不同，天然隔离 |
| 删除保护 | 基线后新 commit 或未提交修改都拒绝 | 最保守地保护子 Agent 和用户成果 |
| 自动清理 | 只清理自动命名且无成果的过期 worktree | 避免误删用户手动创建目录 |
| 子 Agent 隔离触发 | 仅 `isolation: worktree` | 保持现有 Fork、Hook 和普通定义式子 Agent 兼容 |
| Node.js 依赖软链 | 配置驱动并记录风险提示 | 不同项目差异大，软链不是万能策略 |
