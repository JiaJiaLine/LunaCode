# LunaCode 上下文管理 Checklist

> 每一项都通过运行代码、观察 TUI 行为、检查测试结果或对比产物来验证，聚焦系统行为而不是具体实现细节。

## 实现完整性

- [ ] 上下文配置能声明窗口大小，并按 `context_window_tokens - summary_output_reserve_tokens - auto_compact_margin_tokens` 计算自动压缩阈值，按自动阈值加 `force_compact_extra_tokens` 计算强制压缩阈值（验证：运行 `mvn -Dtest=ContextConfigTest test`，默认 200000/20000/13000/10000 得到 180000、167000、177000）
- [ ] 未显式配置 `context:` 时使用默认值，显式配置时能覆盖窗口、摘要输出预留、安全余量、工具结果阈值和恢复预算（验证：运行 `mvn -Dtest=ConfigLoaderTest test`，旧配置和新配置均加载成功）
- [ ] 单个工具结果超过 50,000 字符时会写入 `.lunacode/tmp/context/<session-id>/`，对话中只留下预览、完整路径和重新读取提示（验证：运行 `mvn -Dtest=LightweightToolResultExternalizerTest test`，检查外置文件存在且消息正文不含完整原文）
- [ ] 单条工具结果消息合计超过 200,000 字符时，会从最大的工具结果开始依次外置，直到剩余工具结果总量降到限制以内（验证：运行 `mvn -Dtest=LightweightToolResultExternalizerTest test`，检查外置顺序和剩余字符数）
- [ ] 每次 Provider 请求前先运行轻量预防，再进行自动压缩、强制压缩或普通请求判断（验证：运行 `mvn -Dtest=DefaultContextManagerTest,DefaultAgentLoopTest test`，观察调用顺序断言）
- [ ] 压缩前会生成完整 `session.jsonl`，摘要消息中包含该路径，并提示需要旧细节时读取会话记录或重新读取项目文件（验证：运行 `mvn -Dtest=ProjectSessionContextStoreTest,HistoryCompactorRewriteTest test`，检查摘要消息和文件内容）
- [ ] 超过自动压缩阈值时会自动生成结构化摘要，并把较早历史替换成一条 `user` 摘要消息（验证：运行 `mvn -Dtest=DefaultContextManagerTest,HistoryCompactorRewriteTest test`，检查重写后的消息列表）
- [ ] 普通自动摘要连续失败 3 次后进入熔断，不再重复普通自动压缩，并产生中文提示（验证：运行 `mvn -Dtest=DefaultContextManagerTest test`，连续模拟失败并检查状态）
- [ ] 超过强制压缩线时，即使普通自动压缩已熔断，仍会执行一次强制压缩（验证：运行 `mvn -Dtest=DefaultContextManagerTest test`，检查强制触发不受熔断阻止）
- [ ] 强制压缩重试后仍失败时停止当前请求，不继续调用 Provider，并提示用户手动处理上下文（验证：运行 `mvn -Dtest=DefaultAgentLoopTest,DefaultContextManagerTest test`，检查 Provider 未被调用）
- [ ] 输入 `/compact` 会触发本地手动压缩，不写入普通对话历史，也不发送给模型自动回复（验证：运行 `mvn -Dtest=DefaultChatOrchestratorCompactTest test`，检查 AgentLoop 未收到普通请求）
- [ ] 手动压缩在未超过自动阈值时也会尝试压缩较早历史，并在 TUI 显示覆盖消息数、外置结果数、恢复文件数或失败原因（验证：运行 `mvn -Dtest=DefaultChatOrchestratorCompactTest,DefaultContextManagerTest test`，检查结果消息）
- [ ] 压缩后保留的近期原文覆盖约 10,000 token 或至少 5 条消息中更大的范围（验证：运行 `mvn -Dtest=HistoryCompactorBoundaryTest test`，分别用短消息和长消息样本断言边界）
- [ ] 压缩边界不会切断 `tool_use` 和 `tool_result` 配对，必要时会向前扩展保留范围（验证：运行 `mvn -Dtest=HistoryCompactorBoundaryTest test`，构造边界落在配对中间的样本）
- [ ] 近期原文消息在压缩后一字不改保留（验证：运行 `mvn -Dtest=HistoryCompactorRewriteTest test`，对比压缩前后的近期消息文本和 blocks）
- [ ] 摘要正文包含 9 个固定部分：主要请求和意图、关键技术概念、文件和代码段、错误和修复、问题解决过程、所有用户消息、待办任务、当前工作、可能的下一步（验证：运行 `mvn -Dtest=SummaryPromptBuilderTest,HistoryCompactorRewriteTest test`，检查标题完整）
- [ ] “所有用户消息”部分保留用户非工具结果消息原文，不摘要、不改写、不遮蔽（验证：运行 `mvn -Dtest=SummaryPromptBuilderTest test`，对比原始用户消息和摘要输入要求）
- [ ] 摘要 Prompt 明确禁止工具调用，要求先写分析草稿再写正式摘要，且压缩后的对话历史不包含草稿（验证：运行 `mvn -Dtest=SummaryPromptBuilderTest,SummaryResponseParserTest test`，检查 Prompt 和解析结果）
- [ ] 摘要消息包含压缩边界提示，明确要求需要具体代码、报错或工具细节时读取会话记录或项目文件，不能根据摘要脑补（验证：运行 `mvn -Dtest=HistoryCompactorRewriteTest test`，检查摘要消息文本）
- [ ] 多次压缩后历史中仍只保留一条最新摘要消息，旧摘要作为早期上下文参与新摘要（验证：运行 `mvn -Dtest=HistoryCompactorRewriteTest test`，连续压缩两次并检查摘要数量）
- [ ] 最近访问文件只来自成功的文件工具访问记录，最多恢复 5 个文件，每个文件最多约 5,000 token（验证：运行 `mvn -Dtest=RecentFileAccessTrackerTest,RestoredFileContextBuilderTest test`，检查失败工具和自然语言路径不会进入恢复列表）
- [ ] 压缩前使用过 Skill 时，压缩后的首次请求能重新获得已使用 Skill 定义，且总预算不超过 25,000 token（验证：运行 `mvn -Dtest=InMemoryUsedSkillRegistryTest,HistoryCompactorRewriteTest test`，检查预算截断和摘要附件）
- [ ] 压缩后的首次 Provider 请求仍携带完整可用工具声明，模型可以继续选择和调用工具（验证：运行 `mvn -Dtest=DefaultAgentLoopTest test`，检查压缩后构建的 PromptBundle 工具声明）
- [ ] 摘要请求出现 Prompt Too Long 时，会按 API 轮次丢弃最旧消息组重试，3 次后继续每轮丢弃 20% 消息组，直到成功或进入失败处理（验证：运行 `mvn -Dtest=PromptTooLongRetryPolicyTest,ProviderSummaryModelClientTest test`，检查重试轨迹）
- [ ] Token 估算会使用上一次 Provider usage 校准，并对新增消息按字符数近似估算，不依赖精确 tokenizer（验证：运行 `mvn -Dtest=ApproximateContextTokenEstimatorTest test`，检查 usage 锚定和增量估算）
- [ ] 完整会话记录和外置工具结果原文落盘；TUI 短提示和工具结果预览不会完整展示 API Key、Token 或 Authorization 等已知敏感值（验证：运行 `mvn -Dtest=ProjectSessionContextStoreTest,LightweightToolResultExternalizerTest test`，检查原文文件和遮蔽后的预览）
- [ ] 未触发压缩时，普通对话、工具调用、权限确认、MCP 工具调用和中文回复行为保持可用（验证：运行现有 Agent、权限、工具、MCP 相关测试，确认无回归）

## 集成检查

- [ ] 对话管理器能提供完整 blocks 快照、替换工具结果、重写压缩历史，同时保持现有 `snapshot()` 和 `toAPIFormat()` 行为不变（验证：运行 `mvn -Dtest=DefaultConversationManagerTest,DefaultConversationManagerCompactionTest test`）
- [ ] 会话临时目录、外置工具结果、`session.jsonl` 和压缩元数据都写入项目内 `.lunacode/tmp/context/<session-id>/`，并且 `.gitignore` 忽略 `.lunacode/tmp/`（验证：运行 `mvn -Dtest=ProjectSessionContextStoreTest test` 并运行 `git diff --check`）
- [ ] 摘要调用复用当前 Provider 和模型配置，不携带工具声明，不写入普通对话历史（验证：运行 `mvn -Dtest=ProviderSummaryModelClientTest test`，检查请求和 ConversationManager 状态）
- [ ] Agent Loop 在 Provider 请求前接入上下文预检，工具执行后记录最近访问文件，Provider usage 返回后校准估算器（验证：运行 `mvn -Dtest=DefaultAgentLoopTest,*Agent*Context* test`）
- [ ] Orchestrator 能处理 `/compact` 本地命令，并在忙碌、等待权限或等待用户问题时给出中文提示（验证：运行 `mvn -Dtest=DefaultChatOrchestratorCompactTest test`）
- [ ] 压缩开始、完成、失败事件能被 TUI 或状态层展示，不破坏已有状态展示（验证：运行 `mvn -Dtest=DefaultChatOrchestratorTest test`，人工观察事件文本为中文）
- [ ] 应用启动时能组装 ContextManager、会话存储、估算器、最近文件追踪和 Skill 注册表（验证：运行 `mvn -Dtest=LunaCodeApplicationTest test`）

## 编译与测试

- [ ] 新增和修改的上下文相关单元测试全部通过（验证：运行 `mvn -Dtest=*Context*,*Compaction*,*Compact* test`）
- [ ] Agent Loop 和 Orchestrator 集成测试全部通过（验证：运行 `mvn -Dtest=*Agent*Context*,*Orchestrator*Compact* test`）
- [ ] 项目全量测试通过（验证：运行 `mvn test`，期望 BUILD SUCCESS）
- [ ] 项目可打包（验证：运行 `mvn package -DskipTests`，期望 BUILD SUCCESS）
- [ ] Git 空白检查通过（验证：运行 `git diff --check`，期望无输出）

## 端到端场景

- [ ] tmux 场景 1：启动 LunaCode，让 Agent 读取多个文件并产生大工具结果，再输入 `/compact`，TUI 显示压缩完成，`.lunacode/tmp/context/<session-id>/` 下出现外置结果和 `session.jsonl`，近期原文仍在对话中保留（验证：在 tmux 中实际操作并记录 TUI 输出和文件路径）
- [ ] tmux 场景 2：使用小上下文窗口测试配置启动 LunaCode，持续对话到自动阈值，观察自动压缩触发；连续模拟摘要失败 3 次后普通自动压缩熔断；继续增长到强制线时仍触发强制压缩（验证：在 tmux 中观察中文状态提示和日志）
- [ ] tmux 场景 3：压缩后输入“继续刚才的任务”，模型能基于摘要和近期原文继续工作；当需要旧代码细节时，会读取 `session.jsonl` 或重新读取项目文件，而不是直接编造摘要中没有的代码（验证：在 tmux 中观察工具调用和回复内容）
- [ ] tmux 场景 4：未触发压缩的普通请求仍能正常走工具权限、工具调用和中文回复流程（验证：在 tmux 中输入一个小型真实开发请求，观察行为与压缩前一致）

## 验收记录

- [ ] 所有 checklist 条目执行后，记录通过项、失败项、命令输出摘要和 tmux 观察结果（验证：最终验收报告中包含实际证据，不只写“应该通过”）
