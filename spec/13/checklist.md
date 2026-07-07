# Worktree 隔离 Checklist

> 每一项通过运行代码或观察行为来验证，聚焦系统行为。

## 实现完整性

- [ ] `/worktree create my-feature` 会在 `.lunacode/worktrees/my-feature` 创建 worktree，并创建 `worktree-my-feature` 分支（验证：运行命令后执行 `git branch --list worktree-my-feature`，期望能看到该分支）
- [ ] `/worktree create team-refactor/alice` 会创建嵌套 worktree，并创建 `worktree-team-refactor+alice` 分支（验证：运行命令后检查目录和 `git branch --list worktree-team-refactor+alice`）
- [ ] 不安全名称会被拒绝且返回明确原因（验证：分别运行包含 `..`、反斜杠、空段、Windows 保留名和超长名称的创建命令，期望全部失败并显示原因）
- [ ] 主仓库 dirty 时仍能创建 worktree，并提示未提交修改不会进入 worktree（验证：在主仓库制造未提交修改后运行 `/worktree create dirty-case`，期望成功且看到 warning）
- [ ] 已存在可恢复 worktree 时走快速恢复，不重新执行创建和初始化（验证：先创建 worktree，再重复创建同名 worktree，期望结果显示 restored 或等价状态，初始化 warning 不重复出现）
- [ ] 不可恢复目录会回退到正常创建流程或返回可诊断错误（验证：在目标目录放置无效 `.git` 指针后运行创建命令，期望系统不误判恢复成功）
- [ ] Git 子进程遇到需要交互输入时不会挂起（验证：使用需要认证的无效远端或受控 Git 替身触发失败，期望命令快速失败并返回错误）
- [ ] 新建 worktree 会复制主仓库的 `settings.local.json`（验证：主仓库放置该文件后创建 worktree，期望 worktree 中存在同名文件）
- [ ] 缺少 `settings.local.json` 只产生 warning，不阻断创建（验证：删除或临时隐藏该文件后创建 worktree，期望创建成功且有 warning）
- [ ] `.husky/` 存在时 worktree 的 hooksPath 指向主仓库 `.husky/`（验证：创建 `.husky/` 后创建 worktree，在 worktree 中运行 `git config core.hooksPath`，期望指向 `.husky`）
- [ ] 没有 `.husky/` 时 hooksPath 回退到 Git hooks 目录（验证：无 `.husky/` 时创建 worktree，检查 `git config core.hooksPath`）
- [ ] `settings.worktree.symlinkDirectories` 声明的目录会在 worktree 中创建软链（验证：配置一个存在的依赖目录后创建 worktree，期望 worktree 中对应路径为软链或平台等价链接）
- [ ] 依赖目录软链失败只记录 warning，不阻断创建（验证：配置一个不可链接路径，期望 worktree 创建成功且返回 warning）
- [ ] `.worktreeinclude` 能复制被忽略但需要的文件（验证：`.gitignore` 忽略 `.env`，`.worktreeinclude` 包含 `.env`，创建 worktree 后期望 worktree 中存在 `.env`）
- [ ] `.worktreeinclude` 未匹配的忽略文件不会复制（验证：创建另一个被忽略文件且不在 include 中，期望 worktree 中不存在该文件）
- [ ] `/worktree list` 显示名称、路径、分支、HEAD、当前会话标记和变更摘要（验证：创建并进入一个 worktree 后运行 `/worktree list`，观察输出字段完整）
- [ ] `/worktree enter my-feature` 会持久化当前 session（验证：运行 enter 后检查 `.lunacode/worktree_session.json` 存在且包含 worktree 路径）
- [ ] `/worktree enter` 不改变进程 cwd（验证：进入前后用测试命令或日志观察 Java 进程 cwd，期望保持原始仓库目录）
- [ ] 进入 worktree 后读文件发生在 worktree 路径下（验证：主仓库和 worktree 放同名不同内容文件，让 Agent 调用 ReadFile，期望读到 worktree 内容）
- [ ] 进入 worktree 后写文件发生在 worktree 路径下（验证：让 Agent 写入同名文件，期望 worktree 文件改变且主仓库文件不变）
- [ ] 进入 worktree 后 Bash 命令 cwd 是 worktree 路径（验证：让 Agent 调用 Bash 输出当前目录，期望路径为 worktree）
- [ ] 退出 worktree 后工具调用恢复到主仓库工作根（验证：运行 `/worktree exit` 后再次读取同名文件，期望读到主仓库内容）
- [ ] 启动时有效 session 会自动恢复（验证：进入 worktree 后重启 LunaCode，发起读取命令，期望仍落在 worktree）
- [ ] 启动时无效 session 会被清除并提示 warning（验证：删除 session 指向目录后重启，期望 session 文件被清除或置空且看到 warning）
- [ ] 删除 clean worktree 会删除 worktree 目录和 `worktree-` 分支（验证：运行 `/worktree remove clean-name` 后检查目录和分支都不存在）
- [ ] 删除有未提交修改的 worktree 且未确认丢弃时会拒绝（验证：在 worktree 修改文件后运行 remove，期望失败并提示 `discardChanges` 或等价确认）
- [ ] 删除有基线之后新增 commit 的 worktree 且未确认丢弃时会拒绝（验证：在 worktree commit 后运行 remove，期望失败并保留目录和分支）
- [ ] 显式确认丢弃后允许删除有变更的 worktree（验证：运行 `/worktree remove name --discardChanges`，期望目录和分支被删除）
- [ ] `AgentDefinition` 能解析 `isolation: worktree`（验证：加载带该字段的角色，期望诊断无错误且定义中隔离模式生效）
- [ ] 非法 `isolation` 值会跳过角色并产生诊断（验证：加载 `isolation: invalid` 的角色，期望该角色不可用且有 warning）
- [ ] 声明 `isolation: worktree` 的定义式子 Agent 会自动创建 `agent-a` 加 7 位 hex 名称的 worktree（验证：调用该角色，期望 `.lunacode/worktrees/agent-aXXXXXXX` 出现）
- [ ] 未声明隔离的定义式子 Agent 不会自动创建 worktree（验证：调用普通角色，期望 worktree 状态不新增自动记录）
- [ ] Fork 式子 Agent 不会自动创建 worktree（验证：不传 `subagent_type` 调用 Agent 工具，期望无自动 worktree）
- [ ] Hook 子 Agent 不会默认创建 worktree（验证：触发 Hook sub_agent 动作，期望无自动 worktree）
- [ ] 隔离子 Agent 的系统提示包含 worktree 路径说明（验证：测试 prompt 或日志中出现隔离路径和分支）
- [ ] 隔离子 Agent 修改文件后 worktree 被保留并通知主 Agent（验证：让子 Agent 写文件，完成后观察通知包含 worktree path 和 branch）
- [ ] 隔离子 Agent 只读分析后 worktree 自动清理（验证：让子 Agent 只读文件，完成后期望自动 worktree 目录和分支被删除）
- [ ] 后台过期清理只处理自动命名的 worktree（验证：准备 `agent-a1234567`、`wf_...` 和 `my-feature`，运行清理，期望只考虑前两类）
- [ ] 后台过期清理不会删除手动 worktree（验证：手动 worktree 超过 ttl 后运行清理，期望目录和分支仍存在）
- [ ] 后台过期清理不会删除有未提交修改或新增 commit 的自动 worktree（验证：自动 worktree 制造修改或 commit 后运行清理，期望保留）
- [ ] 快速恢复、环境初始化、删除保护和自动清理的 warning 都可观察（验证：触发各类 warning，期望命令输出、状态或日志能看到原因）

## 集成

- [ ] WorktreeManager 能完成创建、恢复、进入、退出、删除和清理完整生命周期（验证：运行 `DefaultWorktreeManagerTest`，期望通过）
- [ ] `/worktree` 命令通过现有 Slash Command 分发链路执行（验证：运行 `SlashCommandOrchestratorTest`，期望 `/worktree` 用例通过）
- [ ] 文件工具、搜索工具和 Bash 都使用同一个 effective workDir（验证：运行工具相关 worktree scope 测试，期望读写搜和命令 cwd 一致）
- [ ] 权限检查按 effective workDir 判断路径（验证：在 worktree 内写文件被允许或询问，在 worktree 外路径仍被拒绝）
- [ ] Prompt 环境上下文显示当前 worktree 路径和 Git 状态（验证：运行 prompt 测试，期望环境上下文包含 worktree 信息）
- [ ] 子 Agent worktree 隔离与后台任务通知集成正常（验证：运行 subagent 和 background formatter 测试，期望保留/清理信息正确回流）
- [ ] 不使用 worktree 的主 Agent、Fork 子 Agent、Hook 子 Agent 和普通定义式子 Agent 行为保持兼容（验证：运行现有相关测试，期望不回归）

## 编译与测试

- [ ] 项目编译无错误（验证：运行 `mvn -q -DskipTests compile`，期望退出码为 0）
- [ ] worktree 包单元测试通过（验证：运行 `mvn -q -Dtest='com.lunacode.worktree.*Test' test`，期望全部通过）
- [ ] 工具 cwd 相关测试通过（验证：运行 `mvn -q -Dtest=ToolExecutionScopeTest,ReadFileToolTest,WriteFileToolTest,EditFileToolTest,GlobToolTest,GrepToolTest,BashToolTest test`，期望全部通过）
- [ ] 子 Agent 隔离相关测试通过（验证：运行 `mvn -q -Dtest=FrontmatterAgentDefinitionParserTest,DefaultSubAgentServiceTest,DefaultSubAgentRunnerFactoryTest test`，期望全部通过）
- [ ] 命令和 orchestrator 集成测试通过（验证：运行 `mvn -q -Dtest=DefaultWorktreeCommandHandlerTest,SlashCommandOrchestratorTest,DefaultChatOrchestratorTest test`，期望全部通过）
- [ ] 全量单元测试通过（验证：运行 `mvn test`，期望全部通过）
- [ ] 项目可打包启动（验证：运行 `mvn package -DskipTests` 后执行 `run-LunaCode.bat` 或等价启动命令，期望正常进入 TUI）

## 端到端场景

- [ ] 场景 1：手动 worktree 流程能完整运行（验证：在 tmux 中启动 LunaCode，输入 `/worktree create e2e-manual`、`/worktree enter e2e-manual`，让 Agent 读取和写入临时文件，期望文件操作发生在 `.lunacode/worktrees/e2e-manual`）
- [ ] 场景 2：退出手动 worktree 后恢复主仓库（验证：在场景 1 后输入 `/worktree exit`，让 Agent 读取同名临时文件，期望读取主仓库版本）
- [ ] 场景 3：删除保护防止丢失修改（验证：在 `e2e-manual` 中制造未提交修改后运行 `/worktree remove e2e-manual`，期望拒绝删除；再加 `--discardChanges`，期望删除成功）
- [ ] 场景 4：定义式子 Agent 隔离写入（验证：创建带 `isolation: worktree` 的测试角色，让主 Agent 调用该子 Agent 修改临时文件，期望主仓库文件不被直接覆盖，通知中包含保留 worktree 路径和分支）
- [ ] 场景 5：定义式子 Agent 只读后自动清理（验证：让同一隔离角色只读分析文件，期望任务结束后对应 `agent-aXXXXXXX` worktree 被清理）
- [ ] 场景 6：重启恢复有效 session（验证：进入 worktree 后关闭并重启 LunaCode，再让 Agent 执行当前目录或读文件操作，期望仍在该 worktree）
- [ ] 场景 7：后台清理保守策略生效（验证：准备一个过期手动 worktree、一个过期 clean 自动 worktree、一个过期 dirty 自动 worktree，触发清理后期望只删除 clean 自动 worktree）
