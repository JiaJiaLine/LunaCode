package com.lunacode.context;

import com.lunacode.agent.event.AgentEvent;
import com.lunacode.config.ContextBudget;
import com.lunacode.config.ContextConfig;
import com.lunacode.conversation.ConversationCompactionAccess;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.hook.HookContext;
import com.lunacode.hook.HookEventName;
import com.lunacode.hook.HookExecutionScope;
import com.lunacode.hook.HookRuntime;
import com.lunacode.hook.NoOpHookRuntime;
import com.lunacode.prompt.PromptBundle;
import com.lunacode.tool.ToolExecutionRecord;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class DefaultContextManager implements ContextManager {
    private final SessionContextStore store;
    private final ContextTokenEstimator estimator;
    private final LightweightToolResultExternalizer externalizer;
    private final HistoryCompactor historyCompactor;
    private final SummaryModelClient summaryModelClient;
    private final RecentFileAccessTracker recentFileAccessTracker;
    private final UsedSkillRegistry usedSkillRegistry;
    private final CompactionState state;
    private final HookRuntime hookRuntime;
    private final Supplier<String> sessionIdSupplier;
    private volatile TokenEstimate lastEstimate = new TokenEstimate(0, 0, "none");

    public DefaultContextManager(
            SessionContextStore store,
            ContextTokenEstimator estimator,
            LightweightToolResultExternalizer externalizer,
            HistoryCompactor historyCompactor,
            SummaryModelClient summaryModelClient,
            RecentFileAccessTracker recentFileAccessTracker,
            UsedSkillRegistry usedSkillRegistry,
            CompactionState state
    ) {
        this(store, estimator, externalizer, historyCompactor, summaryModelClient, recentFileAccessTracker, usedSkillRegistry, state, NoOpHookRuntime.instance(), () -> "");
    }

    public DefaultContextManager(
            SessionContextStore store,
            ContextTokenEstimator estimator,
            LightweightToolResultExternalizer externalizer,
            HistoryCompactor historyCompactor,
            SummaryModelClient summaryModelClient,
            RecentFileAccessTracker recentFileAccessTracker,
            UsedSkillRegistry usedSkillRegistry,
            CompactionState state,
            HookRuntime hookRuntime,
            Supplier<String> sessionIdSupplier
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.estimator = Objects.requireNonNull(estimator, "estimator");
        this.externalizer = Objects.requireNonNull(externalizer, "externalizer");
        this.historyCompactor = Objects.requireNonNull(historyCompactor, "historyCompactor");
        this.summaryModelClient = Objects.requireNonNull(summaryModelClient, "summaryModelClient");
        this.recentFileAccessTracker = Objects.requireNonNull(recentFileAccessTracker, "recentFileAccessTracker");
        this.usedSkillRegistry = Objects.requireNonNull(usedSkillRegistry, "usedSkillRegistry");
        this.state = Objects.requireNonNull(state, "state");
        this.hookRuntime = hookRuntime == null ? NoOpHookRuntime.instance() : hookRuntime;
        this.sessionIdSupplier = sessionIdSupplier == null ? () -> "" : sessionIdSupplier;
    }

    public static DefaultContextManager createDefault(Path workspaceRoot, ContextConfig config, com.lunacode.tool.SensitiveValueMasker masker) {
        return createDefault(workspaceRoot, config, masker, NoOpHookRuntime.instance(), () -> "");
    }

    public static DefaultContextManager createDefault(Path workspaceRoot, ContextConfig config, com.lunacode.tool.SensitiveValueMasker masker, HookRuntime hookRuntime, Supplier<String> sessionIdSupplier) {
        return new DefaultContextManager(
                new ProjectSessionContextStore(workspaceRoot, config.sessionRoot(), masker),
                new ApproximateContextTokenEstimator(),
                new LightweightToolResultExternalizer(),
                new HistoryCompactor(),
                new ProviderSummaryModelClient(),
                new RecentFileAccessTracker(),
                new InMemoryUsedSkillRegistry(),
                new CompactionState(),
                hookRuntime,
                sessionIdSupplier
        );
    }

    @Override
    public ContextPreparationResult prepareBeforeTurn(ContextPreparationRequest request) {
        ContextConfig config = request.providerConfig().context();
        ConversationCompactionAccess conversation = compactionAccess(request);
        if (conversation == null) {
            return ContextPreparationResult.proceed(request.trigger());
        }
        LightweightCompactionResult lightweight = externalizer.externalizeOversizedResults(
                conversation.fullSnapshot(),
                config,
                store,
                conversation
        );
        PromptBundle bundle = request.promptContextBuilder().build(
                request.runConfig(),
                request.turnIndex(),
                request.conversationManager().toAPIFormat(),
                request.toolRegistry().declarationsForModel(request.runConfig().mode())
        );
        TokenEstimate before = estimator.estimate(bundle, config);
        lastEstimate = before;
        ContextBudget budget = config.budget();
        if (before.estimatedTokens() >= budget.forceCompactThresholdTokens()) {
            return runCompaction(request, conversation, CompactTrigger.FORCE, before, lightweight.externalizedCount());
        }
        if (before.estimatedTokens() >= budget.autoCompactThresholdTokens()) {
            if (state.autoCompactionFused()) {
                String message = "自动上下文压缩已熔断，请输入 /compact 或手动减少上下文。";
                emit(request, new AgentEvent.CompactionFailed(CompactTrigger.AUTO_CHECK, before.estimatedTokens(), message));
                emitHook(HookEventName.ERROR, request, message);
                return new ContextPreparationResult(true, false, CompactTrigger.AUTO_CHECK, before.estimatedTokens(), before.estimatedTokens(), lightweight.externalizedCount(), 0, 0, message);
            }
            return runCompaction(request, conversation, CompactTrigger.AUTO_CHECK, before, lightweight.externalizedCount());
        }
        return new ContextPreparationResult(true, false, CompactTrigger.AUTO_CHECK, before.estimatedTokens(), before.estimatedTokens(), lightweight.externalizedCount(), 0, 0, null);
    }

    @Override
    public ContextPreparationResult compactManually(ContextPreparationRequest request) {
        ConversationCompactionAccess conversation = compactionAccess(request);
        if (conversation == null) {
            return new ContextPreparationResult(true, false, CompactTrigger.MANUAL, 0, 0, 0, 0, 0, "当前对话管理器不支持上下文压缩。");
        }
        ContextConfig config = request.providerConfig().context();
        LightweightCompactionResult lightweight = externalizer.externalizeOversizedResults(
                conversation.fullSnapshot(),
                config,
                store,
                conversation
        );
        TokenEstimate before = estimator.estimateMessages(conversation.fullSnapshot(), config);
        return runCompaction(request, conversation, CompactTrigger.MANUAL, before, lightweight.externalizedCount());
    }

    @Override
    public void recordToolExecutions(List<ToolExecutionRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Path workspaceRoot = Path.of("").toAbsolutePath().normalize();
        recentFileAccessTracker.record(records, workspaceRoot);
    }

    @Override
    public void recordProviderUsage(TokenUsage usage) {
        estimator.anchor(usage, lastEstimate);
    }

    private ContextPreparationResult runCompaction(
            ContextPreparationRequest request,
            ConversationCompactionAccess conversation,
            CompactTrigger trigger,
            TokenEstimate before,
            int externalizedCount
    ) {
        emitHook(HookEventName.COMPACT, request, "compact " + trigger.name().toLowerCase());
        emit(request, new AgentEvent.CompactionStarted(trigger, before.estimatedTokens()));
        CompactionRewrite rewrite = historyCompactor.compact(new HistoryCompactionRequest(
                conversation.fullSnapshot(),
                request.providerConfig(),
                request.runConfig(),
                request.provider(),
                request.providerConfig().context(),
                store,
                recentFileAccessTracker,
                usedSkillRegistry,
                summaryModelClient,
                trigger
        ));
        if (!rewrite.success()) {
            if (trigger == CompactTrigger.AUTO_CHECK) {
                state.recordAutoFailure(request.providerConfig().context());
            }
            String message = failureMessage(trigger, rewrite.failureReason());
            emit(request, new AgentEvent.CompactionFailed(trigger, before.estimatedTokens(), message));
            emitHook(HookEventName.ERROR, request, message);
            boolean proceed = trigger != CompactTrigger.FORCE;
            return new ContextPreparationResult(proceed, false, trigger, before.estimatedTokens(), before.estimatedTokens(), externalizedCount, 0, 0, message);
        }
        conversation.rewriteForCompaction(rewrite.rewrittenMessages());
        state.recordSuccess();
        TokenEstimate after = estimator.estimateMessages(conversation.fullSnapshot(), request.providerConfig().context());
        String message = "上下文压缩完成：覆盖消息 " + rewrite.summarizedMessages()
                + " 条，外置工具结果 " + externalizedCount
                + " 个，恢复文件 " + rewrite.restoredFiles()
                + " 个。";
        emit(request, new AgentEvent.CompactionCompleted(
                trigger,
                before.estimatedTokens(),
                after.estimatedTokens(),
                externalizedCount,
                rewrite.summarizedMessages(),
                rewrite.restoredFiles()
        ));
        return new ContextPreparationResult(true, true, trigger, before.estimatedTokens(), after.estimatedTokens(), externalizedCount, rewrite.summarizedMessages(), rewrite.restoredFiles(), message);
    }

    private String failureMessage(CompactTrigger trigger, String reason) {
        String detail = reason == null || reason.isBlank() ? "未知原因" : reason;
        if (trigger == CompactTrigger.FORCE) {
            return "强制上下文压缩失败，已停止当前请求，请手动处理上下文。原因：" + detail;
        }
        if (trigger == CompactTrigger.MANUAL) {
            return "手动上下文压缩失败：" + detail;
        }
        if (state.autoCompactionFused()) {
            return "自动上下文压缩连续失败已熔断，请输入 /compact 或手动处理上下文。最后原因：" + detail;
        }
        return "自动上下文压缩失败：" + detail;
    }

    private ConversationCompactionAccess compactionAccess(ContextPreparationRequest request) {
        if (request.conversationManager() instanceof ConversationCompactionAccess access) {
            return access;
        }
        return null;
    }

    private void emit(ContextPreparationRequest request, AgentEvent event) {
        if (request.sink() != null) {
            request.sink().emit(event);
        }
    }

    private void emitHook(HookEventName event, ContextPreparationRequest request, String message) {
        hookRuntime.emit(event, new HookContext(event.yamlName(), "", java.util.Map.of(), "", message, event == HookEventName.ERROR ? message : ""), scope(request));
    }

    private HookExecutionScope scope(ContextPreparationRequest request) {
        String sessionId;
        try {
            sessionId = sessionIdSupplier.get();
        } catch (RuntimeException e) {
            sessionId = "";
        }
        return new HookExecutionScope(sessionId, request.turnIndex(), request.runConfig().workDir());
    }
}