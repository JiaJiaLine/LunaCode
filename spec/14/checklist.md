# Agent Team 与 Coordinator Mode Checklist

> 每一项都需要通过运行代码、单元测试、集成测试或端到端观察来验证。Checklist 通过后，才认为 spec/14 的实现完成。

## 实现完整性

- [ ] `/team create <name>` 能创建长期小组，并在用户目录下按仓库身份和小组名持久化元数据、成员目录、任务文件、邮箱文件和名称注册表。（手动运行 `/team create`，检查持久化目录与当前团队状态）
- [ ] 不同 Git 仓库中创建同名团队不会互相覆盖或读取对方数据。（在两个临时仓库中创建同名团队，检查持久化路径和 teamId 不同）
- [ ] 成员记录包含 `name`、`agentId`、角色、运行后端、审批要求、worktree 路径和分支等信息。（创建成员后读取团队元数据验证字段）
- [ ] 团队成员默认使用 spec/13 的 worktree 隔离，分支名和目录遵守 worktree 安全规则，退出与删除复用变更保护。（创建成员、写入临时修改、尝试清理并观察保护行为）
- [ ] `Agent` 创建团队成员时，非空 `name` 会写入 `AgentNameRegistry` 的 `name -> agentId` 映射。（创建 alice，读取注册表验证映射）
- [ ] `AgentNameRegistry` 拒绝重复名称、非法名称和跨团队误解析。（重复创建同名成员、使用非法名称、跨团队发送消息验证失败）
- [ ] 成员后端选择支持同进程协程后端；终端窗格后端当前不可用时明确返回错误，不静默降级到同进程后端。（配置 terminal backend 并创建成员，观察明确错误）
- [ ] 指定 `agentType/subagent_type` 时走定义式路径，成员从空白上下文启动；省略 `agentType` 且 `FORK_SUBAGENT` 开启时走 Fork；省略且 flag 关闭时使用默认 `general-purpose agent`。（分别创建三类成员并检查启动上下文与 agent type）

## 协作工具与消息

- [ ] Lead 能创建、读取、列出、更新共享任务，任务字段包含状态、负责人、依赖、阻塞关系、更新时间和描述。（通过 `TaskCreate/TaskGet/TaskList/TaskUpdate` 验证）
- [ ] `TaskUpdate(addBlocks/addBlockedBy)` 能建立双向一致的有向依赖关系，删除或重复添加不会破坏任务图。（创建 T1/T2，分别用两个字段建立依赖并读取验证）
- [ ] 任务描述中的文本依赖约定会原样保留，系统不强制解析但成员能读到。（创建含“需要在 T1 完成后再开始”的任务并读取验证）
- [ ] `TaskList` 能区分 blocked、ready、assigned、claimable 等状态，成员不会误把被阻塞任务当作可认领任务。（创建依赖链后用成员身份查看列表）
- [ ] 多个成员同时认领同一个可认领任务时，只有一个 `claim` 成功，其余得到明确失败结果。（并发调用 `TaskUpdate claim=true` 验证原子性）
- [ ] 普通主入口和普通 SubAgent 看不到团队协作工具；Team Lead 能看到团队、任务、消息工具；团队成员只额外获得 `TaskCreate/TaskGet/TaskList/TaskUpdate/SendMessage`。（分别打印或断言工具集）
- [ ] `SendMessage` 发送纯文本时必须提供 5-10 词 `summary`，缺失或过长过短都会被拒绝。（调用 SendMessage 验证参数校验）
- [ ] `SendMessage(to="bob")` 和 `SendMessage(to="agent-...")` 路由到同一个邮箱，名称解析依赖 `AgentNameRegistry`。（同时用名称和 agentId 发送并检查 bob 邮箱）
- [ ] `SendMessage(to="*")` 会广播给所有队友，但不会给发送者重复投递。（创建三名成员后广播并检查邮箱）
- [ ] 目标成员已停止但有磁盘上下文时，发送消息会从磁盘恢复上下文并唤醒该成员继续处理。（让成员自然停下后发送消息，观察恢复与回复）
- [ ] `shutdown_request`、`shutdown_response`、`plan_approval_response` 等结构化消息按协议校验发送者、接收者和必填字段。（分别构造合法与非法消息验证）
- [ ] 邮箱写入使用锁文件保护，并支持拿不到锁重试、过期锁回收；并发写入不会丢消息或写坏 JSON/JSONL。（并发写入压力测试并重新解析邮箱）

## 审批、Coordinator 与收敛

- [ ] `planModeRequired=true` 的队员在任何修改操作前必须先向 Lead 发送计划，未获批时写文件或执行修改类操作会被拦截。（创建审批成员并尝试直接修改）
- [ ] Lead 用 `plan_approval_response approve/reject` 审批计划；批准后队员继承 Lead 当前权限模式，驳回后队员保留等待状态并能收到反馈。（分别测试 approve 和 reject）
- [ ] 成员自然完成后会标记 idle，并向 Lead 发送完成或空闲通知；Lead 后续发消息能继续复用该成员上下文。（执行短任务后检查成员状态与邮箱）
- [ ] Coordinator Mode 只有在 `COORDINATOR_MODE` feature flag 和 `LUNACODE_COORDINATOR_MODE` 环境变量同时开启时才进入，任意一把锁关闭都不生效。（四种组合测试）
- [ ] Coordinator Mode 下 Lead 没有写文件、编辑文件等代码修改工具，只保留 `Agent/SendMessage/Task*/Team*/ReadFile/Glob/Grep/Bash` 等允许工具。（进入模式后断言工具集）
- [ ] Coordinator 系统提示词要求 Lead 亲自 synthesis，不把理解和决策完全委托给队员；生成的后续指令应包含具体文件、问题和期望修改。（模拟研究任务并检查 Lead 给执行成员的提示）
- [ ] Lead 通过 Bash 执行 git merge、通过 ReadFile 检查冲突文件；可机械解决的冲突能提交，无法判断的逻辑冲突会 `git merge --abort` 并向用户报告上下文。（构造两个 worktree 分支冲突验证）
- [ ] 合并完成或失败后不会自动删除含未提交修改、未推送 commit 或待 review 价值的成员 worktree。（合并前后检查 worktree 与分支保留策略）
- [ ] 删除团队或成员时会检查未完成任务、未读消息、未合并分支和 worktree 变更；有风险时默认拒绝，显式确认才允许清理。（运行删除命令验证保护）

## 测试与验收

- [ ] 团队模型、持久化、路径安全、AgentNameRegistry、任务依赖、邮箱锁、消息协议、审批流、Coordinator gate 和工具过滤都有对应单元测试。（运行相关单元测试类）
- [ ] 同进程成员运行、停止后恢复、消息唤醒、任务认领和 worktree 创建有集成测试覆盖。（运行相关集成测试类）
- [ ] `mvn test` 通过，或失败项均与本功能无关并有明确说明。（执行 `mvn test`）
- [ ] `mvn package -DskipTests` 通过，确认主程序可启动。（执行打包命令）
- [ ] tmux 端到端场景通过：启动 LunaCode，创建团队，拆分认证重构任务，创建 alice/bob，验证任务依赖、成员间 SendMessage、审批流、Coordinator Mode 工具限制、成员完成通知和 Lead 合并分支行为。（按 AGENTS.md 要求在 tmux 中真实对话验收）
- [ ] 对照本 checklist 所有项目，未通过项都有缺陷记录或明确的后续处理决定。（最终验收时逐项勾选）