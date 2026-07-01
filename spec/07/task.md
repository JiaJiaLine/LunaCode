# LunaCode 上下文管理 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `src/main/java/com/lunacode/config/ContextConfig.java` | 上下文压缩配置默认值与校验 |
| 新建 | `src/main/java/com/lunacode/config/ContextBudget.java` | 上下文预算阈值计算结果 |
| 修改 | `src/main/java/com/lunacode/config/ProviderConfig.java` | 挂载 `ContextConfig` |
| 修改 | `src/main/java/com/lunacode/config/ConfigLoader.java` | 解析 `context:` 配置块 |
| 修改 | `config.example.yaml` | 增加上下文管理配置示例 |
| 修改 | `.gitignore` | 忽略 `.lunacode/tmp/` |
| 新建 | `src/main/java/com/lunacode/conversation/ConversationCompactionAccess.java` | 对话压缩访问接口 |
| 新建 | `src/main/java/com/lunacode/conversation/ConversationMessageSnapshot.java` | 完整消息快照 |
| 新建 | `src/main/java/com/lunacode/conversation/ConversationMessageMetadata.java` | 消息压缩元数据 |
| 修改 | `src/main/java/com/lunacode/conversation/DefaultConversationManager.java` | 支持完整快照、工具结果替换和历史重写 |
| 新建 | `src/main/java/com/lunacode/context/CompactTrigger.java` | 压缩触发来源枚举 |
| 新建 | `src/main/java/com/lunacode/context/ContextPreparationRequest.java` | 压缩预检请求 |
| 新建 | `src/main/java/com/lunacode/context/ContextPreparationResult.java` | 压缩预检结果 |
| 新建 | `src/main/java/com/lunacode/context/ContextManager.java` | 上下文管理主接口 |
| 新建 | `src/main/java/com/lunacode/context/DefaultContextManager.java` | 上下文管理编排实现 |
| 新建 | `src/main/java/com/lunacode/context/CompactionState.java` | 自动摘要失败计数和熔断状态 |
| 新建 | `src/main/java/com/lunacode/context/TokenEstimate.java` | Token 估算结果 |
| 新建 | `src/main/java/com/lunacode/context/ContextTokenEstimator.java` | Token 估算接口 |
| 新建 | `src/main/java/com/lunacode/context/ApproximateContextTokenEstimator.java` | 近似 Token 估算实现 |
| 新建 | `src/main/java/com/lunacode/context/ExternalizedToolResultRef.java` | 外置工具结果引用 |
| 新建 | `src/main/java/com/lunacode/context/ExternalizedToolResultPayload.java` | 外置工具结果写盘载荷 |
| 新建 | `src/main/java/com/lunacode/context/LightweightCompactionResult.java` | 轻量预防结果 |
| 新建 | `src/main/java/com/lunacode/context/LightweightToolResultExternalizer.java` | 工具结果外置逻辑 |
| 新建 | `src/main/java/com/lunacode/context/SessionContextStore.java` | 会话临时存储接口 |
| 新建 | `src/main/java/com/lunacode/context/ProjectSessionContextStore.java` | 项目内会话临时存储实现 |
| 新建 | `src/main/java/com/lunacode/context/SessionLogSnapshot.java` | 完整会话记录载荷 |
| 新建 | `src/main/java/com/lunacode/context/CompactionMetadata.java` | 压缩元数据载荷 |
| 新建 | `src/main/java/com/lunacode/context/SummaryPromptBuilder.java` | 摘要 Prompt 构建 |
| 新建 | `src/main/java/com/lunacode/context/SummaryModelClient.java` | 摘要模型调用接口 |
| 新建 | `src/main/java/com/lunacode/context/ProviderSummaryModelClient.java` | 复用当前 Provider 的摘要调用实现 |
| 新建 | `src/main/java/com/lunacode/context/SummaryModelRequest.java` | 摘要请求 |
| 新建 | `src/main/java/com/lunacode/context/SummaryModelResult.java` | 摘要结果和失败分类 |
| 新建 | `src/main/java/com/lunacode/context/SummaryResponseParser.java` | 正式摘要解析 |
| 新建 | `src/main/java/com/lunacode/context/PromptTooLongRetryPolicy.java` | Prompt Too Long 重试策略 |
| 新建 | `src/main/java/com/lunacode/context/HistoryCompactor.java` | 历史摘要和重写编排 |
| 新建 | `src/main/java/com/lunacode/context/HistoryCompactionRequest.java` | 历史压缩请求 |
| 新建 | `src/main/java/com/lunacode/context/CompactionRewrite.java` | 历史压缩重写结果 |
| 新建 | `src/main/java/com/lunacode/context/RecentFileAccess.java` | 最近访问文件记录 |
| 新建 | `src/main/java/com/lunacode/context/RecentFileAccessTracker.java` | 最近文件访问追踪 |
| 新建 | `src/main/java/com/lunacode/context/RestoredFileContextBuilder.java` | 恢复文件快照构建 |
| 新建 | `src/main/java/com/lunacode/context/UsedSkillDefinition.java` | 已使用 Skill 定义 |
| 新建 | `src/main/java/com/lunacode/context/UsedSkillRegistry.java` | 已使用 Skill 注册接口 |
| 新建 | `src/main/java/com/lunacode/context/InMemoryUsedSkillRegistry.java` | 默认内存 Skill 注册实现 |
| 修改 | `src/main/java/com/lunacode/agent/DefaultAgentLoop.java` | 请求前压缩预检、工具访问记录、usage 校准 |
| 修改 | `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` | `/compact` 本地命令 |
| 修改 | `src/main/java/com/lunacode/orchestrator/StatusSnapshot.java` | 展示压缩状态所需字段或状态 |
| 修改 | `src/main/java/com/lunacode/agent/event/AgentEvent.java` | 增加压缩开始、完成、失败事件 |
| 修改 | `src/main/java/com/lunacode/app/LunaCodeApplication.java` | 组装 ContextManager 和会话存储 |
| 新建/修改 | `src/test/java/com/lunacode/config/*Context*Test.java` | 配置与预算测试 |
| 新建/修改 | `src/test/java/com/lunacode/conversation/*Compaction*Test.java` | 对话快照和重写测试 |
| 新建/修改 | `src/test/java/com/lunacode/context/*Test.java` | 上下文管理核心测试 |
| 新建/修改 | `src/test/java/com/lunacode/agent/*Context*Test.java` | Agent Loop 集成测试 |
| 新建/修改 | `src/test/java/com/lunacode/orchestrator/*Compact*Test.java` | `/compact` 命令测试 |

## T1: 增加上下文配置和预算计算

**文件：** `src/main/java/com/lunacode/config/ContextConfig.java`、`src/main/java/com/lunacode/config/ContextBudget.java`  
**依赖：** 无  
**步骤：**
1. 新建 `ContextConfig` record，包含 plan 中定义的所有配置字段。
2. 实现 `defaults()`，填入 spec 确认的默认值。
3. 实现构造校验，确保上下文窗口、摘要预留、自动安全余量和强制线额外值为正。
4. 新建 `ContextBudget` record，并由 `ContextConfig.budget()` 计算有效窗口、自动压缩阈值和强制压缩阈值。

**验证：** 运行 `mvn -Dtest=ContextConfigTest test`，期望默认 200000/20000/13000/10000 示例能算出 180000、167000、177000。

## T2: 接入配置解析

**文件：** `src/main/java/com/lunacode/config/ProviderConfig.java`、`src/main/java/com/lunacode/config/ConfigLoader.java`  
**依赖：** T1  
**步骤：**
1. 给 `ProviderConfig` 增加 `ContextConfig context` 字段，并在所有构造路径中默认填 `ContextConfig.defaults()`。
2. 在 `ConfigLoader.RawConfig` 增加 `RawContext context`。
3. 实现 `toContextConfig`，支持 YAML 字段到 `ContextConfig` 的映射。
4. 保持未配置 `context` 时使用默认值。

**验证：** 运行 `mvn -Dtest=ConfigLoaderTest test`，期望旧配置仍可加载，新配置能覆盖上下文窗口和阈值。

## T3: 更新配置示例和临时目录忽略

**文件：** `config.example.yaml`、`.gitignore`  
**依赖：** T1  
**步骤：**
1. 在 `config.example.yaml` 增加 `context:` 示例块。
2. 在 `.gitignore` 增加 `.lunacode/tmp/`。
3. 示例中保留默认值注释，说明 `.lunacode/tmp/context/` 是当前会话临时产物。

**验证：** 运行 `git diff --check`，期望无空白错误；人工查看示例包含 `context_window_tokens`。

## T4: 增加完整对话快照数据结构

**文件：** `src/main/java/com/lunacode/conversation/ConversationMessageSnapshot.java`、`src/main/java/com/lunacode/conversation/ConversationMessageMetadata.java`、`src/main/java/com/lunacode/conversation/ConversationCompactionAccess.java`  
**依赖：** 无  
**步骤：**
1. 新建完整消息快照 record，包含 id、role、status、timestamp、usage、content、blocks、metadata、errorSummary。
2. 新建元数据 record，提供 `empty()`。
3. 新建 `ConversationCompactionAccess`，声明 `fullSnapshot`、`replaceToolResultContent`、`rewriteForCompaction`。

**验证：** 运行 `mvn -Dtest=ConversationCompactionAccessTest test`，期望接口类型编译通过。

## T5: 扩展 DefaultConversationManager 的完整快照

**文件：** `src/main/java/com/lunacode/conversation/DefaultConversationManager.java`  
**依赖：** T4  
**步骤：**
1. 让 `DefaultConversationManager` 实现 `ConversationCompactionAccess`。
2. 给内部 `MutableMessage` 增加 `ConversationMessageMetadata metadata`。
3. 实现 `fullSnapshot()`，深拷贝 blocks 和 metadata。
4. 保持现有 `snapshot()` 和 `toAPIFormat()` 行为不变。

**验证：** 运行 `mvn -Dtest=DefaultConversationManagerTest test`，期望现有对话快照和 API 格式测试通过，新测试能看到 ToolUse/ToolResult blocks。

## T6: 实现工具结果替换和历史重写

**文件：** `src/main/java/com/lunacode/conversation/DefaultConversationManager.java`  
**依赖：** T5  
**步骤：**
1. 实现 `replaceToolResultContent`，按 message id 和 toolUseId 替换对应 `ToolResultBlock`。
2. 替换后同步更新该消息 content 和 metadata 外置引用。
3. 实现 `rewriteForCompaction`，用传入快照整体替换内部消息列表。
4. 保证重写后 `toAPIFormat()` 仍能合并连续 user 文本消息。

**验证：** 运行 `mvn -Dtest=DefaultConversationManagerCompactionTest test`，期望工具结果替换成功、摘要 user 消息和近期 user 消息可被序列化层合并。

## T7: 创建上下文基础 DTO 和状态

**文件：** `src/main/java/com/lunacode/context/CompactTrigger.java`、`ContextPreparationRequest.java`、`ContextPreparationResult.java`、`ContextManager.java`、`CompactionState.java`、`TokenEstimate.java`  
**依赖：** T1、T4  
**步骤：**
1. 新建 `CompactTrigger` 枚举，包含 `AUTO_CHECK`、`FORCE`、`MANUAL`。
2. 新建预检请求和结果 record。
3. 新建 `ContextManager` 接口。
4. 新建 `CompactionState`，记录普通自动摘要连续失败次数和熔断状态。
5. 新建 `TokenEstimate`，包含估算 token、字符数和来源说明。

**验证：** 运行 `mvn -Dtest=CompactionStateTest test`，期望连续失败 3 次后熔断，强制触发不受熔断阻止。

## T8: 实现会话临时存储

**文件：** `src/main/java/com/lunacode/context/SessionContextStore.java`、`ProjectSessionContextStore.java`、`ExternalizedToolResultPayload.java`、`SessionLogSnapshot.java`、`CompactionMetadata.java`  
**依赖：** T4、T7  
**步骤：**
1. 定义 `SessionContextStore` 接口。
2. 实现 `ProjectSessionContextStore`，在 `.lunacode/tmp/context/<session-id>/` 下创建目录。
3. 实现工具结果写盘，文件名包含消息 id 和 tool_use id。
4. 实现 `session.jsonl` 写出完整消息历史和外置结果索引。
5. 实现压缩元数据写出。

**验证：** 运行 `mvn -Dtest=ProjectSessionContextStoreTest test`，期望写出的工具结果文件和 `session.jsonl` 存在且路径在工作区临时目录下。

## T9: 实现近似 Token 估算器

**文件：** `src/main/java/com/lunacode/context/ContextTokenEstimator.java`、`ApproximateContextTokenEstimator.java`、`TokenEstimate.java`  
**依赖：** T1、T7  
**步骤：**
1. 定义 `ContextTokenEstimator` 接口。
2. 实现按字符数估算 token 的默认策略。
3. 支持使用 Provider `TokenUsage.inputTokens()` 作为锚点。
4. 支持估算 `PromptBundle` 和消息快照列表。

**验证：** 运行 `mvn -Dtest=ApproximateContextTokenEstimatorTest test`，期望锚定 usage 后新增字符只按增量估算。

## T10: 实现轻量工具结果外置

**文件：** `src/main/java/com/lunacode/context/LightweightToolResultExternalizer.java`、`LightweightCompactionResult.java`、`ExternalizedToolResultRef.java`  
**依赖：** T6、T8  
**步骤：**
1. 遍历完整消息快照中的 `TOOL` 消息。
2. 对单个超 50,000 字符的结果写盘并替换内容。
3. 对单条 TOOL 消息聚合超 200,000 字符的情况，按结果长度倒序继续外置。
4. 替换内容包含工具名、预览、完整路径、重新读取提示和敏感值遮蔽后的短摘要。

**验证：** 运行 `mvn -Dtest=LightweightToolResultExternalizerTest test`，期望单个超限和聚合超限场景都只保留预览，原文文件可读。

## T11: 实现摘要 Prompt 和响应解析

**文件：** `src/main/java/com/lunacode/context/SummaryPromptBuilder.java`、`SummaryResponseParser.java`  
**依赖：** T4、T7  
**步骤：**
1. 构造摘要 Prompt，包含 9 个固定部分。
2. 在 Prompt 中明确禁止工具调用。
3. 在 Prompt 中要求先写分析草稿再写正式摘要，并定义正式摘要分隔标记。
4. 解析模型响应时只提取正式摘要，丢弃草稿。

**验证：** 运行 `mvn -Dtest=SummaryPromptBuilderTest,SummaryResponseParserTest test`，期望 Prompt 包含 9 个标题和禁止工具说明，解析结果不含草稿。

## T12: 实现摘要模型调用和错误分类

**文件：** `src/main/java/com/lunacode/context/SummaryModelClient.java`、`ProviderSummaryModelClient.java`、`SummaryModelRequest.java`、`SummaryModelResult.java`  
**依赖：** T11  
**步骤：**
1. 定义摘要请求、结果和失败类型。
2. 实现 `ProviderSummaryModelClient`，复用当前 `ChatProvider` 和 `ProviderConfig`。
3. 摘要请求传空工具声明，不写入 `ConversationManager`。
4. 收集 stream 文本并返回正式摘要或失败原因。
5. 对错误文本进行 Prompt Too Long 分类。

**验证：** 运行 `mvn -Dtest=ProviderSummaryModelClientTest test`，使用假 Provider 验证成功、普通失败、Prompt Too Long 三类结果。

## T13: 实现 Prompt Too Long 重试策略

**文件：** `src/main/java/com/lunacode/context/PromptTooLongRetryPolicy.java`  
**依赖：** T12  
**步骤：**
1. 按 API 轮次为待摘要消息分组。
2. Prompt Too Long 时丢弃最旧消息组并重试。
3. 最多进行 3 次分组重试。
4. 仍失败时每轮丢弃 20% 消息组继续尝试，直到成功或没有可摘要消息。

**验证：** 运行 `mvn -Dtest=PromptTooLongRetryPolicyTest test`，期望重试次数和丢弃组顺序符合 spec。

## T14: 实现最近访问文件追踪

**文件：** `src/main/java/com/lunacode/context/RecentFileAccess.java`、`RecentFileAccessTracker.java`  
**依赖：** T7  
**步骤：**
1. 从成功的 `ReadFile`、`WriteFile`、`EditFile` 工具记录中提取路径。
2. 规范化为工作区内路径。
3. 按访问时间更新记录，重复访问同一路径时刷新时间。
4. 提供最近 N 个访问文件查询。

**验证：** 运行 `mvn -Dtest=RecentFileAccessTrackerTest test`，期望失败工具不记录、自然语言路径不记录、重复文件按最新访问排序。

## T15: 实现恢复文件快照构建

**文件：** `src/main/java/com/lunacode/context/RestoredFileContextBuilder.java`  
**依赖：** T9、T14  
**步骤：**
1. 按最近访问时间取最多 5 个文件。
2. 读取文件当前内容。
3. 每个文件截断到约 5,000 token。
4. 读取失败时生成简短失败说明，不中断压缩。

**验证：** 运行 `mvn -Dtest=RestoredFileContextBuilderTest test`，期望最多恢复 5 个文件，单文件预算生效。

## T16: 增加 Skill 定义注册接口

**文件：** `src/main/java/com/lunacode/context/UsedSkillDefinition.java`、`UsedSkillRegistry.java`、`InMemoryUsedSkillRegistry.java`  
**依赖：** T9  
**步骤：**
1. 定义已使用 Skill 的名称、定义正文和使用时间。
2. 实现内存注册表。
3. 按 25,000 token 总预算返回最近使用的定义。
4. 默认无 Skill 时返回空列表。

**验证：** 运行 `mvn -Dtest=InMemoryUsedSkillRegistryTest test`，期望预算截断和空注册表行为正确。

## T17: 实现历史保留边界选择

**文件：** `src/main/java/com/lunacode/context/HistoryCompactor.java`、`HistoryCompactionRequest.java`、`CompactionRewrite.java`  
**依赖：** T9、T11  
**步骤：**
1. 从尾部向前累计近期消息 token。
2. 同时满足约 10,000 token 或至少 5 条消息中更大的保留范围。
3. 检查边界是否切断 assistant tool_use 与后续 tool_result 配对。
4. 必要时向前扩展边界。

**验证：** 运行 `mvn -Dtest=HistoryCompactorBoundaryTest test`，期望短消息保留至少 5 条，长消息保留到 10,000 token，工具配对不被切断。

## T18: 实现历史摘要重写

**文件：** `src/main/java/com/lunacode/context/HistoryCompactor.java`、`CompactionRewrite.java`  
**依赖：** T8、T11、T12、T15、T16、T17  
**步骤：**
1. 将旧摘要和待压缩早期消息作为摘要输入。
2. 调用摘要模型生成正式摘要。
3. 写出 `session.jsonl`。
4. 构造新的摘要 user 消息，拼接摘要、会话记录路径、边界提示、恢复文件快照和 Skill 定义。
5. 将摘要消息与近期原文组成重写结果。

**验证：** 运行 `mvn -Dtest=HistoryCompactorRewriteTest test`，期望多次压缩后只保留一条最新摘要，近期原文完全一致。

## T19: 实现 DefaultContextManager 编排

**文件：** `src/main/java/com/lunacode/context/DefaultContextManager.java`  
**依赖：** T8、T9、T10、T13、T18  
**步骤：**
1. 在 `prepareBeforeTurn` 中先执行轻量预防。
2. 构建候选 PromptBundle 并估算 token。
3. 超过强制线时执行强制压缩，忽略普通自动熔断状态。
4. 超过自动线且未熔断时执行普通自动压缩。
5. 自动压缩连续失败 3 次后设置熔断。
6. 强制压缩失败时返回 `proceed=false`。
7. 手动压缩调用同一套压缩逻辑并生成中文结果。

**验证：** 运行 `mvn -Dtest=DefaultContextManagerTest test`，期望自动、手动、强制、熔断、强制失败停止请求行为符合 spec。

## T20: 增加压缩事件和状态展示

**文件：** `src/main/java/com/lunacode/agent/event/AgentEvent.java`、`src/main/java/com/lunacode/orchestrator/StatusSnapshot.java`  
**依赖：** T7  
**步骤：**
1. 增加压缩开始、压缩完成、压缩失败事件。
2. 让事件携带触发来源、估算前后 token、外置数量、摘要消息数量和恢复文件数量。
3. 让状态对象能表达 `compacting`、`warning` 或复用现有状态展示压缩结果。

**验证：** 运行 `mvn -Dtest=DefaultChatOrchestratorTest test`，期望新增事件不会破坏现有状态测试。

## T21: 接入 Agent Loop

**文件：** `src/main/java/com/lunacode/agent/DefaultAgentLoop.java`  
**依赖：** T19、T20  
**步骤：**
1. 给构造函数增加 `ContextManager`，允许测试中使用 no-op 实现。
2. 每轮构建最终 PromptBundle 前调用 `prepareBeforeTurn`。
3. 压缩后重新读取 `conversationManager.toAPIFormat()` 并构建最终 PromptBundle。
4. 工具执行完成后调用 `recordToolExecutions`。
5. Provider usage 更新后调用 `recordProviderUsage`。
6. `proceed=false` 时发出错误事件并结束 Loop。

**验证：** 运行 `mvn -Dtest=DefaultAgentLoopTest test`，期望 no-op 场景行为不变，强制失败场景不会调用 Provider。

## T22: 接入 `/compact` 本地命令

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`  
**依赖：** T19、T20  
**步骤：**
1. 在普通用户请求分支前识别 `/compact`。
2. 忙碌、等待权限、等待问题时提示当前不能压缩。
3. 空闲时提交 executor 调用 `ContextManager.compactManually`。
4. 完成后通过状态展示中文压缩结果。
5. 确保 `/compact` 不写入普通对话历史。

**验证：** 运行 `mvn -Dtest=DefaultChatOrchestratorCompactTest test`，期望 `/compact` 不触发 AgentLoop 普通请求。

## T23: 应用层组装 ContextManager

**文件：** `src/main/java/com/lunacode/app/LunaCodeApplication.java`、`src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`  
**依赖：** T19、T21、T22  
**步骤：**
1. 在应用启动时创建 `ProjectSessionContextStore`。
2. 创建 `ApproximateContextTokenEstimator`、`RecentFileAccessTracker`、`InMemoryUsedSkillRegistry` 和 `DefaultContextManager`。
3. 将 `ContextManager` 注入 `DefaultChatOrchestrator` 和 `DefaultAgentLoop`。
4. 测试构造函数保留 no-op 默认值，减少现有测试改动。

**验证：** 运行 `mvn -Dtest=LunaCodeApplicationTest,DefaultChatOrchestratorTest test`，期望应用组装不抛异常。

## T24: 补齐单元测试覆盖

**文件：** `src/test/java/com/lunacode/config/*Context*Test.java`、`src/test/java/com/lunacode/conversation/*Compaction*Test.java`、`src/test/java/com/lunacode/context/*Test.java`  
**依赖：** T1-T19  
**步骤：**
1. 覆盖预算公式和默认值。
2. 覆盖完整快照、工具结果替换、历史重写。
3. 覆盖轻量外置、会话存储、token 估算、摘要 prompt、response parser。
4. 覆盖最近文件恢复和 Skill 预算。
5. 覆盖 Prompt Too Long 重试。

**验证：** 运行 `mvn -Dtest=*Context*,*Compaction*,*Compact* test`，期望所有新增核心测试通过。

## T25: 补齐集成测试覆盖

**文件：** `src/test/java/com/lunacode/agent/*Context*Test.java`、`src/test/java/com/lunacode/orchestrator/*Compact*Test.java`  
**依赖：** T21、T22、T23  
**步骤：**
1. 用假 Provider 验证自动压缩会在 Provider 请求前发生。
2. 用假 ContextManager 验证强制失败会停止当前请求。
3. 验证工具执行后最近访问文件被记录。
4. 验证 `/compact` 是本地命令且不进入普通对话。

**验证：** 运行 `mvn -Dtest=*Agent*Context*,*Orchestrator*Compact* test`，期望集成测试通过。

## T26: 执行编译、全量测试和 tmux 验收准备

**文件：** `pom.xml`、`run-LunaCode.bat`、`config.example.yaml`、`spec/07/checklist.md`（后续阶段生成后使用）  
**依赖：** T24、T25  
**步骤：**
1. 运行 `mvn test`。
2. 运行 `mvn package -DskipTests`。
3. 准备一个小上下文窗口测试配置，便于 tmux 中触发 `/compact`。
4. 在后续 checklist 阶段按端到端场景启动 LunaCode 并验证。

**验证：** `mvn test` 和 `mvn package -DskipTests` 均成功；tmux 验收步骤留到 checklist 通过后执行。

## 执行顺序

```text
T1 -> T2 -> T3
T4 -> T5 -> T6
T7 -> T8 -> T9 -> T10
T11 -> T12 -> T13
T14 -> T15 -> T16
T17 -> T18 -> T19
T20 -> T21 -> T22 -> T23
T24 -> T25 -> T26
```
