# SubAgent 与后台任务 Checklist

> 每一项都必须通过运行命令、测试用例或端到端观察来验证；重点检查系统行为，不把验收绑定到某个内部实现细节。

## Agent 工具入口

- [ ] `Agent` 工具在模型可见工具清单中名称稳定，新增或删除角色定义文件后仍通过同一个工具入口调用子 Agent（验证：运行 `mvn test -Dtest=AgentToolTest`，并在端到端日志中确认工具名始终为 `Agent`）
- [ ] 调用 `Agent` 且不传 `subagent_type` 时走 Fork 路径，并立即返回 `async_launched` 和后台任务 id（验证：运行 `mvn test -Dtest=AgentToolTest`，或在 tmux Fork 场景中观察工具结果）
- [ ] 调用 `Agent` 且传入有效 `subagent_type` 时，系统启动对应定义式子 Agent（验证：运行 `mvn test -Dtest=DefaultSubAgentServiceTest,AgentToolTest`）
- [ ] 调用 `Agent` 且传入不存在的 `subagent_type` 时，工具返回包含原因的错误结果，主 Agent 不崩溃（验证：运行 `mvn test -Dtest=DefaultSubAgentServiceTest,AgentToolTest`）
- [ ] `Agent` 工具拒绝空白或缺失的 `task` 参数，并返回参数错误（验证：运行 `mvn test -Dtest=AgentToolTest`）
- [ ] `run_in_background: true` 用于定义式子 Agent 时，工具立即返回 `async_launched` 和任务 id，主对话可以继续输入（验证：运行 `mvn test -Dtest=DefaultSubAgentServiceTest,AgentToolTest`，并执行 tmux 定义式后台场景）

## 角色定义加载

- [ ] Markdown 加 YAML frontmatter 的角色定义能被解析，frontmatter 的 `name` 映射为 `agentType`，`description` 映射为 `whenToUse`（验证：运行 `mvn test -Dtest=FrontmatterAgentDefinitionParserTest,DefaultAgentDefinitionCatalogTest`）
- [ ] 角色定义支持 `tools`、`disallowedTools`、`model`、`maxTurns`、`permissionMode` 字段，Markdown 正文作为系统提示保留（验证：运行 `mvn test -Dtest=FrontmatterAgentDefinitionParserTest`）
- [ ] 每个有效角色定义都记录来源和文件路径，诊断信息能定位具体定义（验证：运行 `mvn test -Dtest=DefaultAgentDefinitionCatalogTest`）
- [ ] 项目级、用户级、内置、插件四类来源按项目级 > 用户级 > 内置 > 插件的顺序覆盖同名定义（验证：运行 `mvn test -Dtest=DefaultAgentDefinitionCatalogTest`）
- [ ] 删除高优先级同名定义后，下一优先级定义生效（验证：运行 `mvn test -Dtest=DefaultAgentDefinitionCatalogTest`）
- [ ] 缺少 `name` 或 `description` 的定义被跳过并产生可观察 warning，其他角色仍可用（验证：运行 `mvn test -Dtest=DefaultAgentDefinitionCatalogTest`，并检查启动日志或诊断快照）
- [ ] frontmatter 解析失败、字段类型非法、权限模式非法、模型别名缺失或工具字段非法时，单个坏定义被跳过，不影响应用启动（验证：运行 `mvn test -Dtest=FrontmatterAgentDefinitionParserTest,DefaultAgentDefinitionCatalogTest`）

## 子 Agent 运行隔离

- [ ] 定义式子 Agent 从空白对话启动，只包含角色系统提示和本次任务，不包含父对话历史（验证：运行 `mvn test -Dtest=DefaultSubAgentRunnerFactoryTest`）
- [ ] Fork 式子 Agent 复制父对话原始消息历史，保留消息顺序和内容（验证：运行 `mvn test -Dtest=DefaultSubAgentRunnerFactoryTest#forkCopiesParentHistory`）
- [ ] Fork 式子 Agent 继承父 Agent 当前可用工具集后，再应用后台安全过滤（验证：运行 `mvn test -Dtest=DefaultSubAgentRunnerFactoryTest#forkCopiesParentHistory,ToolPolicyResolverTest`）
- [ ] 子 Agent 的消息、工具结果、权限记录和中间事件不会写入主对话历史（验证：运行 `mvn test -Dtest=DefaultSubAgentRunnerFactoryTest,DefaultChatOrchestratorBackgroundTaskTest`，并在 tmux 场景中检查主历史）
- [ ] 子 Agent 的 token 消耗、工具调用次数和最近活动按任务独立统计，任务结束后不改变主 Agent 的运行状态（验证：运行 `mvn test -Dtest=ProgressTrackerTest,DefaultSubAgentRunnerFactoryTest#tracksProgress`）
- [ ] 子 Agent 在模型不再发起工具调用时结束，并把最终 assistant 输出作为子任务结果（验证：运行 `mvn test -Dtest=DefaultSubAgentRunnerFactoryTest`）

## 模型、权限、轮次和工具过滤

- [ ] `model: inherit` 或未设置模型时，定义式子 Agent 使用主 Agent 当前模型（验证：运行 `mvn test -Dtest=SubAgentModelResolverTest,DefaultSubAgentRunnerFactoryTest`）
- [ ] `model: sonnet`、`opus`、`haiku` 时，实际请求使用配置中对应别名映射的具体模型名（验证：运行 `mvn test -Dtest=SubAgentModelResolverTest`）
- [ ] 角色设置 `maxTurns` 后，子 Agent 达到上限会停止，并在结果或失败原因中写明达到最大轮次（验证：运行 `mvn test -Dtest=DefaultSubAgentRunnerFactoryTest`）
- [ ] 角色未设置 `maxTurns` 时，子 Agent 使用主运行配置的默认最大轮次（验证：运行 `mvn test -Dtest=DefaultSubAgentRunnerFactoryTest`）
- [ ] 子 Agent 需要用户确认权限时，工具调用被直接拒绝，不弹出用户确认，也不阻塞后台任务（验证：运行 `mvn test -Dtest=DenyingPermissionConfirmationBrokerTest,DefaultSubAgentRunnerFactoryTest`）
- [ ] 角色设置 `permissionMode` 时覆盖主 Agent 权限模式；未设置时继承主 Agent 权限模式（验证：运行 `mvn test -Dtest=FrontmatterAgentDefinitionParserTest,DefaultSubAgentRunnerFactoryTest`）
- [ ] 同时设置 `tools: [Read, Grep, Bash]` 和 `disallowedTools: [Bash]` 时，最终可见工具包含 `Read`、`Grep`，不包含 `Bash`（验证：运行 `mvn test -Dtest=ToolPolicyResolverTest`）
- [ ] 只设置 `disallowedTools: [Agent, Write, Edit]` 时，最终工具集从继承工具集中移除这些工具（验证：运行 `mvn test -Dtest=ToolPolicyResolverTest`）
- [ ] 后台 Agent 即使被角色显式允许，也看不到也无法执行 `Agent` 工具（验证：运行 `mvn test -Dtest=AgentToolRunnerTest,ToolPolicyResolverTest`）
- [ ] Fork 式子 Agent 尝试再次 Fork 时，调用被拒绝并返回可诊断原因（验证：运行 `mvn test -Dtest=AgentToolRunnerTest,AgentToolTest`）
- [ ] 后台 Agent 尝试 spawn 任意 Agent 时，调用被拒绝并返回可诊断原因（验证：运行 `mvn test -Dtest=AgentToolRunnerTest,AgentToolTest`）
- [ ] 被过滤掉的工具不会出现在子 Agent 工具声明中，也不能通过执行层绕过（验证：运行 `mvn test -Dtest=AgentToolRunnerTest,ToolPolicyResolverTest`）

## 后台任务生命周期

- [ ] 后台任务创建时生成唯一 id，状态为 `running`，并立即对调用方可见（验证：运行 `mvn test -Dtest=DefaultBackgroundTaskManagerTest`）
- [ ] 后台任务正常完成后状态变为 `completed`，记录结束时间、最终结果、token 消耗和工具调用次数（验证：运行 `mvn test -Dtest=DefaultBackgroundTaskManagerTest,ProgressTrackerTest`）
- [ ] 后台任务运行中抛出异常时状态变为 `failed`，失败原因可观察，主程序继续运行（验证：运行 `mvn test -Dtest=DefaultBackgroundTaskManagerTest`）
- [ ] 后台任务完成或失败后 listener 收到通知，主对话追加一条 `<task-notification>` assistant 消息（验证：运行 `mvn test -Dtest=DefaultBackgroundTaskManagerTest,DefaultChatOrchestratorBackgroundTaskTest`）
- [ ] `<task-notification>` 包含任务 id、状态、摘要和完整结果（验证：运行 `mvn test -Dtest=TaskNotificationFormatterTest,DefaultChatOrchestratorBackgroundTaskTest`）
- [ ] `<task-notification>` 不包含子 Agent 中间对话、工具调用转录或权限记录（验证：运行 `mvn test -Dtest=TaskNotificationFormatterTest`，并在 tmux 场景中检查主历史）
- [ ] 后台通知不会抢占用户当前输入，也不会自动触发主 Agent 新一轮回复（验证：运行 `mvn test -Dtest=DefaultChatOrchestratorBackgroundTaskTest`，并在 tmux 后台场景中观察）
- [ ] 前台定义式子 Agent 超过 `getAutoBackgroundMs()` 阈值后被自动接管为后台任务，并返回任务 id（验证：运行 `mvn test -Dtest=DefaultSubAgentServiceTest#autoBackgroundAfterTimeout`）
- [ ] 用户在前台子 Agent 运行时按 ESC，会把同一个运行实例切到后台，不会杀掉后重启（验证：运行 `mvn test -Dtest=DefaultChatOrchestratorBackgroundTaskTest#escapeAdoptsForegroundSubAgent`，并执行 tmux ESC 场景）
- [ ] 前台切后台后，后台任务继续消费原事件流，最终进入 `completed` 或 `failed` 状态（验证：运行 `mvn test -Dtest=DefaultForegroundSubAgentTrackerTest,DefaultBackgroundTaskManagerTest`）
- [ ] 没有前台子 Agent 时按 ESC，仍保留取消当前主 Agent run 的既有行为（验证：运行 `mvn test -Dtest=DefaultChatOrchestratorBackgroundTaskTest,LanternaLunaTuiTest`）

## Hook 与 Skill 兼容

- [ ] Hook 的 `sub_agent` 动作能真实启动后台子 Agent，不再只返回未实现占位结果（验证：运行 `mvn test -Dtest=RealSubAgentHookActionExecutorTest`，并执行 tmux Hook 场景）
- [ ] Hook 的 `sub_agent` 动作使用配置中的 `name` 作为子 Agent 类型、`prompt` 作为任务输入（验证：运行 `mvn test -Dtest=RealSubAgentHookActionExecutorTest`）
- [ ] Hook 指定不存在的子 Agent 时记录失败原因，Hook Runtime 和主 Agent 不被未处理异常打断（验证：运行 `mvn test -Dtest=RealSubAgentHookActionExecutorTest`）
- [ ] Hook 启动的子 Agent 完成后，通过同一条 `<task-notification>` 机制回流到主对话（验证：执行 tmux Hook 场景，观察主历史中出现通知）
- [ ] 现有 fork Skill 调用仍可使用，并保持主历史只回流简短结果的兼容行为（验证：运行现有 Skill 相关测试，并手动执行一个 fork Skill 场景）

## 构建与自动化测试

- [ ] 配置解析支持 `auto_background_ms` 和 `model_aliases`，缺省值可用（验证：运行 `mvn test -Dtest=ConfigLoaderTest`）
- [ ] 全量单元测试通过（验证：运行 `mvn test`，退出码为 0）
- [ ] 编译打包通过，没有缺失 import、泛型错误或装配错误（验证：运行 `mvn package -DskipTests`，退出码为 0）
- [ ] 应用启动装配成功，角色目录、后台任务管理器、`Agent` 工具和 Hook 子 Agent 执行器都被注册（验证：运行 `mvn test -Dtest=LunaCodeApplicationTest` 或 `mvn package -DskipTests`）

## tmux 端到端场景

- [ ] 在 tmux 中启动 LunaCode 后，定义 `.lunacode/agents/security-reviewer.md` 可以被加载且没有解析 warning（验证：观察启动日志或角色加载诊断）
- [ ] 输入“让 security-reviewer 检查当前项目中和权限相关的风险，后台运行。”时，主 Agent 调用 `Agent` 并传入 `subagent_type: security-reviewer` 与 `run_in_background: true`（验证：观察工具调用日志）
- [ ] 定义式后台场景中，主对话立即恢复可交互，并在任务完成后收到包含完整结果的 `<task-notification>`（验证：tmux 观察主界面）
- [ ] 输入“开一个子 Agent 总结我们刚才讨论过的实现风险。”时，主 Agent 调用 `Agent` 且不传 `subagent_type`，Fork 子 Agent 直接后台运行（验证：tmux 观察工具调用和返回结果）
- [ ] Fork 场景完成后，主对话收到 `<task-notification>`，且主历史没有子 Agent 中间对话转录（验证：tmux 观察主历史）
- [ ] 前台定义式子 Agent 运行期间按 ESC，主对话恢复输入，后台任务完成后通知回流（验证：tmux ESC 场景）
- [ ] 配置并触发一个 `sub_agent` Hook 后，Hook 日志显示后台任务已启动，任务完成后主对话收到 `<task-notification>`（验证：tmux Hook 场景）

## 边界与非目标

- [ ] 本章没有引入 worktree 文件隔离；子 Agent 与主 Agent 仍共享同一文件系统访问基础设施（验证：检查实现和端到端行为，确认没有创建独立 worktree）
- [ ] 本章没有实现多 Agent 团队编排、Agent 间互相协作或任务拆解调度（验证：检查用户可见命令和 `Agent` 工具行为，只存在单次子任务委派）
- [ ] 本章没有实现后台任务跨会话持久化；重启 LunaCode 后，旧进程内任务不要求可查询（验证：启动后台任务后重启应用，确认无恢复入口且无错误）
- [ ] 本章没有新增完整后台任务管理命令界面；第一版只提供启动状态、完成通知和结果回流（验证：检查用户可见命令和 TUI 行为）
- [ ] 子 Agent 权限交互没有弹出用户确认；需要确认的工具调用一律作为拒绝结果进入子任务流程（验证：运行 `mvn test -Dtest=DenyingPermissionConfirmationBrokerTest`，并在端到端中尝试需要确认的操作）

## 最终验收记录

- [ ] 对照 `spec/12/spec.md` 的 AC1-AC30，每一项都有单元测试、集成测试或 tmux 手工证据（验证：整理最终验收报告时逐条标注证据）
- [ ] 最终报告包含 `mvn test` 输出摘要、`mvn package -DskipTests` 输出摘要、tmux 端到端观察结果和未做事项说明（验证：最终回复中列出实际命令结果和端到端证据）
