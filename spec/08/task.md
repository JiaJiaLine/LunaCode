# LunaCode 项目记忆与会话恢复 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `src/main/java/com/lunacode/session/SessionId.java` | 会话 ID 值对象 |
| 新建 | `src/main/java/com/lunacode/session/SessionRecord.java` | JSONL 单行记录结构 |
| 新建 | `src/main/java/com/lunacode/session/SessionInfo.java` | 会话列表和当前会话摘要 |
| 新建 | `src/main/java/com/lunacode/session/SessionLoadResult.java` | JSONL 加载结果和 warning |
| 新建 | `src/main/java/com/lunacode/session/SessionRecoveryResult.java` | 恢复后的历史、提示和降级状态 |
| 新建 | `src/main/java/com/lunacode/session/SessionStore.java` | 会话存储接口 |
| 新建 | `src/main/java/com/lunacode/session/JsonlSessionStore.java` | `.lunacode/sessions/*.jsonl` 读写和扫描 |
| 新建 | `src/main/java/com/lunacode/session/SessionService.java` | 当前会话、恢复、切换、新建能力 |
| 新建 | `src/main/java/com/lunacode/session/DefaultSessionService.java` | 会话服务默认实现 |
| 新建 | `src/main/java/com/lunacode/session/SessionRecoveryPolicy.java` | 坏行、工具配对、时间跨度、token 降级策略 |
| 新建 | `src/main/java/com/lunacode/session/SessionTitleDeriver.java` | 从第一条用户消息推导标题 |
| 新建 | `src/main/java/com/lunacode/session/SessionBackedConversationManager.java` | 包装 ConversationManager 并追加写 JSONL |
| 新建 | `src/main/java/com/lunacode/session/SessionCommandHandler.java` | `/session` 命令处理 |
| 新建 | `src/main/java/com/lunacode/instructions/InstructionScope.java` | 指令来源枚举 |
| 新建 | `src/main/java/com/lunacode/instructions/InstructionSource.java` | 指令来源文件描述 |
| 新建 | `src/main/java/com/lunacode/instructions/InstructionSection.java` | 加载后的指令段 |
| 新建 | `src/main/java/com/lunacode/instructions/ProjectInstructionContext.java` | 多层项目指令上下文 |
| 新建 | `src/main/java/com/lunacode/instructions/ProjectInstructionLoader.java` | 项目指令加载接口 |
| 新建 | `src/main/java/com/lunacode/instructions/DefaultProjectInstructionLoader.java` | 三层 `LUNACODE.md` 加载实现 |
| 新建 | `src/main/java/com/lunacode/instructions/IncludeResolver.java` | `@include` 展开实现 |
| 新建 | `src/main/java/com/lunacode/instructions/IncludeBoundary.java` | include 允许根目录描述 |
| 新建 | `src/main/java/com/lunacode/memory/MemoryType.java` | 四类记忆枚举 |
| 新建 | `src/main/java/com/lunacode/memory/MemoryConfig.java` | `memory.auto_update` 配置 |
| 新建 | `src/main/java/com/lunacode/memory/MemoryNote.java` | 单条 Markdown 记忆 |
| 新建 | `src/main/java/com/lunacode/memory/MemoryIndexSnapshot.java` | 用户级、项目级、合并索引快照 |
| 新建 | `src/main/java/com/lunacode/memory/MemoryRuntimeState.java` | 运行时记忆开关和状态 |
| 新建 | `src/main/java/com/lunacode/memory/MemoryStore.java` | 记忆存储接口 |
| 新建 | `src/main/java/com/lunacode/memory/MarkdownMemoryStore.java` | frontmatter Markdown 读写 |
| 新建 | `src/main/java/com/lunacode/memory/MemoryIndexBuilder.java` | `MemoryIndex.md` 重建和大小限制 |
| 新建 | `src/main/java/com/lunacode/memory/MemoryContextLoader.java` | Prompt 前加载合并索引 |
| 新建 | `src/main/java/com/lunacode/memory/DefaultMemoryContextLoader.java` | 记忆索引加载默认实现 |
| 新建 | `src/main/java/com/lunacode/memory/AutoMemoryUpdater.java` | 自动记忆异步入口 |
| 新建 | `src/main/java/com/lunacode/memory/DefaultAutoMemoryUpdater.java` | LoopComplete 后后台更新记忆 |
| 新建 | `src/main/java/com/lunacode/memory/MemoryModelClient.java` | 记忆更新 LLM 客户端接口 |
| 新建 | `src/main/java/com/lunacode/memory/ProviderMemoryModelClient.java` | 复用当前 Provider 调用记忆模型 |
| 新建 | `src/main/java/com/lunacode/memory/MemoryUpdateRequest.java` | 记忆更新输入 |
| 新建 | `src/main/java/com/lunacode/memory/MemoryUpdateAction.java` | add/update/delete/no-op 动作 |
| 新建 | `src/main/java/com/lunacode/memory/MemoryCommandHandler.java` | `/memory` 命令处理 |
| 修改 | `src/main/java/com/lunacode/config/ProviderConfig.java` | 增加 memory 配置 |
| 修改 | `src/main/java/com/lunacode/config/ConfigLoader.java` | 解析 `memory.auto_update` |
| 修改 | `src/main/java/com/lunacode/prompt/MessageChannel.java` | 承载项目指令和记忆上下文 |
| 修改 | `src/main/java/com/lunacode/prompt/MessageChannelBuilder.java` | 组装项目指令和记忆 |
| 修改 | `src/main/java/com/lunacode/prompt/PromptContextBuilder.java` | 请求前加载指令和 MemoryIndex |
| 修改 | `src/main/java/com/lunacode/provider/OpenAiPromptAdapter.java` | 按顺序渲染项目指令和记忆 |
| 修改 | `src/main/java/com/lunacode/provider/AnthropicPromptAdapter.java` | 按顺序渲染项目指令和记忆 |
| 修改 | `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` | 接入 `/session`、`/memory` 和 LoopComplete 记忆钩子 |
| 修改 | `src/main/java/com/lunacode/orchestrator/StatusSnapshot.java` | 增加 session 和 memory 状态 |
| 修改 | `src/main/java/com/lunacode/agent/event/AgentEvent.java` | 增加会话和记忆状态事件 |
| 修改 | `src/main/java/com/lunacode/tui/LanternaLunaTui.java` | 展示 session 短 ID 和 memory 状态 |
| 修改 | `src/main/java/com/lunacode/app/LunaCodeApplication.java` | 启动时装配并恢复会话 |
| 新建 | `src/test/java/com/lunacode/session/*Test.java` | 会话单元测试 |
| 新建 | `src/test/java/com/lunacode/instructions/*Test.java` | 指令和 include 单元测试 |
| 新建 | `src/test/java/com/lunacode/memory/*Test.java` | 记忆单元测试 |
| 新建 | `src/test/java/com/lunacode/prompt/PromptContextBuilderMemoryInstructionTest.java` | Prompt 注入顺序测试 |
| 新建 | `src/test/java/com/lunacode/orchestrator/*CommandHandlerTest.java` | `/session` 和 `/memory` 命令测试 |
| 新建 | `src/test/java/com/lunacode/tui/LanternaLunaTuiStatusSnapshotTest.java` | TUI 状态展示测试 |

## T1: 增加 memory 配置模型

**文件：** `src/main/java/com/lunacode/memory/MemoryConfig.java`、`src/main/java/com/lunacode/config/ProviderConfig.java`、`src/main/java/com/lunacode/config/ConfigLoader.java`
**依赖：** 无
**步骤：**
1. 新建 `MemoryConfig`，包含 `autoUpdate` 默认值。
2. 在 `ProviderConfig` 中增加 memory 配置入口。
3. 在 `ConfigLoader` 的 raw config 中解析 `memory.auto_update`，缺省为 `true`。

**验证：** 运行 `mvn -Dtest=ConfigLoaderTest test`；如果没有现成测试，先运行 `mvn -DskipTests compile` 确认配置模型编译通过。

## T2: 扩展 StatusSnapshot

**文件：** `src/main/java/com/lunacode/orchestrator/StatusSnapshot.java`
**依赖：** T1
**步骤：**
1. 增加 session 短 ID 字段。
2. 增加自动记忆开关字段。
3. 增加最近一次记忆更新状态字段。
4. 保持已有构造方法或 builder 调用兼容。

**验证：** 运行 `mvn -Dtest=DefaultChatOrchestratorCompactTest,LanternaLunaTuiTest test`，确认旧状态测试仍通过。

## T3: 建立会话基础类型和接口

**文件：** `src/main/java/com/lunacode/session/SessionId.java`、`SessionRecord.java`、`SessionInfo.java`、`SessionLoadResult.java`、`SessionRecoveryResult.java`、`SessionStore.java`、`SessionService.java`
**依赖：** 无
**步骤：**
1. 按 plan 定义会话 ID、记录、信息、加载结果、恢复结果类型。
2. 定义 `SessionStore` 和 `SessionService` 接口。
3. 让类型只依赖现有 conversation snapshot 和 Java 标准库。

**验证：** 运行 `mvn -DskipTests compile`，确认新增接口无循环依赖。

## T4: 实现会话 ID 和路径规则

**文件：** `src/main/java/com/lunacode/session/JsonlSessionStore.java`、`src/test/java/com/lunacode/session/JsonlSessionStoreTest.java`
**依赖：** T3
**步骤：**
1. 实现 `YYYYMMDD-HHMMSS-xxxx` ID 生成。
2. 处理同秒同后缀冲突时重新生成。
3. 将 ID 映射到 `<项目根>/.lunacode/sessions/<id>.jsonl`。
4. 补测试覆盖文件名正则和路径位置。

**验证：** 运行 `mvn -Dtest=JsonlSessionStoreTest test`，期望 ID 格式和路径断言通过。

## T5: 实现 JSONL 追加写

**文件：** `src/main/java/com/lunacode/session/JsonlSessionStore.java`、`src/test/java/com/lunacode/session/JsonlSessionStoreTest.java`
**依赖：** T4
**步骤：**
1. 将 `ConversationMessageSnapshot` 转成只含 `role`、`content`、`ts` 的 JSON 对象。
2. 支持字符串 content 和 content block 数组。
3. 每次 append 只追加一行并刷新。
4. 测试 user、assistant text、assistant tool_use、tool_result 的落盘结构。

**验证：** 运行 `mvn -Dtest=JsonlSessionStoreTest test`，期望 JSONL 每行只有三个字段。

## T6: 实现 JSONL 扫描和标题推导

**文件：** `src/main/java/com/lunacode/session/JsonlSessionStore.java`、`src/main/java/com/lunacode/session/SessionTitleDeriver.java`、`src/test/java/com/lunacode/session/JsonlSessionStoreTest.java`
**依赖：** T5
**步骤：**
1. 实现 `listSessions()` 扫描 `.jsonl` 文件。
2. 通过文件名解析 createdAt。
3. 通过最后一条合法记录计算 lastActiveAt 和消息数。
4. 从第一条 user 消息截断生成标题。
5. 坏行只记录 warning，不影响其他合法行。

**验证：** 运行 `mvn -Dtest=JsonlSessionStoreTest test`，期望坏行跳过、标题和消息数正确。

## T7: 实现会话恢复加载

**文件：** `src/main/java/com/lunacode/session/JsonlSessionStore.java`、`src/test/java/com/lunacode/session/JsonlSessionStoreTest.java`
**依赖：** T6
**步骤：**
1. 将合法 JSONL 行反序列化成 `ConversationMessageSnapshot`。
2. 保留坏行 warning。
3. 保留 content block 的 tool_use/tool_result ID、名称、输入和错误状态。
4. 覆盖字符串 content 与数组 content 两种恢复路径。

**验证：** 运行 `mvn -Dtest=JsonlSessionStoreTest test`，期望恢复快照与写入内容等价。

## T8: 实现恢复策略的工具配对修复

**文件：** `src/main/java/com/lunacode/session/SessionRecoveryPolicy.java`、`src/test/java/com/lunacode/session/SessionRecoveryPolicyTest.java`
**依赖：** T7
**步骤：**
1. 扫描消息中的 `tool_use` 和后续 `tool_result`。
2. 如果尾部存在未配对 `tool_use`，从包含该 `tool_use` 的 assistant 消息开始截断。
3. 已配对工具调用保持原样。
4. 测试单个未配对、多工具部分未配对和全配对场景。

**验证：** 运行 `mvn -Dtest=SessionRecoveryPolicyTest test`，期望未配对工具调用不会进入恢复结果。

## T9: 实现时间跨度提醒

**文件：** `src/main/java/com/lunacode/session/SessionRecoveryPolicy.java`、`src/test/java/com/lunacode/session/SessionRecoveryPolicyTest.java`
**依赖：** T8
**步骤：**
1. 判断 lastActiveAt 距当前时间是否超过 24 小时。
2. 超过时生成一条内部提醒消息。
3. 提醒内容包含上次活跃时间、可能有代码变更、建议重新读取相关文件。
4. 未超过 24 小时时不生成提醒。

**验证：** 运行 `mvn -Dtest=SessionRecoveryPolicyTest test`，期望 24 小时边界行为正确。

## T10: 接入 token 超限压缩降级

**文件：** `src/main/java/com/lunacode/session/SessionRecoveryPolicy.java`、相关现有 context/compaction 文件
**依赖：** T9
**步骤：**
1. 复用现有 token 估算或压缩能力判断恢复历史是否超限。
2. 超限时尝试压缩一次。
3. 压缩成功时返回 compacted 恢复结果。
4. 压缩失败时不改写 JSONL，返回 summaryOnly/warning 结果。

**验证：** 运行 `mvn -Dtest=SessionRecoveryPolicyTest,DefaultChatOrchestratorCompactTest test`，期望压缩成功和失败降级都有覆盖。

## T11: 实现 DefaultSessionService

**文件：** `src/main/java/com/lunacode/session/DefaultSessionService.java`、`src/test/java/com/lunacode/session/DefaultSessionServiceTest.java`
**依赖：** T10
**步骤：**
1. 实现启动清理 30 天以上会话。
2. 实现恢复最新未过期会话；没有则创建新会话。
3. 实现 `resume`、`newSession`、`currentSession`、`listSessions`。
4. 确保切换 current session 后后续 append 写入新目标。

**验证：** 运行 `mvn -Dtest=DefaultSessionServiceTest test`，期望清理、恢复、切换、新建都通过。

## T12: 实现 SessionBackedConversationManager

**文件：** `src/main/java/com/lunacode/session/SessionBackedConversationManager.java`、`src/test/java/com/lunacode/session/SessionBackedConversationManagerTest.java`
**依赖：** T11
**步骤：**
1. 包装现有 `DefaultConversationManager`。
2. user 消息加入后立即追加 JSONL。
3. assistant 消息 complete 后追加最终内容。
4. tool result 消息加入后追加 JSONL。
5. 恢复或切换历史时替换内存历史，但禁止重复追加落盘。

**验证：** 运行 `mvn -Dtest=SessionBackedConversationManagerTest test`，期望追加时机和恢复抑制写入正确。

## T13: 装配启动会话恢复

**文件：** `src/main/java/com/lunacode/app/LunaCodeApplication.java`
**依赖：** T11、T12
**步骤：**
1. 创建正式 `SessionStore` 和 `SessionService`。
2. 启动时调用清理和恢复。
3. 将恢复历史写入 `SessionBackedConversationManager`。
4. 把 session 服务传入 orchestrator。

**验证：** 运行 `mvn -DskipTests compile`，再运行相关 orchestrator 测试确认装配无破坏。

## T14: 实现 `/session` 命令

**文件：** `src/main/java/com/lunacode/session/SessionCommandHandler.java`、`src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`src/test/java/com/lunacode/orchestrator/SessionCommandHandlerTest.java`
**依赖：** T13
**步骤：**
1. 支持 `/session` 或 `/session current`。
2. 支持 `/session list`。
3. 支持 `/session resume <id>`。
4. 支持 `/session new`。
5. Agent 忙、等待权限或等待提问输入时拒绝切换。

**验证：** 运行 `mvn -Dtest=SessionCommandHandlerTest test`，期望命令输出和忙碌保护正确。

## T15: 建立项目指令基础类型

**文件：** `src/main/java/com/lunacode/instructions/InstructionScope.java`、`InstructionSource.java`、`InstructionSection.java`、`ProjectInstructionContext.java`、`ProjectInstructionLoader.java`、`IncludeBoundary.java`
**依赖：** 无
**步骤：**
1. 定义三层来源和优先级。
2. 定义 include 边界根。
3. 定义加载结果的 section 列表。
4. 保持类型不依赖 provider 适配器。

**验证：** 运行 `mvn -DskipTests compile`，确认类型可独立编译。

## T16: 实现 IncludeResolver

**文件：** `src/main/java/com/lunacode/instructions/IncludeResolver.java`、`src/test/java/com/lunacode/instructions/IncludeResolverTest.java`
**依赖：** T15
**步骤：**
1. 逐行扫描并替换包含 `@include <path>` 的行。
2. 支持递归展开，最大深度 5。
3. 使用规范绝对路径 visited 集合防环路。
4. 拦截越过 include 边界的路径。
5. 不支持 glob，并对失败原因写入短 warning。

**验证：** 运行 `mvn -Dtest=IncludeResolverTest test`，期望递归、环路、越界、超深度都通过。

## T17: 实现三层 ProjectInstructionLoader

**文件：** `src/main/java/com/lunacode/instructions/DefaultProjectInstructionLoader.java`、`src/test/java/com/lunacode/instructions/DefaultProjectInstructionLoaderTest.java`
**依赖：** T16
**步骤：**
1. 按项目根、项目 `.lunacode`、用户 `~/.lunacode` 顺序检查文件。
2. 对每个存在的文件调用 include 展开。
3. 高优先级内容排在前面。
4. 输出带来源标题的 section。

**验证：** 运行 `mvn -Dtest=DefaultProjectInstructionLoaderTest test`，期望加载顺序和缺失文件行为正确。

## T18: 接入项目指令和记忆的 MessageChannel 字段

**文件：** `src/main/java/com/lunacode/prompt/MessageChannel.java`、`src/main/java/com/lunacode/prompt/MessageChannelBuilder.java`
**依赖：** T17
**步骤：**
1. 确认或调整 `projectInstructions` 和 `memory` 字段结构。
2. 让 builder 可以接收已加载的项目指令和记忆索引。
3. 保持现有未传入时的空值行为。

**验证：** 运行 `mvn -DskipTests compile`，确认现有 provider 适配器暂时仍能编译。

## T19: 建立记忆基础类型

**文件：** `src/main/java/com/lunacode/memory/MemoryType.java`、`MemoryNote.java`、`MemoryIndexSnapshot.java`、`MemoryRuntimeState.java`、`MemoryUpdateRequest.java`、`MemoryUpdateAction.java`、`MemoryStore.java`
**依赖：** T1
**步骤：**
1. 定义四类记忆枚举。
2. 定义 Markdown 记忆和索引快照结构。
3. 定义运行时开关状态。
4. 定义更新请求和动作。
5. 定义 `MemoryStore` 接口。

**验证：** 运行 `mvn -DskipTests compile`，确认基础类型可编译。

## T20: 实现 MarkdownMemoryStore

**文件：** `src/main/java/com/lunacode/memory/MarkdownMemoryStore.java`、`src/test/java/com/lunacode/memory/MarkdownMemoryStoreTest.java`
**依赖：** T19
**步骤：**
1. 解析带 frontmatter 的 Markdown 记忆。
2. 写入 `id`、`type`、`title`、`created_at`、`updated_at`、`source_session`。
3. 按 `type` 决定用户级或项目级目录。
4. 支持 upsert 和 delete。
5. 坏 frontmatter 文件跳过并记录 warning。

**验证：** 运行 `mvn -Dtest=MarkdownMemoryStoreTest test`，期望目录落点和 frontmatter 字段正确。

## T21: 实现 MemoryIndexBuilder

**文件：** `src/main/java/com/lunacode/memory/MemoryIndexBuilder.java`、`src/test/java/com/lunacode/memory/MemoryIndexBuilderTest.java`
**依赖：** T20
**步骤：**
1. 分别生成用户级和项目级 `MemoryIndex.md`。
2. 合并索引时保留类型、标题和摘要。
3. 强制合并索引不超过 200 行 / 25KB。
4. 超限时按更新时间或优先级裁剪。

**验证：** 运行 `mvn -Dtest=MemoryIndexBuilderTest test`，期望文件生成和上限裁剪正确。

## T22: 实现 MemoryContextLoader

**文件：** `src/main/java/com/lunacode/memory/MemoryContextLoader.java`、`src/main/java/com/lunacode/memory/DefaultMemoryContextLoader.java`、`src/test/java/com/lunacode/memory/DefaultMemoryContextLoaderTest.java`
**依赖：** T21
**步骤：**
1. 加载用户级和项目级 `MemoryIndex.md`。
2. 缺失索引时返回空快照。
3. 合并后再次执行 200 行 / 25KB 限制。

**验证：** 运行 `mvn -Dtest=DefaultMemoryContextLoaderTest test`，期望缺失、单侧存在、双侧存在都通过。

## T23: PromptContextBuilder 注入指令和记忆

**文件：** `src/main/java/com/lunacode/prompt/PromptContextBuilder.java`、`src/test/java/com/lunacode/prompt/PromptContextBuilderMemoryInstructionTest.java`
**依赖：** T18、T22
**步骤：**
1. 注入 `ProjectInstructionLoader`。
2. 注入 `MemoryContextLoader`。
3. 每次构建请求前加载最新指令和索引。
4. 将结果传给 `MessageChannelBuilder`。

**验证：** 运行 `mvn -Dtest=PromptContextBuilderMemoryInstructionTest test`，期望构建出的 channel 包含指令和记忆。

## T24: Provider 适配器渲染注入顺序

**文件：** `src/main/java/com/lunacode/provider/OpenAiPromptAdapter.java`、`src/main/java/com/lunacode/provider/AnthropicPromptAdapter.java`、`src/test/java/com/lunacode/prompt/PromptContextBuilderMemoryInstructionTest.java`
**依赖：** T23
**步骤：**
1. OpenAI 请求中按系统提示、环境、项目指令、记忆、提醒、历史顺序渲染。
2. Anthropic 请求中保持现有风格并加入相同逻辑顺序。
3. 不改变普通历史消息的 role/content 结构。

**验证：** 运行 `mvn -Dtest=PromptContextBuilderMemoryInstructionTest test`，期望适配后的请求顺序符合 plan。

## T25: 实现 MemoryModelClient

**文件：** `src/main/java/com/lunacode/memory/MemoryModelClient.java`、`src/main/java/com/lunacode/memory/ProviderMemoryModelClient.java`、`src/test/java/com/lunacode/memory/ProviderMemoryModelClientTest.java`
**依赖：** T19
**步骤：**
1. 定义记忆模型输入提示，包含本轮增量和现有索引。
2. 要求模型输出结构化 add/update/delete/no-op。
3. 解析输出并校验 action、type、targetId、title、body。
4. 解析失败时返回失败状态，不抛到主对话。

**验证：** 运行 `mvn -Dtest=ProviderMemoryModelClientTest test`，用 fake provider 覆盖合法和非法输出。

## T26: 实现 DefaultAutoMemoryUpdater

**文件：** `src/main/java/com/lunacode/memory/DefaultAutoMemoryUpdater.java`、`src/test/java/com/lunacode/memory/DefaultAutoMemoryUpdaterTest.java`
**依赖：** T20、T21、T25
**步骤：**
1. 在独立 executor 中执行记忆更新。
2. 对 action 执行敏感信息过滤。
3. add/update/delete/no-op 后重建 `MemoryIndex.md`。
4. 失败时只更新状态，不影响聊天回复。

**验证：** 运行 `mvn -Dtest=DefaultAutoMemoryUpdaterTest test`，期望成功、no-op、敏感值过滤、失败降级都通过。

## T27: 实现 `/memory` 命令

**文件：** `src/main/java/com/lunacode/memory/MemoryCommandHandler.java`、`src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`src/test/java/com/lunacode/orchestrator/MemoryCommandHandlerTest.java`
**依赖：** T22、T26
**步骤：**
1. 支持 `/memory` 查看索引摘要和开关状态。
2. 支持 `/memory list` 列出记忆。
3. 支持 `/memory delete <id>` 删除记忆并重建索引。
4. 支持 `/memory on` 和 `/memory off` 切换运行时状态。

**验证：** 运行 `mvn -Dtest=MemoryCommandHandlerTest test`，期望命令输出和状态切换正确。

## T28: 接入 LoopComplete 自动记忆钩子

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`src/main/java/com/lunacode/agent/event/AgentEvent.java`
**依赖：** T26、T27
**步骤：**
1. 在提交用户消息前记录本轮起始消息位置。
2. 收到 `LoopComplete` 后截取本轮新增消息。
3. 自动记忆开启时提交 `AutoMemoryUpdater.updateAsync`。
4. 增加记忆 started/updated/noop/failed 状态事件。

**验证：** 运行 `mvn -Dtest=DefaultChatOrchestratorCompactTest,MemoryCommandHandlerTest test`，期望 LoopComplete 后记忆 updater 被调用且不会阻塞。

## T29: 接入会话和记忆状态栏

**文件：** `src/main/java/com/lunacode/orchestrator/StatusSnapshot.java`、`src/main/java/com/lunacode/tui/LanternaLunaTui.java`、`src/test/java/com/lunacode/tui/LanternaLunaTuiStatusSnapshotTest.java`
**依赖：** T2、T14、T28
**步骤：**
1. Orchestrator 生成包含 session 和 memory 字段的状态快照。
2. TUI 渲染 session 短 ID。
3. TUI 渲染 `memory:on/off`。
4. TUI 渲染最新记忆更新状态。

**验证：** 运行 `mvn -Dtest=LanternaLunaTuiStatusSnapshotTest,LanternaLunaTuiTest test`，期望状态文本包含新增字段且旧测试不回退。

## T30: 完成应用级装配

**文件：** `src/main/java/com/lunacode/app/LunaCodeApplication.java`、相关构造调用文件
**依赖：** T13、T14、T17、T22、T23、T27、T28、T29
**步骤：**
1. 在应用启动时创建 instruction、session、memory 服务。
2. 把服务注入 `PromptContextBuilder`、`DefaultChatOrchestrator` 和 TUI 状态链路。
3. 确认启动清理、恢复结果、warning 能通过可见事件或系统消息呈现。
4. 确认不影响 provider、tool registry、permission registry 的现有装配。

**验证：** 运行 `mvn -DskipTests compile`，期望全项目编译通过。

## T31: 补齐会话模块测试

**文件：** `src/test/java/com/lunacode/session/*.java`
**依赖：** T14
**步骤：**
1. 覆盖 ID、append、scan、load、bad line。
2. 覆盖未配对 tool_use 截断。
3. 覆盖 24 小时时间跨度提醒。
4. 覆盖 30 天过期清理。
5. 覆盖 `/session resume` 后继续写入恢复会话。

**验证：** 运行 `mvn -Dtest='com.lunacode.session.*Test' test`，期望会话测试全部通过。

## T32: 补齐指令和 Prompt 测试

**文件：** `src/test/java/com/lunacode/instructions/*.java`、`src/test/java/com/lunacode/prompt/PromptContextBuilderMemoryInstructionTest.java`
**依赖：** T24
**步骤：**
1. 覆盖三层 `LUNACODE.md` 优先级。
2. 覆盖 include 行内、递归、环路、深度、越界。
3. 覆盖 Prompt 注入顺序。
4. 覆盖缺失文件和 include 失败不阻塞。

**验证：** 运行 `mvn -Dtest='com.lunacode.instructions.*Test,PromptContextBuilderMemoryInstructionTest' test`，期望全部通过。

## T33: 补齐记忆模块测试

**文件：** `src/test/java/com/lunacode/memory/*.java`
**依赖：** T28
**步骤：**
1. 覆盖四类记忆落点。
2. 覆盖 frontmatter 读写。
3. 覆盖 `MemoryIndex.md` 重建和 200 行 / 25KB 限制。
4. 覆盖自动记忆 add/update/delete/no-op。
5. 覆盖敏感值过滤和失败状态。

**验证：** 运行 `mvn -Dtest='com.lunacode.memory.*Test' test`，期望记忆测试全部通过。

## T34: 补齐 orchestrator 和 TUI 集成测试

**文件：** `src/test/java/com/lunacode/orchestrator/*.java`、`src/test/java/com/lunacode/tui/*.java`
**依赖：** T29
**步骤：**
1. 覆盖 `/session` 命令分发。
2. 覆盖 `/memory` 命令分发。
3. 覆盖 Agent 忙碌时禁止 session resume/new。
4. 覆盖 LoopComplete 后自动记忆异步触发。
5. 覆盖状态栏显示 session 和 memory。

**验证：** 运行 `mvn -Dtest='com.lunacode.orchestrator.*Test,com.lunacode.tui.*Test' test`，期望集成测试全部通过。

## T35: 全量编译和单元测试

**文件：** 全项目
**依赖：** T31、T32、T33、T34
**步骤：**
1. 运行全量 Maven 测试。
2. 修复编译错误或失败测试。
3. 如果存在环境相关跳过项，记录原因。

**验证：** 运行 `mvn test`，期望编译和单元测试全部通过。

## T36: tmux 端到端验收

**文件：** 运行中的 LunaCode、`.lunacode/sessions/`、`.lunacode/memory/`、`~/.lunacode/memory/`
**依赖：** T35
**步骤：**
1. 在 tmux 中启动 LunaCode。
2. 输入一段真实对话请求，等待 Agent 最终回复。
3. 检查 `.lunacode/sessions/` 中 JSONL 被追加。
4. 检查状态栏显示 session 短 ID、memory 开关和更新状态。
5. 重启 LunaCode，确认最近未过期会话自动恢复。
6. 使用 `/session list` 和 `/session resume <id>` 切换历史会话。
7. 使用 `/memory off` 后再发起对话，确认自动记忆不再更新。
8. 对照 `checklist.md` 逐项验收。

**验证：** 观察 tmux 输出、JSONL 文件、`MemoryIndex.md` 和 TUI 状态，确认行为符合验收清单。

## 执行顺序

```text
T1 -> T2

T3 -> T4 -> T5 -> T6 -> T7 -> T8 -> T9 -> T10 -> T11 -> T12 -> T13 -> T14

T15 -> T16 -> T17 -> T18

T19 -> T20 -> T21 -> T22

T18 + T22 -> T23 -> T24

T19 -> T25 -> T26 -> T27 -> T28

T14 + T24 + T28 -> T29 -> T30

T30 -> T31 -> T32 -> T33 -> T34 -> T35 -> T36
```