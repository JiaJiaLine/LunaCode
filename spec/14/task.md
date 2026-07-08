# Agent Team 与 Coordinator Mode Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `src/main/java/com/lunacode/team/TeamRepositoryIdentity.java` | 仓库身份与 repo hash |
| 新建 | `src/main/java/com/lunacode/team/TeamDirectoryResolver.java` | 团队用户目录解析接口 |
| 新建 | `src/main/java/com/lunacode/team/DefaultTeamDirectoryResolver.java` | repoHash/teamName 持久化路径实现 |
| 新建 | `src/main/java/com/lunacode/team/TeamRecord.java` | 团队元数据 |
| 新建 | `src/main/java/com/lunacode/team/TeamSnapshot.java` | `/team status` 展示模型 |
| 新建 | `src/main/java/com/lunacode/team/TeamManager.java` | 团队生命周期接口 |
| 新建 | `src/main/java/com/lunacode/team/DefaultTeamManager.java` | 团队创建、使用、删除保护和状态汇总 |
| 新建 | `src/main/java/com/lunacode/team/TeamStore.java` | 团队元数据存储接口 |
| 新建 | `src/main/java/com/lunacode/team/JsonTeamStore.java` | `team.json` 和 `current_team.json` 读写 |
| 新建 | `src/main/java/com/lunacode/team/TeamNameValidator.java` | 团队名安全校验 |
| 新建 | `src/main/java/com/lunacode/team/TeamRuntimeContext.java` | 当前团队运行身份 |
| 新建 | `src/main/java/com/lunacode/team/TeamRuntimeContextHolder.java` | 团队上下文 thread-local |
| 新建 | `src/main/java/com/lunacode/team/TeamActorRole.java` | Lead/Member 角色枚举 |
| 新建 | `src/main/java/com/lunacode/team/TeamDeleteResult.java` | 团队删除保护结果 |
| 新建 | `src/main/java/com/lunacode/team/member/*` | 成员模型、存储、上下文、后端和服务 |
| 新建 | `src/main/java/com/lunacode/team/task/*` | 任务模型、依赖图、存储和任务板服务 |
| 新建 | `src/main/java/com/lunacode/team/mailbox/*` | 邮箱消息、锁、注册表和消息服务 |
| 新建 | `src/main/java/com/lunacode/team/approval/*` | 计划审批协议和修改 guard |
| 新建 | `src/main/java/com/lunacode/team/merge/*` | 合并计划和保护检查 |
| 新建 | `src/main/java/com/lunacode/team/tool/*` | `Task*`、`SendMessage`、`Team*` 工具和工具策略 |
| 新建 | `src/main/java/com/lunacode/coordinator/*` | Coordinator Mode 解析、状态和提示贡献 |
| 新建 | `src/main/java/com/lunacode/config/FeatureGate.java` | feature flag 枚举 |
| 新建 | `src/main/java/com/lunacode/config/FeatureGateService.java` | feature flag 读取接口 |
| 新建 | `src/main/java/com/lunacode/command/TeamCommandHandler.java` | `/team` 命令接口 |
| 新建 | `src/main/java/com/lunacode/command/DefaultTeamCommandHandler.java` | `/team` 命令解析和输出 |
| 修改 | `src/main/java/com/lunacode/runtime/AgentRunConfig.java` | 携带团队上下文和 coordinator 状态 |
| 修改 | `src/main/java/com/lunacode/tool/ToolExecutionContext.java` | 工具执行上下文携带团队上下文 |
| 修改 | `src/main/java/com/lunacode/tool/DefaultToolRegistry.java` | 团队工具可见性和 coordinator 过滤入口 |
| 修改 | `src/main/java/com/lunacode/tool/AgentTool.java` | 增加 `name`、团队成员派生字段和注册表写入 |
| 修改 | `src/main/java/com/lunacode/subagent/AgentToolRequest.java` | 扩展 Agent 工具请求字段 |
| 修改 | `src/main/java/com/lunacode/subagent/DefaultSubAgentService.java` | 支持团队成员派生路径 |
| 修改 | `src/main/java/com/lunacode/subagent/SubAgentLaunchRequest.java` | 携带团队成员运行上下文 |
| 修改 | `src/main/java/com/lunacode/subagent/DefaultSubAgentRunnerFactory.java` | 成员运行配置、上下文保存和恢复 |
| 修改 | `src/main/java/com/lunacode/command/BuiltinSlashCommands.java` | 注册 `/team` 命令 |
| 修改 | `src/main/java/com/lunacode/command/CommandRuntime.java` | 增加 team 命令运行入口 |
| 修改 | `src/main/java/com/lunacode/app/LunaCodeApplication.java` | 装配 Team/Coordinator 服务和工具 |
| 修改 | `src/main/java/com/lunacode/prompt/PromptContextBuilder.java` | 注入 coordinator prompt 和团队上下文提示 |
| 新建/修改 | `src/test/java/com/lunacode/team/**` | 团队、任务、邮箱、成员服务测试 |
| 新建/修改 | `src/test/java/com/lunacode/coordinator/**` | Coordinator Mode 测试 |
| 新建/修改 | `src/test/java/com/lunacode/tool/**` | 团队工具和 AgentTool 扩展测试 |
| 新建/修改 | `src/test/java/com/lunacode/command/**` | `/team` 命令测试 |
| 新建/修改 | `src/test/java/com/lunacode/integration/**` | 团队协作集成测试 |

## T1: 创建团队基础模型

**文件：** `src/main/java/com/lunacode/team/TeamRepositoryIdentity.java`、`TeamRecord.java`、`TeamSnapshot.java`、`TeamActorRole.java`、`TeamDeleteResult.java`、`TeamRuntimeContext.java`
**依赖：** 无
**步骤：**
1. 新建 `team` 包。
2. 按 plan 定义团队、仓库身份、团队快照、运行角色和删除结果 record/enum。
3. 在 record 构造器中规范化路径、空字符串、Optional 和集合。
4. 保证 `TeamRuntimeContext` 能表达 Lead 和成员两种调用者。

**验证：** 运行 `mvn -q -DskipTests compile`，期望编译通过。

## T2: 实现团队目录解析和团队名校验

**文件：** `TeamDirectoryResolver.java`、`DefaultTeamDirectoryResolver.java`、`TeamNameValidator.java`、`src/test/java/com/lunacode/team/DefaultTeamDirectoryResolverTest.java`
**依赖：** T1
**步骤：**
1. 实现 repoRoot 规范化和 remote URL 可选读取。
2. 生成稳定 `identityHash`，目录布局为用户目录下 `teams/<repoHash>/<teamName>/`。
3. 实现团队名字符集、长度、`.`/`..`、路径分隔符和 Windows 保留名校验。
4. 编写不同仓库同名团队目录互不冲突测试。

**验证：** 运行 `mvn -q -Dtest=DefaultTeamDirectoryResolverTest test`，期望全部通过。

## T3: 实现团队元数据存储

**文件：** `TeamStore.java`、`JsonTeamStore.java`、`src/test/java/com/lunacode/team/JsonTeamStoreTest.java`
**依赖：** T1、T2
**步骤：**
1. 实现 `team.json` 读写。
2. 实现仓库级 `current_team.json` 读写。
3. 写入时创建父目录并使用临时文件原子替换。
4. 读取缺失文件时返回空状态，读取坏 JSON 时返回可诊断错误。
5. 补充 round-trip、current team、坏 JSON 测试。

**验证：** 运行 `mvn -q -Dtest=JsonTeamStoreTest test`，期望全部通过。

## T4: 创建成员模型和成员存储

**文件：** `src/main/java/com/lunacode/team/member/TeamMemberRecord.java`、`TeamMemberStatus.java`、`TeamMemberRole.java`、`TeamMemberBackendType.java`、`TeamMemberLaunchMode.java`、`TeamMemberCreateRequest.java`、`TeamMemberDispatchRequest.java`、`TeamMemberStore.java`、`JsonTeamMemberStore.java`、`src/test/java/com/lunacode/team/member/JsonTeamMemberStoreTest.java`
**依赖：** T1、T3
**步骤：**
1. 定义成员 record，包含 `name`、`agentId`、角色、后端、启动模式、worktree、审批和上下文位置。
2. 定义创建和派发请求对象。
3. 实现 `members/<name>.json` 持久化。
4. 写入时校验成员名和 agentId 均非空，且不覆盖不同成员。
5. 测试成员读写、状态更新、缺失文件和重复成员场景。

**验证：** 运行 `mvn -q -Dtest=JsonTeamMemberStoreTest test`，期望全部通过。

## T5: 实现成员上下文存储

**文件：** `TeamMemberContextStore.java`、`JsonTeamMemberContextStore.java`、`src/test/java/com/lunacode/team/member/JsonTeamMemberContextStoreTest.java`
**依赖：** T4
**步骤：**
1. 定义可保存成员对话快照、最后摘要、当前任务和更新时间的上下文格式。
2. 实现 `contexts/<member>.json` 读写。
3. 支持空上下文、坏 JSON 诊断和原子写入。
4. 编写保存后恢复同一成员上下文的测试。

**验证：** 运行 `mvn -q -Dtest=JsonTeamMemberContextStoreTest test`，期望全部通过。

## T6: 实现 AgentNameRegistry

**文件：** `src/main/java/com/lunacode/team/mailbox/AgentNameRegistry.java`、`JsonAgentNameRegistry.java`、`src/test/java/com/lunacode/team/mailbox/JsonAgentNameRegistryTest.java`
**依赖：** T4
**步骤：**
1. 定义 `register(team, name, agentId, member)`、`resolve(nameOrAgentId)`、`agentIdForName(name)` 和 `unregister`。
2. 持久化 `registry.json`，同时保存 name -> agentId 和 agentId -> member 元数据引用。
3. 保证同名不同 agentId、同 agentId 不同名都返回明确错误。
4. 测试名称路由、Agent ID 路由、重复注册和注销。

**验证：** 运行 `mvn -q -Dtest=JsonAgentNameRegistryTest test`，期望全部通过。

## T7: 创建任务模型和任务存储

**文件：** `src/main/java/com/lunacode/team/task/TeamTaskRecord.java`、`TeamTaskStatus.java`、`TeamTaskView.java`、`TaskCreateRequest.java`、`TaskUpdatePatch.java`、`TaskListFilter.java`、`TeamTaskStore.java`、`JsonTeamTaskStore.java`、`src/test/java/com/lunacode/team/task/JsonTeamTaskStoreTest.java`
**依赖：** T3
**步骤：**
1. 定义任务 record、状态枚举、列表视图和请求对象。
2. 实现 `tasks.json` 读写。
3. 实现 `tasks.lock` 锁文件、重试和过期锁恢复。
4. 测试任务 round-trip、锁重试、过期锁恢复和坏 JSON 诊断。

**验证：** 运行 `mvn -q -Dtest=JsonTeamTaskStoreTest test`，期望全部通过。

## T8: 实现任务依赖图

**文件：** `TaskDependencyGraph.java`、`src/test/java/com/lunacode/team/task/TaskDependencyGraphTest.java`
**依赖：** T7
**步骤：**
1. 实现 `addBlocks` 与 `addBlockedBy` 的双向同步。
2. 拒绝任务依赖自身。
3. 检测简单环路并拒绝写入。
4. 根据依赖完成情况计算 blocked/ready 视图。
5. 测试双向同步、环路拒绝、文本依赖不参与强制阻塞。

**验证：** 运行 `mvn -q -Dtest=TaskDependencyGraphTest test`，期望全部通过。

## T9: 实现任务板服务

**文件：** `TeamTaskBoardService.java`、`DefaultTeamTaskBoardService.java`、`src/test/java/com/lunacode/team/task/DefaultTeamTaskBoardServiceTest.java`
**依赖：** T7、T8
**步骤：**
1. 实现任务创建、读取、列表和更新。
2. `TaskCreate` 自动生成稳定任务 ID。
3. `TaskUpdate` 支持 `assignee`、状态、`addBlocks`、`addBlockedBy`、结果摘要。
4. 实现 `claim=true` 原子认领，未分配且未阻塞时才成功。
5. 测试 Lead 分配、成员认领、并发认领只有一个成功、阻塞任务不可认领。

**验证：** 运行 `mvn -q -Dtest=DefaultTeamTaskBoardServiceTest test`，期望全部通过。

## T10: 实现邮箱消息模型和锁策略

**文件：** `MailboxMessage.java`、`MailboxMessageType.java`、`SendMessageRequest.java`、`MailboxLockPolicy.java`、`MailboxStore.java`、`JsonlMailboxStore.java`、`src/test/java/com/lunacode/team/mailbox/JsonlMailboxStoreTest.java`
**依赖：** T6
**步骤：**
1. 定义消息 record、消息类型、发送请求和锁策略。
2. 实现 append-only JSONL 邮箱读写。
3. 写入时补 `id`、`timestamp`、`read=false`。
4. 使用 `<agentId>.lock` 保护并发追加。
5. 测试追加顺序、未读过滤、标记已读、锁重试和过期锁恢复。

**验证：** 运行 `mvn -q -Dtest=JsonlMailboxStoreTest test`，期望全部通过。

## T11: 实现 SendMessage 路由和协议校验

**文件：** `MailboxService.java`、`DefaultMailboxService.java`、`src/test/java/com/lunacode/team/mailbox/DefaultMailboxServiceTest.java`
**依赖：** T6、T10
**步骤：**
1. 实现 `to` 为队友名称时通过 `AgentNameRegistry` 解析 Agent ID。
2. 实现 `to` 为 Agent ID 时直接解析到同一成员。
3. 实现 `to="*"` 展开为所有队友并汇总结果。
4. `TEXT` 消息强制校验 5-10 词 `summary`。
5. 校验 `shutdown_response` 只能发给 Lead。
6. 校验 `plan_approval_response` 只能由 Lead 发送。
7. 目标成员 `IDLE` 或 `STOPPED` 且上下文存在时，调用后端 wake。
8. 测试名称/ID 同路由、广播、summary 缺失拒绝、协议权限拒绝、wake 触发。

**验证：** 运行 `mvn -q -Dtest=DefaultMailboxServiceTest test`，期望全部通过。

## T12: 实现计划审批服务和修改 guard

**文件：** `src/main/java/com/lunacode/team/approval/PlanApprovalService.java`、`DefaultPlanApprovalService.java`、`PlanApprovalRequest.java`、`PlanApprovalDecision.java`、`TeamModificationGuard.java`、`src/test/java/com/lunacode/team/approval/DefaultPlanApprovalServiceTest.java`
**依赖：** T4、T9、T11
**步骤：**
1. 成员 `planModeRequired=true` 且未批准时，修改 guard 返回拒绝。
2. 生成 `PLAN_APPROVAL_REQUEST` 消息给 Lead。
3. 处理 `PLAN_APPROVAL_RESPONSE`，记录 approve/reject、feedback 和 Lead permission mode。
4. 批准后允许对应任务的修改操作，驳回后保持等待状态。
5. 测试批准、驳回、非 Lead 回复、跨任务审批不串用。

**验证：** 运行 `mvn -q -Dtest=DefaultPlanApprovalServiceTest test`，期望全部通过。

## T13: 实现成员后端抽象和选择器

**文件：** `TeamMemberBackend.java`、`TeamMemberBackendSelector.java`、`SameProcessTeamMemberBackend.java`、`TerminalPaneTeamMemberBackend.java`、`src/test/java/com/lunacode/team/member/TeamMemberBackendSelectorTest.java`
**依赖：** T4、T5
**步骤：**
1. 定义后端 `probe()`、`launch()` 和 `wake()`。
2. `SameProcessTeamMemberBackend` 先接入可测试的假 runner。
3. `TerminalPaneTeamMemberBackend` 第一版返回 `NOT_IMPLEMENTED` 或 `UNAVAILABLE`。
4. 选择器按请求后端选择，失败时返回明确错误，不降级。
5. 测试 same-process 可用、terminal 不可用、请求 terminal 不降级。

**验证：** 运行 `mvn -q -Dtest=TeamMemberBackendSelectorTest test`，期望全部通过。

## T14: 实现团队成员服务

**文件：** `TeamMemberService.java`、`DefaultTeamMemberService.java`、`src/test/java/com/lunacode/team/member/DefaultTeamMemberServiceTest.java`
**依赖：** T4、T5、T6、T13、spec/13 的 `WorktreeManager`
**步骤：**
1. `addMember` 校验成员名，生成 agentId。
2. 为成员创建 worktree，并记录路径和分支。
3. 调用 `AgentNameRegistry.register(name, agentId, member)`。
4. 根据 `agentType` 和 `FORK_SUBAGENT` feature flag 决定 launch mode。
5. `spawnOrResume` 加载上下文，追加任务或消息提示，交给后端运行。
6. `markIdle` 保存摘要、状态和上下文位置。
7. `stopMember` 更新状态并保留 worktree。
8. 测试添加成员、注册表写入、worktree 创建、Fork/general-purpose 分支、恢复空闲成员。

**验证：** 运行 `mvn -q -Dtest=DefaultTeamMemberServiceTest test`，期望全部通过。

## T15: 实现 TeamManager 和删除保护

**文件：** `TeamManager.java`、`DefaultTeamManager.java`、`src/test/java/com/lunacode/team/DefaultTeamManagerTest.java`
**依赖：** T3、T4、T7、T10、T14
**步骤：**
1. 实现创建团队、列出团队、切换当前团队和状态快照。
2. 创建团队时初始化 `members/`、`mailboxes/`、`contexts/` 等目录。
3. 删除团队前检查运行中成员、未合并分支、未提交修改、未读重要消息。
4. 默认拒绝危险删除，并返回阻塞原因。
5. force 删除只允许明确传参，并仍保留可诊断结果。
6. 测试同仓库团队、跨仓库同名团队、删除保护。

**验证：** 运行 `mvn -q -Dtest=DefaultTeamManagerTest test`，期望全部通过。

## T16: 实现合并辅助服务

**文件：** `src/main/java/com/lunacode/team/merge/TeamMergeService.java`、`DefaultTeamMergeService.java`、`TeamMergePlan.java`、`TeamMergeRequest.java`、`TeamMergeProtection.java`、`src/test/java/com/lunacode/team/merge/DefaultTeamMergeServiceTest.java`
**依赖：** T9、T14、T15
**步骤：**
1. 收集 DONE 任务、成员 worktree 分支和结果摘要。
2. 按依赖图和完成时间生成建议合并顺序。
3. 检查未完成任务、未提交成员修改、未读重要消息和缺失分支。
4. 输出 Git merge 命令建议，但不执行 merge。
5. 测试 clean 合并计划、阻塞保护、缺失成员分支诊断。

**验证：** 运行 `mvn -q -Dtest=DefaultTeamMergeServiceTest test`，期望全部通过。

## T17: 实现 FeatureGateService

**文件：** `src/main/java/com/lunacode/config/FeatureGate.java`、`FeatureGateService.java`、`DefaultFeatureGateService.java`、`src/test/java/com/lunacode/config/DefaultFeatureGateServiceTest.java`
**依赖：** 无
**步骤：**
1. 定义 `FORK_SUBAGENT` 和 `COORDINATOR_MODE`。
2. 从现有配置对象中读取 feature flag；配置缺失默认 false。
3. 提供测试用内存实现或 builder。
4. 测试默认关闭、显式开启和未知字段忽略。

**验证：** 运行 `mvn -q -Dtest=DefaultFeatureGateServiceTest test`，期望全部通过。

## T18: 实现 Coordinator Mode

**文件：** `src/main/java/com/lunacode/coordinator/CoordinatorModeResolver.java`、`DefaultCoordinatorModeResolver.java`、`CoordinatorModeState.java`、`CoordinatorPromptContributor.java`、`src/test/java/com/lunacode/coordinator/DefaultCoordinatorModeResolverTest.java`
**依赖：** T17
**步骤：**
1. 同时检查 `COORDINATOR_MODE` feature flag 和 `LUNACODE_COORDINATOR_MODE` truthy。
2. 定义 coordinator allowed tools 白名单。
3. 输出 `CoordinatorModeState` 供 `/status` 和测试使用。
4. 生成 coordinator 系统提示词，包含 spawn、收结果、synthesis、续写规格四阶段。
5. 测试单开不开、双开生效、白名单不包含 WriteFile/EditFile、提示词强调 Lead 自己 synthesis。

**验证：** 运行 `mvn -q -Dtest=DefaultCoordinatorModeResolverTest test`，期望全部通过。

## T19: 扩展运行配置和工具上下文

**文件：** `src/main/java/com/lunacode/runtime/AgentRunConfig.java`、`src/main/java/com/lunacode/tool/ToolExecutionContext.java`、`src/main/java/com/lunacode/team/TeamRuntimeContextHolder.java`、`src/test/java/com/lunacode/runtime/AgentRunConfigTest.java`、`src/test/java/com/lunacode/tool/ToolExecutionContextTest.java`
**依赖：** T1、T18
**步骤：**
1. 给 `AgentRunConfig` 增加可选 `TeamRuntimeContext` 和 `CoordinatorModeState`。
2. 增加 with 方法，保持现有构造器兼容。
3. 给 `ToolExecutionContext` 增加可选团队上下文。
4. `TeamRuntimeContextHolder` 在工具执行期间提供兜底读取。
5. 测试默认空上下文、Lead 上下文、成员上下文、coordinator 状态传播。

**验证：** 运行 `mvn -q -Dtest=AgentRunConfigTest,ToolExecutionContextTest test`，期望全部通过。

## T20: 实现团队工具策略过滤

**文件：** `src/main/java/com/lunacode/team/tool/TeamToolPolicyResolver.java`、`src/main/java/com/lunacode/tool/DefaultToolRegistry.java`、`src/test/java/com/lunacode/team/tool/TeamToolPolicyResolverTest.java`、`src/test/java/com/lunacode/tool/ToolRegistryTest.java`
**依赖：** T18、T19
**步骤：**
1. 没有团队上下文时过滤所有团队协作工具。
2. Lead 上下文允许团队管理、任务和消息工具。
3. Member 上下文只额外允许 `TaskCreate`、`TaskGet`、`TaskList`、`TaskUpdate`、`SendMessage`。
4. Coordinator Mode 生效时再叠加 coordinator 白名单。
5. 执行层也检查工具是否允许，避免仅声明层过滤。
6. 测试普通 Agent、Lead、Member、Coordinator 四种声明结果。

**验证：** 运行 `mvn -q -Dtest=TeamToolPolicyResolverTest,ToolRegistryTest test`，期望全部通过。

## T21: 实现任务协作工具

**文件：** `src/main/java/com/lunacode/team/tool/TaskCreateTool.java`、`TaskGetTool.java`、`TaskListTool.java`、`TaskUpdateTool.java`、`src/test/java/com/lunacode/team/tool/TaskToolsTest.java`
**依赖：** T9、T19、T20
**步骤：**
1. 定义四个工具的 input schema。
2. 执行时读取 `TeamRuntimeContext`，缺失则返回工具错误。
3. `TaskCreate` 支持标题、描述、assignee。
4. `TaskList` 返回 blocked/ready/assignee/claimable 视图。
5. `TaskUpdate` 支持状态、assignee、claim、addBlocks/addBlockedBy。
6. 测试工具输入校验、Lead 创建、成员列表、依赖更新、成员认领。

**验证：** 运行 `mvn -q -Dtest=TaskToolsTest test`，期望全部通过。

## T22: 实现 SendMessage 工具

**文件：** `src/main/java/com/lunacode/team/tool/SendMessageTool.java`、`src/test/java/com/lunacode/team/tool/SendMessageToolTest.java`
**依赖：** T11、T19、T20
**步骤：**
1. 定义 `to`、`type`、`summary`、`message`、`payload` input schema。
2. TEXT 消息要求 `summary` 和 `message`。
3. 协议消息按 type 要求 payload。
4. 工具调用 `MailboxService.send` 或广播路由。
5. 返回每个目标写入、失败和 wake 结果。
6. 测试 summary 缺失、名称路由、Agent ID 路由、广播、协议权限错误。

**验证：** 运行 `mvn -q -Dtest=SendMessageToolTest test`，期望全部通过。

## T23: 实现 TeamCreate 和 TeamDelete 工具

**文件：** `src/main/java/com/lunacode/team/tool/TeamCreateTool.java`、`TeamDeleteTool.java`、`src/test/java/com/lunacode/team/tool/TeamToolsTest.java`
**依赖：** T15、T19、T20
**步骤：**
1. `TeamCreate` 仅允许 Lead 或 coordinator 上下文调用。
2. `TeamDelete` 调用删除保护，默认不 force。
3. 工具结果包含团队目录、当前团队和阻塞原因。
4. 成员上下文调用时返回拒绝。
5. 测试创建、删除保护、成员拒绝。

**验证：** 运行 `mvn -q -Dtest=TeamToolsTest test`，期望全部通过。

## T24: 扩展 AgentTool 和 AgentToolRequest

**文件：** `src/main/java/com/lunacode/tool/AgentTool.java`、`src/main/java/com/lunacode/subagent/AgentToolRequest.java`、`src/test/java/com/lunacode/tool/AgentToolTest.java`
**依赖：** T14、T17、T19、T20
**步骤：**
1. 给 Agent 工具 schema 增加 `name`、`team_member`、`task_id`、`backend`、`planModeRequired`。
2. 没有团队字段时保持现有子 Agent 行为。
3. 团队字段存在但当前不是 Team Lead 时拒绝。
4. 创建团队成员时要求 `name` 等于成员名，生成 agentId 并写入 `AgentNameRegistry`。
5. 指定 `subagent_type` 时走定义式角色。
6. 未指定 `subagent_type` 时按 `FORK_SUBAGENT` 决定 Fork 或默认 general-purpose。
7. 测试现有 Agent 行为不变、团队成员注册、非 Lead 拒绝、Fork flag 分支。

**验证：** 运行 `mvn -q -Dtest=AgentToolTest test`，期望全部通过。

## T25: 接入团队成员同进程运行

**文件：** `src/main/java/com/lunacode/team/member/SameProcessTeamMemberBackend.java`、`src/main/java/com/lunacode/subagent/SubAgentLaunchRequest.java`、`src/main/java/com/lunacode/subagent/DefaultSubAgentService.java`、`src/main/java/com/lunacode/subagent/DefaultSubAgentRunnerFactory.java`、`src/test/java/com/lunacode/team/member/SameProcessTeamMemberBackendTest.java`
**依赖：** T14、T19、T24
**步骤：**
1. `SameProcessTeamMemberBackend.launch` 构建成员运行请求。
2. 成员 `AgentRunConfig` 携带 `TeamRuntimeContext(role=MEMBER)`。
3. 成员工具策略包含固定协作工具集。
4. 启动时使用成员 worktree 作为 workDir。
5. 完成后保存成员上下文、更新成员状态、发送完成/空闲通知。
6. wake 时加载上下文并追加新消息提示。
7. 测试成员 workDir、工具策略、完成后 idle、wake 恢复。

**验证：** 运行 `mvn -q -Dtest=SameProcessTeamMemberBackendTest test`，期望全部通过。

## T26: 接入 `/team` 命令族

**文件：** `src/main/java/com/lunacode/command/TeamCommandHandler.java`、`DefaultTeamCommandHandler.java`、`BuiltinSlashCommands.java`、`CommandRuntime.java`、`src/test/java/com/lunacode/command/DefaultTeamCommandHandlerTest.java`、`BuiltinSlashCommandsTest.java`
**依赖：** T15、T16
**步骤：**
1. 注册 `/team` 和别名可选项。
2. 实现 `create`、`delete`、`list`、`use`、`status`。
3. 实现 `member add/list/stop`。
4. 实现 `task create/list/update/get`。
5. 实现 `message send/read`。
6. 实现 `merge` 输出合并计划。
7. 命令结果只输出 UI，不写入模型历史。
8. 测试 help、create/use/status/member/task/message/merge。

**验证：** 运行 `mvn -q -Dtest=DefaultTeamCommandHandlerTest,BuiltinSlashCommandsTest test`，期望全部通过。

## T27: 注入 Coordinator Prompt 和团队上下文提示

**文件：** `src/main/java/com/lunacode/prompt/PromptContextBuilder.java`、`src/main/java/com/lunacode/coordinator/CoordinatorPromptContributor.java`、`src/test/java/com/lunacode/prompt/PromptContextBuilderTest.java`
**依赖：** T18、T19
**步骤：**
1. Coordinator Mode 生效时注入专用提示。
2. 提示包含 spawn、收结果、synthesis、续写规格四阶段。
3. 提示明确 Lead 不得把理解和综合职责委托给成员。
4. 团队上下文存在时提示当前团队、当前身份和成员名。
5. 测试普通模式无 coordinator 提示，双开模式有提示，成员上下文包含团队身份。

**验证：** 运行 `mvn -q -Dtest=PromptContextBuilderTest test`，期望全部通过。

## T28: 应用装配和工具注册

**文件：** `src/main/java/com/lunacode/app/LunaCodeApplication.java`、`src/main/java/com/lunacode/tool/DefaultToolRegistry.java`、`src/test/java/com/lunacode/app/LunaCodeApplicationTest.java`
**依赖：** T15、T18、T20、T21、T22、T23、T24、T26
**步骤：**
1. 创建 TeamStore、TeamManager、TaskBoard、Mailbox、MemberService、MergeService。
2. 创建 CoordinatorModeResolver 和 FeatureGateService。
3. 注册团队协作工具和 `/team` 命令。
4. 把 TeamManager 注入 AgentTool 和 CommandRuntime。
5. 确认无团队上下文时工具不出现在模型声明。
6. 测试应用装配不破坏现有 `/worktree`、`Agent` 和普通工具注册。

**验证：** 运行 `mvn -q -Dtest=LunaCodeApplicationTest,ToolRegistryTest test`，期望全部通过。

## T29: 补充团队集成测试

**文件：** `src/test/java/com/lunacode/integration/AgentTeamIntegrationTest.java`
**依赖：** T21-T28
**步骤：**
1. 在临时仓库中创建团队。
2. 创建任务 A/B/C/D，并设置 C blockedBy A、D blockedBy B。
3. 添加 alice 和 bob 两个成员，断言都创建 worktree 和注册表映射。
4. alice 认领一个未阻塞任务，bob 认领另一个未阻塞任务。
5. alice 向 bob 发送 TEXT 消息，断言 summary 和邮箱预览存在。
6. bob 使用 Agent ID 回复 alice，断言路由到同一邮箱。
7. 触发 plan approval 请求与批准，断言成员恢复执行。
8. 生成 merge plan，断言包含成员分支和任务摘要。

**验证：** 运行 `mvn -q -Dtest=AgentTeamIntegrationTest test`，期望全部通过。

## T30: 补充 Coordinator Mode 集成测试

**文件：** `src/test/java/com/lunacode/integration/CoordinatorModeIntegrationTest.java`
**依赖：** T18、T20、T27、T28
**步骤：**
1. feature flag 关闭、env 开启时构建主 Agent，断言不进入 coordinator。
2. feature flag 开启、env 关闭时构建主 Agent，断言不进入 coordinator。
3. 双开时构建主 Agent，断言工具声明不包含 `WriteFile` 和 `EditFile`。
4. 双开时断言仍包含 `Agent`、`SendMessage`、`TaskCreate`、`TaskGet`、`TaskList`、`TaskUpdate`、`TeamCreate`、`TeamDelete`、`ReadFile`、`Glob`、`Grep`、`Bash`。
5. 断言 prompt 中包含 synthesis 规则。

**验证：** 运行 `mvn -q -Dtest=CoordinatorModeIntegrationTest test`，期望全部通过。

## T31: 全量编译和单元测试

**文件：** 全项目
**依赖：** T1-T30
**步骤：**
1. 运行编译。
2. 运行全量单元测试。
3. 修复所有编译错误和测试失败。
4. 确认普通主入口、普通子 Agent、Fork 子 Agent、Hook 子 Agent、手动 worktree 既有测试仍通过。

**验证：** 运行 `mvn test`，期望全部通过。

## T32: 打包和端到端测试准备

**文件：** `run-LunaCode.bat`、临时测试团队目录、临时测试角色定义
**依赖：** T31
**步骤：**
1. 运行 `mvn package -DskipTests`。
2. 准备一个临时测试仓库，避免污染真实代码。
3. 准备一个真实对话请求：创建团队、拆分两个任务、派生 alice/bob、发送消息、查看任务状态。
4. 准备一个 coordinator 模式请求，验证 Lead 只能调度和读取。
5. 准备一个合并计划请求，验证输出成员分支和建议顺序。

**验证：** `mvn package -DskipTests` 通过，且测试输入和临时目录准备完成。

## T33: tmux 端到端验收

**文件：** 运行环境、`spec/14/checklist.md`
**依赖：** T32、已批准的 checklist
**步骤：**
1. 在 tmux 中启动 LunaCode。
2. 输入 `/team create refactor-demo`，观察团队创建结果。
3. 输入真实对话请求，让 Lead 创建带依赖任务并派生 alice/bob。
4. 观察 alice/bob 的 worktree、任务状态和 `agentNameRegistry` 映射。
5. 让 alice 给 bob 发送带 summary 的消息，观察 bob 邮箱和 UI 预览。
6. 发送 `to="*"` 广播，观察所有队友邮箱写入。
7. 触发 `planModeRequired` 队员审批，观察请求、批准、权限传播和恢复执行。
8. 开启 coordinator 双锁后重启或新会话，观察 Lead 工具集中没有写文件工具但保留 Bash。
9. 让 Lead 生成并执行可合并成员分支的合并流程；如遇逻辑冲突，观察回滚和上报。
10. 对照 checklist.md 逐项验收。

**验证：** tmux 会话中能观察到团队创建、任务依赖、成员并行、消息路由、审批、coordinator 工具收窄和合并辅助行为；checklist 全部通过。

## 执行顺序

```text
T1 -> T2 -> T3
T1 -> T4 -> T5 -> T6
T3 -> T7 -> T8 -> T9
T6 -> T10 -> T11
T4 + T9 + T11 -> T12
T4 + T5 -> T13 -> T14
T3 + T7 + T10 + T14 -> T15 -> T16
T17 -> T18
T1 + T18 -> T19 -> T20
T9 + T20 -> T21
T11 + T20 -> T22
T15 + T20 -> T23
T14 + T17 + T19 -> T24 -> T25
T15 + T16 -> T26
T18 + T19 -> T27
T21 + T22 + T23 + T24 + T26 + T27 -> T28
T28 -> T29 -> T30 -> T31 -> T32 -> T33
```