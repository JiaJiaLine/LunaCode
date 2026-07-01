# LunaCode 上下文管理 Plan

## 架构概览

本阶段新增一个独立的 `com.lunacode.context` 包，把上下文管理从 Agent Loop、工具执行器和 Provider 适配中抽出来。Agent Loop 在每次构建 PromptBundle 前调用上下文预检服务：先执行轻量工具结果外置，再估算当前上下文，必要时触发自动或强制摘要；Orchestrator 在收到 `/compact` 时调用同一套摘要服务做手动压缩。

对话历史仍由 `ConversationManager` 作为唯一写入入口管理，但需要扩展它的快照和重写能力，让上下文模块可以读取完整 blocks、替换过大的 tool_result 内容、把早期历史重写成一条摘要 user 消息并保留近期原文。普通 `toAPIFormat()` 继续负责 Provider 前的角色合并和消息序列化准备。

会话内临时数据由 `SessionContextStore` 统一写入项目目录下 `.lunacode/tmp/context/<session-id>/`。工具结果外置文件、完整会话 `session.jsonl`、摘要元数据都放在这个目录中，只保证当前会话可用。该目录不参与长期记忆，且需要加入忽略规则，避免误提交。

Token 预算由近似估算器维护。估算器使用上一次 Provider usage 作为锚点，对新增消息和恢复上下文按字符数估算 token，并计算自动压缩线和强制压缩线。压缩成功后更新估算锚点；Provider 返回 usage 后再次校准。

摘要调用复用当前 `ChatProvider` 和 `ProviderConfig`。摘要请求不携带工具声明，专用 Prompt 明确禁止工具调用，并要求模型先写分析草稿再输出正式摘要；响应解析器只取正式摘要，草稿不写回历史。

## 核心数据结构

### `ContextConfig`

```java
public record ContextConfig(
        long contextWindowTokens,
        long summaryOutputReserveTokens,
        long autoCompactMarginTokens,
        long forceCompactExtraTokens,
        int singleToolResultCharLimit,
        int toolMessageCharLimit,
        int recentTokenBudget,
        int minimumRecentMessages,
        int restoredFileLimit,
        int restoredFileTokenLimit,
        int skillDefinitionTokenBudget,
        int maxAutoSummaryFailures,
        int promptTooLongGroupRetries,
        double promptTooLongDropFraction,
        Path sessionRoot
) {
    public static ContextConfig defaults();
    public ContextBudget budget();
}
```

`ContextConfig` 挂到 `ProviderConfig`，由 `ConfigLoader` 从 `context:` 配置块解析。默认值按 spec：单工具结果 50,000 字符、单消息聚合 200,000 字符、摘要输出预留 20,000 token、自动安全余量 13,000 token、强制线额外 10,000 token、近期保留 10,000 token、至少 5 条消息、恢复 5 个文件、每文件 5,000 token、Skill 定义 25,000 token、普通自动摘要失败 3 次熔断。

### `ContextBudget`

```java
public record ContextBudget(
        long contextWindowTokens,
        long summaryOutputReserveTokens,
        long effectiveWindowTokens,
        long autoCompactThresholdTokens,
        long forceCompactThresholdTokens
) {}
```

`effectiveWindowTokens = contextWindowTokens - summaryOutputReserveTokens`，`autoCompactThresholdTokens = effectiveWindowTokens - autoCompactMarginTokens`，`forceCompactThresholdTokens = autoCompactThresholdTokens + forceCompactExtraTokens`。构造时校验所有阈值为正且顺序有效。

### `ConversationMessageSnapshot`

```java
public record ConversationMessageSnapshot(
        String id,
        MessageRole role,
        MessageStatus status,
        Instant timestamp,
        TokenUsage usage,
        String content,
        List<ContentBlock> blocks,
        ConversationMessageMetadata metadata,
        String errorSummary
) {}
```

它是压缩模块使用的完整消息快照，补上当前 `InternalMessage` 缺少的 `blocks` 和元数据。`DefaultConversationManager` 继续内部持有可变消息，向外只暴露不可变快照。

### `ConversationMessageMetadata`

```java
public record ConversationMessageMetadata(
        boolean contextSummary,
        List<ExternalizedToolResultRef> externalizedToolResults,
        Map<String, String> attributes
) {
    public static ConversationMessageMetadata empty();
}
```

`contextSummary` 用于识别旧摘要，二次压缩时把旧摘要作为早期上下文输入并在重写后只保留最新摘要。`externalizedToolResults` 记录某条工具结果消息中被外置的结果。

### `ExternalizedToolResultRef`

```java
public record ExternalizedToolResultRef(
        String messageId,
        String toolUseId,
        String toolName,
        Path path,
        int originalChars,
        int previewChars,
        boolean error
) {}
```

轻量预防写盘后返回该引用，用于更新工具结果块、写 `session.jsonl`，也用于摘要中的“外置工具结果索引”。

### `ContextPreparationRequest`

```java
public record ContextPreparationRequest(
        ProviderConfig providerConfig,
        AgentRunConfig runConfig,
        int turnIndex,
        ConversationManager conversationManager,
        ToolRegistry toolRegistry,
        ChatProvider provider,
        PromptContextBuilder promptContextBuilder,
        AgentEventSink sink,
        CompactTrigger trigger
) {}
```

`trigger` 为 `AUTO_CHECK`、`FORCE` 或 `MANUAL`。普通 Agent Loop 使用 `AUTO_CHECK`；`/compact` 使用 `MANUAL`。

### `ContextPreparationResult`

```java
public record ContextPreparationResult(
        boolean proceed,
        boolean compacted,
        CompactTrigger trigger,
        long estimatedTokensBefore,
        long estimatedTokensAfter,
        int externalizedToolResults,
        int summarizedMessages,
        int restoredFiles,
        String userVisibleMessage
) {}
```

`proceed=false` 只用于强制压缩失败这类必须停止当前请求的情况。手动 `/compact` 会把 `userVisibleMessage` 显示到 TUI。

### `RecentFileAccess`

```java
public record RecentFileAccess(
        Path path,
        String toolName,
        Instant accessedAt,
        long observedSizeBytes
) {}
```

由成功的 `ReadFile`、`WriteFile`、`EditFile` 记录生成。恢复文件时按 `accessedAt` 倒序取最多 5 个，并读取文件当前内容。

### `SummaryDraft`

```java
public record SummaryDraft(
        String finalSummary,
        int sourceMessageCount,
        int retainedMessageCount,
        List<Path> restoredFiles,
        Path sessionLogPath
) {}
```

`finalSummary` 是解析后的正式摘要，不包含模型分析草稿。

## 核心接口

### `ContextManager`

```java
public interface ContextManager {
    ContextPreparationResult prepareBeforeTurn(ContextPreparationRequest request);
    ContextPreparationResult compactManually(ContextPreparationRequest request);
    void recordToolExecutions(List<ToolExecutionRecord> records);
    void recordProviderUsage(TokenUsage usage);
}
```

`prepareBeforeTurn` 给 Agent Loop 使用，内部先执行轻量预防，再估算是否越过自动或强制阈值。`compactManually` 给 `/compact` 使用，始终尝试重量压缩较早历史。`recordToolExecutions` 维护最近访问文件；`recordProviderUsage` 用 Provider usage 校准估算器。

### `ConversationCompactionAccess`

```java
public interface ConversationCompactionAccess {
    List<ConversationMessageSnapshot> fullSnapshot();
    void replaceToolResultContent(String messageId, String toolUseId, ContentBlock.ToolResultBlock replacement, ExternalizedToolResultRef ref);
    void rewriteForCompaction(List<ConversationMessageSnapshot> rewrittenMessages);
}
```

`DefaultConversationManager` 实现该接口，保留现有 `ConversationManager` API。压缩模块只依赖该接口做重写，避免直接接触内部 `MutableMessage`。

### `LightweightToolResultExternalizer`

```java
public final class LightweightToolResultExternalizer {
    LightweightCompactionResult externalizeOversizedResults(
            List<ConversationMessageSnapshot> messages,
            ContextConfig config,
            SessionContextStore store,
            ConversationCompactionAccess conversation
    );
}
```

它只处理 `TOOL` 消息中的 `ToolResultBlock`。先处理单个超限，再处理聚合超限；替换后的工具结果正文包含摘要、预览、完整文件路径和重新读取提示。

### `ContextTokenEstimator`

```java
public interface ContextTokenEstimator {
    TokenEstimate estimate(PromptBundle bundle, ContextConfig config);
    TokenEstimate estimateMessages(List<ConversationMessageSnapshot> messages, ContextConfig config);
    void anchor(TokenUsage usage, TokenEstimate estimateAtRequest);
}
```

估算器按字符数近似估算，优先使用最近一次 `TokenUsage.inputTokens()` 校准。工具声明、system prompt、恢复文件快照和消息正文都纳入估算。

### `HistoryCompactor`

```java
public final class HistoryCompactor {
    CompactionRewrite compact(HistoryCompactionRequest request);
}
```

负责选择近期原文边界、生成摘要请求、调用模型、恢复文件和 Skill 定义、构造新摘要消息，并输出可用于 `rewriteForCompaction` 的新消息列表。

### `SummaryModelClient`

```java
public interface SummaryModelClient {
    SummaryModelResult summarize(SummaryModelRequest request);
}
```

默认实现复用当前 `ChatProvider`。摘要请求使用空工具声明，不走普通 Agent Loop，不写 assistant 消息，不触发工具执行。出现 Prompt Too Long 时返回可分类失败结果，交给 `PromptTooLongRetryPolicy` 控制重试。

### `RecentFileAccessTracker`

```java
public final class RecentFileAccessTracker {
    void record(List<ToolExecutionRecord> records, Path workspaceRoot);
    List<RecentFileAccess> recentFiles(int limit);
}
```

只记录成功工具调用，且只记录真实文件工具访问，不解析自然语言路径。

### `UsedSkillRegistry`

```java
public interface UsedSkillRegistry {
    void markUsed(String name, String definition);
    List<UsedSkillDefinition> recentDefinitions(int tokenBudget, ContextTokenEstimator estimator);
}
```

当前项目还没有独立 Skill 系统，本阶段提供接口和默认空实现。未来 Skill 入口在实际使用 Skill 时调用 `markUsed`；上下文压缩只消费已登记定义。测试可注入内存实现验证 25,000 token 预算行为。

### `SessionContextStore`

```java
public interface SessionContextStore {
    Path sessionDirectory();
    Path writeToolResult(ExternalizedToolResultPayload payload);
    Path writeSessionLog(SessionLogSnapshot snapshot);
    Path writeCompactionMetadata(CompactionMetadata metadata);
}
```

默认实现写入 `.lunacode/tmp/context/<session-id>/`。文件名使用消息 id、tool_use id 和递增序号，避免覆盖。

## 模块设计

### 配置模块

**职责：** 解析 `context:` 配置块，注入 `ProviderConfig`，校验上下文窗口和阈值。  
**对外接口：** `ProviderConfig.context()`、`ContextConfig.defaults()`、`ContextBudget`。  
**依赖：** Jackson YAML、现有 `ConfigLoader`。

配置示例：

```yaml
context:
  context_window_tokens: 200000
  summary_output_reserve_tokens: 20000
  auto_compact_margin_tokens: 13000
  force_compact_extra_tokens: 10000
  single_tool_result_char_limit: 50000
  tool_message_char_limit: 200000
```

### 对话重写模块

**职责：** 让压缩模块能读取完整 blocks、替换工具结果、重写历史。  
**对外接口：** `ConversationCompactionAccess`。  
**依赖：** `DefaultConversationManager`、`ContentBlock`、`TokenUsage`。

重写时只保留 complete/error 状态可安全表达的消息；正在 streaming 的 assistant 消息不参与压缩。摘要消息作为 `MessageRole.USER` 且带 `contextSummary=true` 元数据。

### 会话临时存储模块

**职责：** 管理 `.lunacode/tmp/context/<session-id>/`，写工具结果、完整会话记录和压缩元数据。  
**对外接口：** `SessionContextStore`。  
**依赖：** Jackson、`SensitiveValueMasker`、`ConversationMessageSnapshot`。

`session.jsonl` 以 JSON Lines 保存消息和外置结果索引。完整工具结果原文落盘；短提示和预览通过 `SensitiveValueMasker` 遮蔽敏感值。

### 轻量预防模块

**职责：** 在每次模型请求前处理过大的工具结果，减少工具结果占用。  
**对外接口：** `LightweightToolResultExternalizer.externalizeOversizedResults(...)`。  
**依赖：** `SessionContextStore`、`ConversationCompactionAccess`、`ContextConfig`。

处理顺序固定：单个结果超限先外置；单条 TOOL 消息聚合超限时，按剩余结果大小倒序继续外置，直到降到限制以内。

### 预算估算模块

**职责：** 根据 PromptBundle 和历史消息估算上下文 token，用 Provider usage 校准。  
**对外接口：** `ContextTokenEstimator`、`TokenEstimate`。  
**依赖：** `PromptBundle`、`TokenUsage`、`ContextConfig`。

估算规则为本阶段近似实现：中文和英文统一按字符数折算 token；上一次 Provider usage 作为锚点，新增内容按字符差估算。估算偏保守，并依赖 13,000 token 安全余量吸收误差。

### 历史摘要模块

**职责：** 对早期历史做结构化摘要，保留近期原文，恢复必要上下文。  
**对外接口：** `HistoryCompactor.compact(...)`。  
**依赖：** `SummaryModelClient`、`SummaryPromptBuilder`、`RecentFileAccessTracker`、`UsedSkillRegistry`、`SessionContextStore`。

保留边界从尾部向前计算，满足 10,000 token 或至少 5 条消息中更大的范围。若边界切在 assistant tool_use 和后续 tool_result 配对中间，则继续向前扩展到完整配对。旧摘要参与新摘要，重写后只保留最新摘要消息。

### 摘要模型调用模块

**职责：** 复用当前 Provider 生成正式摘要，处理 Prompt Too Long 重试。  
**对外接口：** `SummaryModelClient.summarize(...)`、`PromptTooLongRetryPolicy`。  
**依赖：** `ChatProvider`、`ProviderConfig`、`StreamEvent`。

摘要请求不携带工具声明。Provider 非 2xx、网络异常、流错误都返回结构化失败；Prompt Too Long 通过 HTTP 状态码和错误正文关键词分类。重试策略按 API 轮次分组丢弃最旧组，最多 3 次；仍失败后按 20% 消息组继续丢弃再试，直到成功或触发强制失败处理。

### 最近访问文件模块

**职责：** 记录真实文件工具成功访问，并在摘要后恢复最多 5 个当前文件快照。  
**对外接口：** `RecentFileAccessTracker`、`RestoredFileContextBuilder`。  
**依赖：** `ToolExecutionRecord`、`WorkspacePathResolver` 或工作区根目录、`ContextTokenEstimator`。

只记录 `ReadFile`、`WriteFile`、`EditFile` 的成功结果。恢复时读取文件当前内容，单文件最多约 5,000 token；读取失败时在摘要附件中写入简短失败说明，不阻断整次压缩。

### Orchestrator/TUI 命令模块

**职责：** 处理 `/compact` 本地命令，展示压缩结果。  
**对外接口：** `DefaultChatOrchestrator.submitUserMessage` 中新增 `/compact` 分支。  
**依赖：** `ContextManager`、`StatusSnapshot`、`AgentEvent`。

如果当前正在响应、等待工具权限或等待用户问题，`/compact` 返回中文提示“当前有任务运行，请结束后再压缩”。空闲时提交到 executor 执行手动压缩，完成后 TUI 打印 `Luna [info] 已压缩...`。

### Agent Loop 接入模块

**职责：** 在每轮 Provider 请求前执行上下文预检，并在工具执行后记录文件访问。  
**对外接口：** 修改 `DefaultAgentLoop` 构造参数，接收 `ContextManager`。  
**依赖：** `PromptContextBuilder`、`ToolRegistry`、`ChatProvider`、`ConversationManager`。

每轮循环开始时调用 `prepareBeforeTurn`。如果返回 `proceed=false`，Agent Loop 发出错误事件并结束本轮；否则重新构建 PromptBundle 并请求 Provider。工具执行完成后调用 `recordToolExecutions`；每次收到最终 usage 后调用 `recordProviderUsage`。

## 模块交互

```text
用户输入普通请求
  -> DefaultChatOrchestrator 创建 AgentRequest
  -> DefaultAgentLoop 每轮开始
  -> ContextManager.prepareBeforeTurn
     -> LightweightToolResultExternalizer 外置大工具结果
     -> PromptContextBuilder 构建候选 PromptBundle
     -> ContextTokenEstimator 估算 token
     -> 若超过强制线：HistoryCompactor 强制压缩
     -> 否则若超过自动线且未熔断：HistoryCompactor 自动压缩
     -> 否则直接继续
  -> DefaultAgentLoop 重新构建最终 PromptBundle
  -> ChatProvider.streamChat
  -> StreamingTurnCollector 收集 usage 和工具调用
  -> AgentToolRunner 执行工具
  -> ContextManager.recordToolExecutions 记录最近访问文件
```

```text
用户输入 /compact
  -> DefaultChatOrchestrator 识别本地命令
  -> ContextManager.compactManually
     -> 轻量预防
     -> 写 session.jsonl
     -> 选择近期原文边界
     -> 调 SummaryModelClient 生成摘要
     -> 恢复最近访问文件和已用 Skill 定义
     -> ConversationManager.rewriteForCompaction
  -> TUI 显示压缩结果
```

```text
摘要请求 Prompt Too Long
  -> SummaryModelClient 返回 PROMPT_TOO_LONG
  -> PromptTooLongRetryPolicy 按 API 轮次丢弃最旧消息组
  -> 最多 3 次分组重试
  -> 仍失败则继续丢弃 20% 消息组
  -> 成功则重写历史；强制压缩最终失败则停止当前请求
```

## 文件组织

```text
src/main/java/com/lunacode/config/
├── ContextConfig.java
├── ContextBudget.java
├── ConfigLoader.java                  # 增加 RawContext 解析
└── ProviderConfig.java                # 增加 context 字段

src/main/java/com/lunacode/conversation/
├── ConversationManager.java           # 扩展 compaction 访问方法或继承接口
├── ConversationCompactionAccess.java
├── ConversationMessageSnapshot.java
├── ConversationMessageMetadata.java
└── DefaultConversationManager.java    # 支持完整快照、工具结果替换、历史重写

src/main/java/com/lunacode/context/
├── ContextManager.java
├── DefaultContextManager.java
├── ContextPreparationRequest.java
├── ContextPreparationResult.java
├── CompactTrigger.java
├── CompactionState.java
├── TokenEstimate.java
├── ContextTokenEstimator.java
├── ApproximateContextTokenEstimator.java
├── LightweightToolResultExternalizer.java
├── LightweightCompactionResult.java
├── ExternalizedToolResultRef.java
├── ExternalizedToolResultPayload.java
├── SessionContextStore.java
├── ProjectSessionContextStore.java
├── SessionLogSnapshot.java
├── HistoryCompactor.java
├── HistoryCompactionRequest.java
├── CompactionRewrite.java
├── SummaryPromptBuilder.java
├── SummaryModelClient.java
├── ProviderSummaryModelClient.java
├── SummaryModelRequest.java
├── SummaryModelResult.java
├── SummaryResponseParser.java
├── PromptTooLongRetryPolicy.java
├── RecentFileAccess.java
├── RecentFileAccessTracker.java
├── RestoredFileContextBuilder.java
├── UsedSkillDefinition.java
├── UsedSkillRegistry.java
└── InMemoryUsedSkillRegistry.java

src/main/java/com/lunacode/agent/
└── DefaultAgentLoop.java              # 请求前预检、工具后记录、usage 校准

src/main/java/com/lunacode/orchestrator/
├── DefaultChatOrchestrator.java       # /compact 本地命令和状态展示
└── StatusSnapshot.java                # 如需增加 compacting 状态字段

src/main/java/com/lunacode/agent/event/
└── AgentEvent.java                    # 增加压缩开始/完成/失败事件

src/main/java/com/lunacode/app/
└── LunaCodeApplication.java           # 组装 ContextManager 和会话存储

.gitignore                            # 忽略 .lunacode/tmp/
config.example.yaml                    # 增加 context 配置示例
```

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 压缩入口 | Agent Loop 请求前预检 + Orchestrator `/compact` | 自动压缩和手动压缩共用核心逻辑，同时保证每次 Provider 请求前都可拦截。 |
| 存储位置 | `.lunacode/tmp/context/<session-id>/` | 位于工作区内，普通文件工具可重新读取；又明确是当前会话临时产物。 |
| 对话重写方式 | 扩展 ConversationManager 的完整快照和 rewrite 能力 | 保持对话历史单一事实来源，避免在 PromptBuilder 外另维护一份影子历史。 |
| 工具结果外置 | 替换 ToolResultBlock 内容，原文写文件 | 最大 token 来源是工具结果；替换 block 能保持 tool_use/tool_result 配对不变。 |
| 摘要消息角色 | 新增一条 `user` 摘要消息 | 符合 spec 示例，也能被现有序列化层与后续 user 消息合并处理。 |
| 近期原文边界 | 10,000 token 或 5 条消息取更大范围，并扩展到完整工具配对 | 保留最近工作细节，避免模型只靠摘要理解刚发生的工具调用。 |
| 多次压缩 | 旧摘要参与新摘要，历史中只保留最新摘要 | 防止摘要堆叠造成新的上下文噪声。 |
| 摘要模型 | 复用当前 Provider 和模型 | 本阶段不引入单独摘要模型配置，配置和鉴权更简单。 |
| 摘要工具声明 | 摘要请求不携带工具 | Prompt 明确禁止工具调用，技术上也不给工具声明，双重保证。 |
| Token 估算 | Provider usage 锚定 + 字符数增量估算 | 满足本阶段“不做精确 tokenizer”，实现成本低且有安全余量。 |
| 最近文件恢复 | 基于成功文件工具访问记录 | 避免从自然语言中误判路径，恢复内容更可靠。 |
| Skill 恢复 | 新增 UsedSkillRegistry 接口，默认可为空 | 当前代码没有完整 Skill 系统，先提供消费接口和测试注入点，不把 Skill 系统塞进本章。 |
| 强制压缩失败 | 停止当前请求 | 已接近硬上限，继续发 Provider 请求大概率失败，明确提示更可控。 |
