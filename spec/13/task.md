# Worktree 隔离 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeKind.java` | worktree 来源类型 |
| 新建 | `src/main/java/com/lunacode/worktree/ValidWorktreeName.java` | 安全名称解析结果 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeNameValidator.java` | 名称校验接口 |
| 新建 | `src/main/java/com/lunacode/worktree/DefaultWorktreeNameValidator.java` | 名称、目录 slug、分支名校验 |
| 新建 | `src/main/java/com/lunacode/worktree/GitRepositoryState.java` | repo 当前 HEAD、分支和 dirty 状态 |
| 新建 | `src/main/java/com/lunacode/worktree/FastRestoredHead.java` | 快速恢复 HEAD 读取结果 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeChanges.java` | 未提交修改和新增 commit 统计 |
| 新建 | `src/main/java/com/lunacode/worktree/GitWorktreeClient.java` | Git worktree 操作接口 |
| 新建 | `src/main/java/com/lunacode/worktree/ProcessGitWorktreeClient.java` | Git 子进程和快速恢复实现 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeRecord.java` | active worktree 记录 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeSnapshot.java` | `/worktree list` 展示模型 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeState.java` | 持久化状态根对象 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeStateStore.java` | active 状态存储接口 |
| 新建 | `src/main/java/com/lunacode/worktree/JsonWorktreeStateStore.java` | `.lunacode/worktree_state.json` 读写 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeSession.java` | 当前 worktree 会话 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeSessionStore.java` | session 存储接口 |
| 新建 | `src/main/java/com/lunacode/worktree/JsonWorktreeSessionStore.java` | `.lunacode/worktree_session.json` 读写 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeCreateRequest.java` | 创建请求 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeCreateResult.java` | 创建结果 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeRemoveRequest.java` | 删除请求 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeRemoveResult.java` | 删除结果 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeSettings.java` | 初始化配置 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeSettingsLoader.java` | 读取 `settings.local.json` 中的 worktree 配置 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeIncludeMatcher.java` | `.worktreeinclude` 匹配接口 |
| 新建 | `src/main/java/com/lunacode/worktree/GitignoreWorktreeIncludeMatcher.java` | gitignore 风格匹配实现 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeEnvironmentInitializer.java` | 环境初始化接口 |
| 新建 | `src/main/java/com/lunacode/worktree/DefaultWorktreeEnvironmentInitializer.java` | 本地配置、hooks、软链、忽略文件补齐 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeCleanupPolicy.java` | 过期清理策略 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeCleanupResult.java` | 清理结果 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeManager.java` | worktree 管理接口 |
| 新建 | `src/main/java/com/lunacode/worktree/DefaultWorktreeManager.java` | 创建、进入、退出、删除、清理编排 |
| 新建 | `src/main/java/com/lunacode/worktree/WorktreeCommandHandler.java` | `/worktree` 命令接口 |
| 新建 | `src/main/java/com/lunacode/worktree/DefaultWorktreeCommandHandler.java` | `/worktree` 命令解析和输出 |
| 新建 | `src/main/java/com/lunacode/tool/ToolExecutionScope.java` | 单次工具调用有效工作目录 |
| 新建 | `src/main/java/com/lunacode/tool/ToolExecutionScopeHolder.java` | 工具调用 scope 线程上下文 |
| 修改 | `src/main/java/com/lunacode/tool/ToolExecutionContext.java` | 支持 repoRoot、workDir 和当前 worktree session |
| 修改 | `src/main/java/com/lunacode/tool/DefaultToolExecutor.java` | 按 scope 派生工具执行上下文 |
| 修改 | `src/main/java/com/lunacode/tool/WorkspacePathResolver.java` | 按有效 workDir 解析和相对化 |
| 修改 | `src/main/java/com/lunacode/tool/ReadFileTool.java` | 使用有效 workDir 读取文件 |
| 修改 | `src/main/java/com/lunacode/tool/WriteFileTool.java` | 使用有效 workDir 写文件 |
| 修改 | `src/main/java/com/lunacode/tool/EditFileTool.java` | 使用有效 workDir 修改文件 |
| 修改 | `src/main/java/com/lunacode/tool/GlobTool.java` | 使用有效 workDir 搜索文件 |
| 修改 | `src/main/java/com/lunacode/tool/GrepTool.java` | 使用有效 workDir 搜索内容 |
| 修改 | `src/main/java/com/lunacode/hook/ShellCommandRunner.java` | Bash/Hook 命令使用有效 cwd |
| 修改 | `src/main/java/com/lunacode/agent/execution/AgentToolRunner.java` | 工具执行前设置 ToolExecutionScope |
| 修改 | `src/main/java/com/lunacode/tool/DefaultToolPermissionGateway.java` | 权限检查使用有效 workDir |
| 修改 | `src/main/java/com/lunacode/permission/PermissionTargetExtractor.java` | 路径目标跟随有效沙箱根 |
| 修改 | `src/main/java/com/lunacode/subagent/AgentDefinition.java` | 增加 isolation 字段 |
| 修改 | `src/main/java/com/lunacode/subagent/FrontmatterAgentDefinitionParser.java` | 解析 `isolation: worktree` |
| 修改 | `src/main/java/com/lunacode/subagent/SubAgentLaunchRequest.java` | 携带可选 WorktreeRecord |
| 修改 | `src/main/java/com/lunacode/subagent/DefaultSubAgentService.java` | 定义式子 Agent 自动创建 worktree |
| 修改 | `src/main/java/com/lunacode/subagent/DefaultSubAgentRunnerFactory.java` | 子 Agent 使用 worktree workDir 并完成后清理 |
| 修改 | `src/main/java/com/lunacode/subagent/SubAgentResult.java` | 返回保留 worktree 的路径和分支信息 |
| 修改 | `src/main/java/com/lunacode/command/BuiltinSlashCommands.java` | 注册 `/worktree` 命令 |
| 修改 | `src/main/java/com/lunacode/command/CommandRuntime.java` | 增加 worktree 命令运行入口 |
| 修改 | `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` | 接入 WorktreeManager、effective workDir 和命令 handler |
| 修改 | `src/main/java/com/lunacode/prompt/EnvironmentContext.java` | 渲染当前 worktree 会话信息 |
| 修改 | `src/main/java/com/lunacode/prompt/EnvironmentContextCollector.java` | 按 effective workDir 收集 Git 状态 |
| 修改 | `src/main/java/com/lunacode/app/LunaCodeApplication.java` | 装配 WorktreeManager、恢复 session、启动清理器 |
| 新建 | `src/test/java/com/lunacode/worktree/DefaultWorktreeNameValidatorTest.java` | 名称校验测试 |
| 新建 | `src/test/java/com/lunacode/worktree/ProcessGitWorktreeClientTest.java` | Git 子进程、快速恢复和变更统计测试 |
| 新建 | `src/test/java/com/lunacode/worktree/JsonWorktreeStateStoreTest.java` | 状态持久化测试 |
| 新建 | `src/test/java/com/lunacode/worktree/JsonWorktreeSessionStoreTest.java` | session 持久化测试 |
| 新建 | `src/test/java/com/lunacode/worktree/GitignoreWorktreeIncludeMatcherTest.java` | `.worktreeinclude` 匹配测试 |
| 新建 | `src/test/java/com/lunacode/worktree/DefaultWorktreeEnvironmentInitializerTest.java` | 环境初始化测试 |
| 新建 | `src/test/java/com/lunacode/worktree/DefaultWorktreeManagerTest.java` | 创建、进入、退出、删除、清理测试 |
| 新建 | `src/test/java/com/lunacode/worktree/DefaultWorktreeCommandHandlerTest.java` | `/worktree` 命令测试 |
| 新建 | `src/test/java/com/lunacode/tool/ToolExecutionScopeTest.java` | 工具 effective cwd 测试 |
| 修改 | `src/test/java/com/lunacode/tool/ReadFileToolTest.java` | 补充 worktree cwd 读取测试 |
| 修改 | `src/test/java/com/lunacode/tool/WriteFileToolTest.java` | 补充 worktree cwd 写入测试 |
| 修改 | `src/test/java/com/lunacode/tool/BashToolTest.java` | 补充 worktree cwd 命令测试 |
| 修改 | `src/test/java/com/lunacode/subagent/FrontmatterAgentDefinitionParserTest.java` | 补充 isolation 解析测试 |
| 修改 | `src/test/java/com/lunacode/subagent/DefaultSubAgentServiceTest.java` | 补充自动 worktree 创建测试 |
| 修改 | `src/test/java/com/lunacode/subagent/DefaultSubAgentRunnerFactoryTest.java` | 补充子 Agent workDir 和清理测试 |
| 修改 | `src/test/java/com/lunacode/orchestrator/SlashCommandOrchestratorTest.java` | 补充 `/worktree` 集成测试 |
| 修改 | `src/test/java/com/lunacode/prompt/PromptContextBuilderTest.java` | 补充 worktree 环境上下文测试 |

## T1: 创建 worktree 基础值对象

**文件：** `src/main/java/com/lunacode/worktree/WorktreeKind.java`、`WorktreeRecord.java`、`WorktreeSnapshot.java`、`WorktreeChanges.java`、`WorktreeCreateRequest.java`、`WorktreeCreateResult.java`、`WorktreeRemoveRequest.java`、`WorktreeRemoveResult.java`、`GitRepositoryState.java`、`FastRestoredHead.java`、`WorktreeCleanupPolicy.java`、`WorktreeCleanupResult.java`
**依赖：** 无
**步骤：**
1. 新建 `worktree` 包。
2. 按 plan 定义所有 record 和 enum。
3. 给 record 构造器补充空值默认、绝对路径 normalize 和不可变 List 复制。
4. 给 `WorktreeChanges` 增加 `hasChanges()`。

**验证：** 运行 `mvn -q -DskipTests compile`，期望编译通过。

## T2: 实现名称校验

**文件：** `src/main/java/com/lunacode/worktree/ValidWorktreeName.java`、`WorktreeNameValidator.java`、`DefaultWorktreeNameValidator.java`、`src/test/java/com/lunacode/worktree/DefaultWorktreeNameValidatorTest.java`
**依赖：** T1
**步骤：**
1. 定义 `ValidWorktreeName`，包含原名、目录相对路径、分支 slug、分支名。
2. 实现字符集、长度、段规则、反斜杠和 Windows 保留名校验。
3. 实现手动名称 `/` 到目录嵌套、分支名 `+` 替换。
4. 实现 `AGENT` 名称 `agent-a` 加 7 位 hex 校验，`WORKFLOW` 名称 `wf_` 固定 hex 校验。
5. 编写合法、非法和边界长度测试。

**验证：** 运行 `mvn -q -Dtest=DefaultWorktreeNameValidatorTest test`，期望全部通过。

## T3: 定义 Git worktree 客户端接口

**文件：** `src/main/java/com/lunacode/worktree/GitWorktreeClient.java`
**依赖：** T1
**步骤：**
1. 按 plan 定义 `inspectRepository`、`tryReadHead`、`addWorktree`、`countChanges`、`removeWorktree`、`deleteBranch`、`configureHooksPath`、`ignoredFiles`。
2. 为每个方法写清 JavaDoc，说明是否调用 Git 子进程。
3. 明确所有异常统一使用 `IllegalStateException` 或 `IllegalArgumentException` 向上报告。

**验证：** 运行 `mvn -q -DskipTests compile`，期望编译通过。

## T4: 实现 Git 子进程执行骨架

**文件：** `src/main/java/com/lunacode/worktree/ProcessGitWorktreeClient.java`、`src/test/java/com/lunacode/worktree/ProcessGitWorktreeClientTest.java`
**依赖：** T3
**步骤：**
1. 新建 `ProcessGitWorktreeClient`。
2. 添加内部 Git 命令运行方法，统一设置 `GIT_TERMINAL_PROMPT=0` 和 `GIT_ASKPASS=`。
3. 设置 `ProcessBuilder.Redirect.PIPE` 并立即关闭 stdin，避免交互等待。
4. 捕获 stdout、stderr、exit code 和 timeout，失败时抛出包含命令摘要的错误。
5. 用测试替身或临时 git 仓库验证环境变量和 stdin 行为。

**验证：** 运行 `mvn -q -Dtest=ProcessGitWorktreeClientTest test`，期望 Git 命令环境测试通过。

## T5: 实现仓库状态读取

**文件：** `src/main/java/com/lunacode/worktree/ProcessGitWorktreeClient.java`、`src/test/java/com/lunacode/worktree/ProcessGitWorktreeClientTest.java`
**依赖：** T4
**步骤：**
1. 实现 `inspectRepository`。
2. 读取 `rev-parse HEAD` 作为 base commit。
3. 读取 `branch --show-current` 作为可选原分支。
4. 读取 `status --porcelain` 判断 dirty，并生成 warning 所需摘要。
5. 增加干净仓库和 dirty 仓库测试。

**验证：** 运行 `mvn -q -Dtest=ProcessGitWorktreeClientTest test`，期望仓库状态相关测试通过。

## T6: 实现快速恢复 HEAD 读取

**文件：** `src/main/java/com/lunacode/worktree/ProcessGitWorktreeClient.java`、`src/test/java/com/lunacode/worktree/ProcessGitWorktreeClientTest.java`
**依赖：** T4
**步骤：**
1. 实现 `tryReadHead(Path worktreePath)`。
2. 读取 worktree 下 `.git` 指针文件并解析 `gitdir:`。
3. 读取 gitdir 下 `HEAD`。
4. 当 HEAD 为 `ref: refs/heads/...` 时继续读取 loose ref。
5. loose ref 不存在时解析 `packed-refs`。
6. HEAD 为 detached commit 时直接返回 commit。
7. 对无 `.git`、坏指针、缺 ref、packed ref、detached HEAD 分别写测试。

**验证：** 运行 `mvn -q -Dtest=ProcessGitWorktreeClientTest test`，期望快速恢复测试通过且测试断言不调用 Git 命令。

## T7: 实现 Git worktree 创建和删除命令

**文件：** `src/main/java/com/lunacode/worktree/ProcessGitWorktreeClient.java`、`src/test/java/com/lunacode/worktree/ProcessGitWorktreeClientTest.java`
**依赖：** T4
**步骤：**
1. 实现 `addWorktree`，调用 `git worktree add -B <branch> <path> <baseCommit>`。
2. 实现 `removeWorktree`，调用 `git worktree remove --force <path>`。
3. 实现 `deleteBranch`，调用 `git branch -D <branch>`。
4. 在删除 worktree 和删除分支之间加入短暂等待。
5. 用临时 git 仓库验证创建目录、分支前缀和删除分支。

**验证：** 运行 `mvn -q -Dtest=ProcessGitWorktreeClientTest test`，期望创建删除测试通过。

## T8: 实现变更统计

**文件：** `src/main/java/com/lunacode/worktree/ProcessGitWorktreeClient.java`、`src/test/java/com/lunacode/worktree/ProcessGitWorktreeClientTest.java`
**依赖：** T4
**步骤：**
1. 实现 `countChanges`。
2. 使用 `git -C <path> status --porcelain` 统计未提交修改。
3. 使用 `git -C <path> rev-list --count <baseHeadCommit>..HEAD` 统计新增 commit。
4. 对 clean、dirty、new commit、dirty + new commit 分别写测试。

**验证：** 运行 `mvn -q -Dtest=ProcessGitWorktreeClientTest test`，期望变更统计测试通过。

## T9: 实现 hooks 和 ignored files Git 操作

**文件：** `src/main/java/com/lunacode/worktree/ProcessGitWorktreeClient.java`、`src/test/java/com/lunacode/worktree/ProcessGitWorktreeClientTest.java`
**依赖：** T4
**步骤：**
1. 实现 `configureHooksPath`，在 worktree 路径下设置 `core.hooksPath`。
2. 实现 `ignoredFiles`，运行 `git ls-files --others --ignored --exclude-standard --directory`。
3. 对 hooksPath 配置结果写测试。
4. 对 `.gitignore` 忽略文件列表写测试。

**验证：** 运行 `mvn -q -Dtest=ProcessGitWorktreeClientTest test`，期望 hooks 和 ignored files 测试通过。

## T10: 实现 worktree 状态持久化

**文件：** `src/main/java/com/lunacode/worktree/WorktreeState.java`、`WorktreeStateStore.java`、`JsonWorktreeStateStore.java`、`src/test/java/com/lunacode/worktree/JsonWorktreeStateStoreTest.java`
**依赖：** T1
**步骤：**
1. 定义 `WorktreeState`，保存 active map。
2. 实现 JSON 读写，文件路径为 `.lunacode/worktree_state.json`。
3. 写入时创建父目录，使用临时文件再移动替换。
4. 读不到文件时返回空状态。
5. 写读 round-trip、空文件、不合法 JSON 场景测试。

**验证：** 运行 `mvn -q -Dtest=JsonWorktreeStateStoreTest test`，期望状态持久化测试通过。

## T11: 实现 worktree session 持久化

**文件：** `src/main/java/com/lunacode/worktree/WorktreeSession.java`、`WorktreeSessionStore.java`、`JsonWorktreeSessionStore.java`、`src/test/java/com/lunacode/worktree/JsonWorktreeSessionStoreTest.java`
**依赖：** T1
**步骤：**
1. 定义 `WorktreeSession`。
2. 实现 `.lunacode/worktree_session.json` 读写。
3. `save(Optional.empty())` 时删除或清空 session 文件。
4. 对读写、清除、缺失文件、不合法 JSON 写测试。

**验证：** 运行 `mvn -q -Dtest=JsonWorktreeSessionStoreTest test`，期望 session 持久化测试通过。

## T12: 实现 worktree 配置读取

**文件：** `src/main/java/com/lunacode/worktree/WorktreeSettings.java`、`WorktreeSettingsLoader.java`、`src/test/java/com/lunacode/worktree/DefaultWorktreeEnvironmentInitializerTest.java`
**依赖：** T1
**步骤：**
1. 定义 `WorktreeSettings`。
2. 从 `settings.local.json` 读取 `settings.worktree.symlinkDirectories`。
3. 默认 `localConfigFiles` 包含 `settings.local.json`。
4. 缺失配置返回空软链列表。
5. 字段类型非法时返回 warning 并忽略该字段。

**验证：** 运行 `mvn -q -Dtest=DefaultWorktreeEnvironmentInitializerTest test`，期望配置读取相关测试通过。

## T13: 实现 .worktreeinclude 匹配

**文件：** `src/main/java/com/lunacode/worktree/WorktreeIncludeMatcher.java`、`GitignoreWorktreeIncludeMatcher.java`、`src/test/java/com/lunacode/worktree/GitignoreWorktreeIncludeMatcherTest.java`
**依赖：** T1
**步骤：**
1. 定义匹配接口。
2. 解析 `.worktreeinclude`，支持空行、注释、`!` 否定、根匹配、目录匹配、`*`、`?` 和 `**`。
3. 输入 ignored files 列表，输出匹配文件列表。
4. 对 `.env`、目录、否定规则、无 include 文件写测试。

**验证：** 运行 `mvn -q -Dtest=GitignoreWorktreeIncludeMatcherTest test`，期望匹配测试通过。

## T14: 实现环境初始化

**文件：** `src/main/java/com/lunacode/worktree/WorktreeEnvironmentInitializer.java`、`DefaultWorktreeEnvironmentInitializer.java`、`src/test/java/com/lunacode/worktree/DefaultWorktreeEnvironmentInitializerTest.java`
**依赖：** T9、T12、T13
**步骤：**
1. 复制 `settings.local.json` 到 worktree，缺失时 warning。
2. 优先检测 `.husky/`，没有时回退 `.git/hooks`，调用 `configureHooksPath`。
3. 为 `symlinkDirectories` 中存在的目录创建软链。
4. 读取 ignored files 并按 `.worktreeinclude` 复制匹配文件。
5. 将复制、hooks、软链、匹配失败全部转成 warning。
6. 写测试覆盖本地配置、hooks、软链、`.env` 复制和 best-effort warning。

**验证：** 运行 `mvn -q -Dtest=DefaultWorktreeEnvironmentInitializerTest test`，期望环境初始化测试通过。

## T15: 实现 WorktreeManager 创建流程

**文件：** `src/main/java/com/lunacode/worktree/WorktreeManager.java`、`DefaultWorktreeManager.java`、`src/test/java/com/lunacode/worktree/DefaultWorktreeManagerTest.java`
**依赖：** T2、T5、T6、T7、T10、T14
**步骤：**
1. 定义 `WorktreeManager` 接口。
2. 实现 `create` 的名称校验和管理目录路径解析。
3. 调用 `inspectRepository`，dirty 时加入 warning。
4. 目标目录存在时先 `tryReadHead`。
5. 快速恢复成功时保存 record 并跳过环境初始化。
6. 快速恢复失败时调用 `addWorktree` 后执行环境初始化。
7. 更新 active 状态并持久化。
8. 测试新建、dirty warning、快速恢复和初始化跳过。

**验证：** 运行 `mvn -q -Dtest=DefaultWorktreeManagerTest test`，期望创建流程测试通过。

## T16: 实现 WorktreeManager 列表和查找

**文件：** `src/main/java/com/lunacode/worktree/DefaultWorktreeManager.java`、`WorktreeSnapshot.java`、`src/test/java/com/lunacode/worktree/DefaultWorktreeManagerTest.java`
**依赖：** T15、T8
**步骤：**
1. 实现 `find`。
2. 实现 `list`，为每个 active record 补充当前变更摘要和 current 标识。
3. 更新 `lastUsedAt` 不在 list 中执行，避免只读命令改变状态。
4. 测试 list 输出字段和当前 session 标识。

**验证：** 运行 `mvn -q -Dtest=DefaultWorktreeManagerTest test`，期望列表测试通过。

## T17: 实现 WorktreeManager enter/exit/session 恢复

**文件：** `src/main/java/com/lunacode/worktree/DefaultWorktreeManager.java`、`src/test/java/com/lunacode/worktree/DefaultWorktreeManagerTest.java`
**依赖：** T11、T15
**步骤：**
1. 实现 `enter(name, sessionId)`，记录 original cwd、worktree 路径、分支、原分支、原 HEAD 和 session id。
2. 持久化 session。
3. 实现 `exit()`，清除内存 session 和持久化 session。
4. 实现构造或启动方法中的 session 恢复：`tryReadHead` 成功则恢复，失败则清除并 warning。
5. 实现 `effectiveWorkDir()`。
6. 测试 enter 不改变进程 cwd、exit 恢复 effective workDir、启动恢复有效/无效 session。

**验证：** 运行 `mvn -q -Dtest=DefaultWorktreeManagerTest test`，期望 session 测试通过。

## T18: 实现 WorktreeManager 删除保护

**文件：** `src/main/java/com/lunacode/worktree/DefaultWorktreeManager.java`、`src/test/java/com/lunacode/worktree/DefaultWorktreeManagerTest.java`
**依赖：** T8、T15
**步骤：**
1. 实现 `remove`。
2. 自动删除手动 worktree 时直接拒绝。
3. 未设置 `discardChanges` 时调用 `countChanges`。
4. 有 uncommitted 或 newCommits 时返回拒绝结果。
5. 无变更或显式丢弃时执行 remove worktree 和 delete branch。
6. 删除成功后从 active 状态移除并持久化。
7. 测试 clean 删除、dirty 拒绝、新 commit 拒绝、discard 删除、自动删除手动 worktree 拒绝。

**验证：** 运行 `mvn -q -Dtest=DefaultWorktreeManagerTest test`，期望删除保护测试通过。

## T19: 实现后台过期清理

**文件：** `src/main/java/com/lunacode/worktree/DefaultWorktreeManager.java`、`WorktreeCleanupPolicy.java`、`WorktreeCleanupResult.java`、`src/test/java/com/lunacode/worktree/DefaultWorktreeManagerTest.java`
**依赖：** T18
**步骤：**
1. 实现 `cleanupExpired`。
2. 检查 ttl、管理目录、状态记录、自动名称模式、`worktree-` 分支前缀。
3. 调用 `countChanges`，有成果则保留。
4. 无成果才调用自动 remove。
5. 测试手动 worktree 不清理、自动 clean 清理、自动 dirty 保留、分支前缀不匹配保留。

**验证：** 运行 `mvn -q -Dtest=DefaultWorktreeManagerTest test`，期望清理测试通过。

## T20: 实现 /worktree 命令 handler

**文件：** `src/main/java/com/lunacode/worktree/WorktreeCommandHandler.java`、`DefaultWorktreeCommandHandler.java`、`src/test/java/com/lunacode/worktree/DefaultWorktreeCommandHandlerTest.java`
**依赖：** T15、T16、T17、T18
**步骤：**
1. 定义命令结果对象或复用现有 command result 模式。
2. 解析 `create <name>`。
3. 解析 `list` 并格式化表格文本。
4. 解析 `enter <name>` 和 `exit`。
5. 解析 `remove <name> [--discardChanges]`。
6. 未知子命令返回用法。
7. 忙碌状态下拒绝 create、enter、exit、remove，允许空闲 list。
8. 写命令解析和输出测试。

**验证：** 运行 `mvn -q -Dtest=DefaultWorktreeCommandHandlerTest test`，期望命令 handler 测试通过。

## T21: 接入 Slash Command Runtime

**文件：** `src/main/java/com/lunacode/command/BuiltinSlashCommands.java`、`src/main/java/com/lunacode/command/CommandRuntime.java`、`src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`src/test/java/com/lunacode/orchestrator/SlashCommandOrchestratorTest.java`
**依赖：** T20
**步骤：**
1. 在 `CommandRuntime` 增加 `runWorktreeCommand(String rawInput)`。
2. 在 `BuiltinSlashCommands.registerAll` 注册 `/worktree`。
3. 在 `DefaultChatOrchestrator` 持有可选 `WorktreeCommandHandler`。
4. 实现 `runWorktreeCommand`，把 command result 映射到 info/warning/error 状态。
5. 增加 orchestrator 测试验证 `/worktree list` 和未知子命令。

**验证：** 运行 `mvn -q -Dtest=SlashCommandOrchestratorTest test`，期望 `/worktree` 调度测试通过。

## T22: 引入 ToolExecutionScope

**文件：** `src/main/java/com/lunacode/tool/ToolExecutionScope.java`、`ToolExecutionScopeHolder.java`、`src/test/java/com/lunacode/tool/ToolExecutionScopeTest.java`
**依赖：** T11
**步骤：**
1. 定义 `ToolExecutionScope`。
2. 实现 `ToolExecutionScopeHolder` thread-local。
3. 确保 `withScope` 在 finally 中清理旧 scope。
4. 写嵌套 scope 和并发线程隔离测试。

**验证：** 运行 `mvn -q -Dtest=ToolExecutionScopeTest test`，期望 scope 测试通过。

## T23: 扩展 ToolExecutionContext

**文件：** `src/main/java/com/lunacode/tool/ToolExecutionContext.java`、`src/test/java/com/lunacode/tool/ToolExecutionScopeTest.java`
**依赖：** T22
**步骤：**
1. 增加 `repoRoot`、`workDir`、`worktreeSession` 字段。
2. 保持旧构造器兼容，把旧 `workspaceRoot` 同时作为 repoRoot 和 workDir。
3. 提供 `withScope(ToolExecutionScope scope)` 或等价方法，派生本次调用上下文。
4. 确保 sandboxRoots 和 masker 沿用原上下文。
5. 测试旧构造器和派生上下文的路径字段。

**验证：** 运行 `mvn -q -Dtest=ToolExecutionScopeTest test`，期望上下文兼容测试通过。

## T24: 改造 DefaultToolExecutor 使用 scope

**文件：** `src/main/java/com/lunacode/tool/DefaultToolExecutor.java`、`src/test/java/com/lunacode/tool/ToolExecutionScopeTest.java`
**依赖：** T23
**步骤：**
1. 在 execute 前读取 `ToolExecutionScopeHolder.current()`。
2. 有 scope 时派生 `ToolExecutionContext`。
3. 无 scope 时保持原行为。
4. 用测试工具断言 execute 收到的 `workDir`。

**验证：** 运行 `mvn -q -Dtest=ToolExecutionScopeTest test`，期望 executor scope 测试通过。

## T25: 改造 WorkspacePathResolver

**文件：** `src/main/java/com/lunacode/tool/WorkspacePathResolver.java`、`src/test/java/com/lunacode/tool/ToolExecutionScopeTest.java`
**依赖：** T23
**步骤：**
1. 增加基于 `ToolExecutionContext` 的 resolve 方法。
2. 路径解析时优先使用 `context.workDir()`。
3. 保持 pathSandbox 校验；如 pathSandbox 绑定 repoRoot，补充 workDir 内路径校验，防止逃逸当前 worktree。
4. 相对化时优先相对 `context.workDir()`。
5. 保留旧方法兼容现有调用。

**验证：** 运行 `mvn -q -Dtest=ToolExecutionScopeTest test`，期望路径解析 scope 测试通过。

## T26: 改造文件工具使用 effective workDir

**文件：** `src/main/java/com/lunacode/tool/ReadFileTool.java`、`WriteFileTool.java`、`EditFileTool.java`、`src/test/java/com/lunacode/tool/ReadFileToolTest.java`、`WriteFileToolTest.java`、`EditFileToolTest.java`
**依赖：** T25
**步骤：**
1. `ReadFileTool` 调用 context-aware resolver。
2. `WriteFileTool` 调用 context-aware resolver。
3. `EditFileTool` 调用 context-aware resolver 和 writer。
4. 保持 metadata path 相对当前 effective workDir。
5. 补充主仓库和 worktree 同名文件读取/写入互不影响测试。

**验证：** 运行 `mvn -q -Dtest=ReadFileToolTest,WriteFileToolTest,EditFileToolTest test`，期望文件工具测试通过。

## T27: 改造搜索工具使用 effective workDir

**文件：** `src/main/java/com/lunacode/tool/GlobTool.java`、`GrepTool.java`、`src/test/java/com/lunacode/tool/GlobToolTest.java`、`GrepToolTest.java`
**依赖：** T25
**步骤：**
1. `GlobTool` 从 `context.workDir()` walk 文件。
2. `GrepTool` 默认从 `context.workDir()` 搜索。
3. `GrepTool` 指定 path 时使用 context-aware resolver。
4. 跳过规则按当前 effective workDir 计算相对路径。
5. 补充 worktree 下搜索不会命中主仓库文件的测试。

**验证：** 运行 `mvn -q -Dtest=GlobToolTest,GrepToolTest test`，期望搜索工具测试通过。

## T28: 改造 Bash 和 Hook 命令 cwd

**文件：** `src/main/java/com/lunacode/hook/ShellCommandRunner.java`、`src/main/java/com/lunacode/tool/BashTool.java`、`src/test/java/com/lunacode/tool/BashToolTest.java`、`src/test/java/com/lunacode/hook/CommandHookActionExecutorTest.java`
**依赖：** T23
**步骤：**
1. `ShellCommandRunner` 的 `wrapShellCommand` 和 `ProcessBuilder.directory` 使用 `context.workDir()`。
2. 确认 sandbox roots 仍来自原 context。
3. `BashTool` 不改 schema，只受上下文 cwd 影响。
4. 测试 `pwd` 或等价命令输出为 worktree 路径。
5. 测试 Hook command action 在 worktree cwd 下运行。

**验证：** 运行 `mvn -q -Dtest=BashToolTest,CommandHookActionExecutorTest test`，期望 cwd 测试通过。

## T29: 改造 AgentToolRunner 设置工具 scope

**文件：** `src/main/java/com/lunacode/agent/execution/AgentToolRunner.java`、`src/test/java/com/lunacode/agent/execution/AgentToolRunnerTest.java`
**依赖：** T22、T24
**步骤：**
1. 在 `executeOne` 中根据 `AgentRunConfig.workDir()` 创建 `ToolExecutionScope`。
2. 串行工具调用使用 `ToolExecutionScopeHolder.withScope`。
3. 并发工具 future 中也写入同一个 scope。
4. 保留已有 `AgentExecutionContextHolder` 行为。
5. 测试串行和并发工具都收到正确 workDir。

**验证：** 运行 `mvn -q -Dtest=AgentToolRunnerTest test`，期望工具 runner scope 测试通过。

## T30: 改造权限检查使用 effective workDir

**文件：** `src/main/java/com/lunacode/tool/DefaultToolPermissionGateway.java`、`src/main/java/com/lunacode/permission/PermissionTargetExtractor.java`、`src/test/java/com/lunacode/tool/ToolPermissionGatewayTest.java`、`src/test/java/com/lunacode/permission/DefaultPermissionEngineTest.java`
**依赖：** T23、T29
**步骤：**
1. `DefaultToolPermissionGateway.evaluate` 使用 `config.workDir()` 构建权限请求上下文。
2. 如 `PermissionTargetExtractor` 内部持有 pathSandbox，增加基于当前 workDir 的创建方式或参数。
3. 确保写入 worktree 内文件不会被误判为主仓库外路径。
4. 确保访问 worktree 外路径仍被拒绝。
5. 补充权限测试。

**验证：** 运行 `mvn -q -Dtest=ToolPermissionGatewayTest,DefaultPermissionEngineTest test`，期望权限测试通过。

## T31: 接入主 Agent effective workDir

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`src/test/java/com/lunacode/orchestrator/DefaultChatOrchestratorTest.java`
**依赖：** T17、T21、T29
**步骤：**
1. `DefaultChatOrchestrator` 持有可选 `WorktreeManager`。
2. `runConfig` 使用 `worktreeManager.effectiveWorkDir()`，无 manager 时保持 `workspaceRoot`。
3. 计划文件按 effective workDir 解析。
4. `/worktree enter` 后下一轮 AgentRequest 的 config.workDir 为 worktree 路径。
5. `/worktree exit` 后恢复 repoRoot。
6. 补充 orchestrator runConfig 测试。

**验证：** 运行 `mvn -q -Dtest=DefaultChatOrchestratorTest test`，期望 effective workDir 测试通过。

## T32: 更新环境上下文渲染

**文件：** `src/main/java/com/lunacode/prompt/EnvironmentContext.java`、`EnvironmentContextCollector.java`、`src/test/java/com/lunacode/prompt/PromptContextBuilderTest.java`
**依赖：** T23、T31
**步骤：**
1. `EnvironmentContext` 增加可选 worktree 名称、分支和路径信息。
2. `EnvironmentContextCollector` 使用 `config.workDir()` 收集 Git 状态。
3. 如果 `ToolExecutionScope` 或 config 能提供 session，则渲染 worktree 会话说明。
4. 测试 prompt 中出现当前 worktree 路径和分支。

**验证：** 运行 `mvn -q -Dtest=PromptContextBuilderTest test`，期望环境上下文测试通过。

## T33: 解析 AgentDefinition isolation 字段

**文件：** `src/main/java/com/lunacode/subagent/AgentDefinition.java`、`FrontmatterAgentDefinitionParser.java`、`src/test/java/com/lunacode/subagent/FrontmatterAgentDefinitionParserTest.java`
**依赖：** T1
**步骤：**
1. 增加 `SubAgentIsolation` enum 或等价字段。
2. `AgentDefinition` 增加 `isolation`。
3. parser 解析 `isolation` 字符串。
4. 空值为 none，`worktree` 为 worktree。
5. 非法值返回 parse failure，角色被跳过。
6. 更新既有构造器兼容测试。

**验证：** 运行 `mvn -q -Dtest=FrontmatterAgentDefinitionParserTest test`，期望 isolation 解析测试通过。

## T34: 扩展 SubAgentLaunchRequest 携带 worktree

**文件：** `src/main/java/com/lunacode/subagent/SubAgentLaunchRequest.java`、`src/main/java/com/lunacode/subagent/SubAgentResult.java`、`src/test/java/com/lunacode/subagent/DefaultSubAgentRunnerFactoryTest.java`
**依赖：** T1、T33
**步骤：**
1. `SubAgentLaunchRequest` 增加 `Optional<WorktreeRecord>`。
2. `SubAgentResult` 增加可选保留 worktree 路径和分支。
3. 更新所有构造调用点，默认 `Optional.empty()`。
4. 更新测试编译。

**验证：** 运行 `mvn -q -Dtest=DefaultSubAgentRunnerFactoryTest test`，期望构造兼容测试通过。

## T35: 子 Agent 启动前创建 worktree

**文件：** `src/main/java/com/lunacode/subagent/DefaultSubAgentService.java`、`src/test/java/com/lunacode/subagent/DefaultSubAgentServiceTest.java`
**依赖：** T15、T33、T34
**步骤：**
1. `DefaultSubAgentService` 注入可选 `WorktreeManager`。
2. 定义式子 Agent 且 `isolation=worktree` 时生成 `agent-a` 加 7 位 hex 名称。
3. 调用 `WorktreeManager.create(AGENT)`。
4. 把 `WorktreeRecord` 放入 `SubAgentLaunchRequest`。
5. Fork 和 Hook 路径保持不创建。
6. 创建失败时返回工具错误，错误内容包含原因。
7. 测试定义式创建、未声明不创建、Fork 不创建、Hook 不创建。

**验证：** 运行 `mvn -q -Dtest=DefaultSubAgentServiceTest test`，期望子 Agent 创建 worktree 测试通过。

## T36: 子 Agent 使用 worktree workDir

**文件：** `src/main/java/com/lunacode/subagent/DefaultSubAgentRunnerFactory.java`、`src/test/java/com/lunacode/subagent/DefaultSubAgentRunnerFactoryTest.java`
**依赖：** T34、T35
**步骤：**
1. `childConfig` 如果 launch request 携带 worktree，则使用 `worktree.path()` 作为 `workDir`。
2. 子 Agent system prompt 追加 worktree 路径、分支和隔离说明。
3. 权限规则、path sandbox 和工具 runner 都基于 child workDir 创建。
4. 测试 child config workDir 为 worktree 路径。
5. 测试 system prompt 包含隔离路径说明。

**验证：** 运行 `mvn -q -Dtest=DefaultSubAgentRunnerFactoryTest test`，期望子 Agent workDir 测试通过。

## T37: 子 Agent 完成后自动清理或保留

**文件：** `src/main/java/com/lunacode/subagent/DefaultSubAgentRunnerFactory.java`、`src/main/java/com/lunacode/subagent/SubAgentResult.java`、`src/test/java/com/lunacode/subagent/DefaultSubAgentRunnerFactoryTest.java`
**依赖：** T18、T36
**步骤：**
1. 子 Agent completion 后，如果 launch request 有 worktree，调用 `WorktreeManager.remove(automatic=true)`。
2. 删除成功时结果记录“worktree 已清理”摘要。
3. 因变更被保留时，把 worktree path 和 branch 写入 `SubAgentResult`。
4. 异常时保留 worktree，并把警告写入结果。
5. 测试无变更自动删除、有变更保留并返回路径、删除异常保留。

**验证：** 运行 `mvn -q -Dtest=DefaultSubAgentRunnerFactoryTest test`，期望子 Agent 清理测试通过。

## T38: 更新后台通知中的 worktree 信息

**文件：** `src/main/java/com/lunacode/background/TaskNotificationFormatter.java`、`src/test/java/com/lunacode/background/TaskNotificationFormatterTest.java`
**依赖：** T37
**步骤：**
1. 如果 `SubAgentResult` 包含保留 worktree，通知文本包含路径和分支。
2. 如果自动清理成功，通知文本包含已清理摘要。
3. 保持现有后台任务通知格式兼容。
4. 补充 formatter 测试。

**验证：** 运行 `mvn -q -Dtest=TaskNotificationFormatterTest test`，期望通知格式测试通过。

## T39: 装配 WorktreeManager 到应用

**文件：** `src/main/java/com/lunacode/app/LunaCodeApplication.java`、`src/test/java/com/lunacode/app/LunaCodeApplicationTest.java`
**依赖：** T19、T21、T31、T35
**步骤：**
1. 在应用启动时创建 `JsonWorktreeStateStore` 和 `JsonWorktreeSessionStore`。
2. 创建 `ProcessGitWorktreeClient`、initializer、manager、command handler。
3. 把 manager 注入 orchestrator 和 subAgentService。
4. 启动时恢复 session，无效 session warning 输出到 stderr 或状态。
5. 启动 scheduled cleanup，关闭应用时停止线程。
6. 更新应用装配测试。

**验证：** 运行 `mvn -q -Dtest=LunaCodeApplicationTest test`，期望应用装配测试通过。

## T40: 补全 `/worktree` 命令集成测试

**文件：** `src/test/java/com/lunacode/orchestrator/SlashCommandOrchestratorTest.java`、`src/test/java/com/lunacode/worktree/DefaultWorktreeCommandHandlerTest.java`
**依赖：** T21、T31
**步骤：**
1. 测试 `/help` 中出现 `/worktree`。
2. 测试 `/worktree create my-feature` 返回路径、分支和 warning。
3. 测试 `/worktree list` 展示 current 标识。
4. 测试 `/worktree enter` 后 orchestrator effective workDir 改变。
5. 测试 `/worktree exit` 后恢复 repoRoot。
6. 测试 `/worktree remove` 在有变更时拒绝，在 `--discardChanges` 时允许。

**验证：** 运行 `mvn -q -Dtest=SlashCommandOrchestratorTest,DefaultWorktreeCommandHandlerTest test`，期望命令集成测试通过。

## T41: 补全工具跨目录集成测试

**文件：** `src/test/java/com/lunacode/tool/ToolExecutionScopeTest.java`、`src/test/java/com/lunacode/tool/ReadFileToolTest.java`、`src/test/java/com/lunacode/tool/WriteFileToolTest.java`、`src/test/java/com/lunacode/tool/BashToolTest.java`
**依赖：** T26、T27、T28、T29
**步骤：**
1. 在临时 repoRoot 和 worktreePath 下创建同名文件。
2. 通过 scope 读取，断言读到 worktree 文件。
3. 通过 scope 写入，断言主仓库同名文件不变。
4. 执行 Bash `pwd` 或 Windows 兼容命令，断言 cwd 是 worktree。
5. 并发执行两个不同 scope，断言互不串目录。

**验证：** 运行 `mvn -q -Dtest=ToolExecutionScopeTest,ReadFileToolTest,WriteFileToolTest,BashToolTest test`，期望跨目录集成测试通过。

## T42: 补全子 Agent worktree 集成测试

**文件：** `src/test/java/com/lunacode/subagent/DefaultSubAgentServiceTest.java`、`src/test/java/com/lunacode/subagent/DefaultSubAgentRunnerFactoryTest.java`
**依赖：** T35、T36、T37
**步骤：**
1. 构造声明 `isolation: worktree` 的角色定义。
2. 模拟 Agent 工具调用定义式子 Agent。
3. 断言 manager 收到 `AGENT` 创建请求且名称匹配 `agent-a` 加 7 位 hex。
4. 断言子 Agent runner 使用 worktree 路径。
5. 模拟子 Agent 无变更完成，断言自动 remove。
6. 模拟子 Agent 有变更完成，断言保留路径和分支进入结果。

**验证：** 运行 `mvn -q -Dtest=DefaultSubAgentServiceTest,DefaultSubAgentRunnerFactoryTest test`，期望子 Agent 集成测试通过。

## T43: 编译和全量单元测试

**文件：** 全项目
**依赖：** T1-T42
**步骤：**
1. 运行项目编译。
2. 运行全量测试。
3. 修复编译错误和测试失败。
4. 确认现有不使用 worktree 的子 Agent、Hook、Skill、工具测试仍通过。

**验证：** 运行 `mvn test`，期望全部测试通过。

## T44: 端到端前准备

**文件：** `run-LunaCode.bat`、临时测试角色 `.lunacode/agents/*.md`、临时测试配置文件
**依赖：** T43
**步骤：**
1. 准备一个测试角色，frontmatter 包含 `isolation: worktree`。
2. 准备一个真实对话请求，要求主 Agent 创建 worktree、写入文件、退出。
3. 准备一个真实对子 Agent 的请求，要求子 Agent 修改一个临时文件。
4. 确认测试文件不会覆盖用户重要文件。

**验证：** 运行 `mvn package -DskipTests`，期望打包通过并可启动 LunaCode。

## T45: tmux 端到端验收

**文件：** 运行环境、`spec/13/checklist.md`
**依赖：** T44、已批准的 checklist
**步骤：**
1. 在 tmux 中启动 LunaCode。
2. 输入 `/worktree create e2e-manual`。
3. 输入 `/worktree enter e2e-manual`。
4. 发送真实对话，让主 Agent 读取并写入一个临时文件。
5. 输入 `/worktree exit`，确认后续工具回到主仓库。
6. 发送真实对话，让声明 `isolation: worktree` 的子 Agent 修改文件。
7. 观察子 Agent 完成后 worktree 被清理或保留的通知。
8. 对照 checklist.md 逐项验收。

**验证：** tmux 会话中能看到 `/worktree` 命令结果、工具调用落在对应 worktree、子 Agent 隔离生效，且 checklist 全部通过。

## 执行顺序

```text
T1
 -> T2
 -> T3 -> T4 -> T5 -> T6 -> T7 -> T8 -> T9
 -> T10 -> T11
 -> T12 -> T13 -> T14
 -> T15 -> T16 -> T17 -> T18 -> T19
 -> T20 -> T21
 -> T22 -> T23 -> T24 -> T25 -> T26 -> T27 -> T28 -> T29 -> T30
 -> T31 -> T32
 -> T33 -> T34 -> T35 -> T36 -> T37 -> T38
 -> T39
 -> T40 -> T41 -> T42
 -> T43 -> T44 -> T45
```
