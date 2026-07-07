package com.lunacode.subagent;

import com.lunacode.agent.AgentLoop;
import com.lunacode.agent.AgentRequest;
import com.lunacode.agent.DefaultAgentLoop;
import com.lunacode.agent.LoopDecisionMaker;
import com.lunacode.agent.event.AgentEvent;
import com.lunacode.agent.event.AgentEventSink;
import com.lunacode.agent.execution.AgentToolRunner;
import com.lunacode.agent.turn.AgentTurnRunner;
import com.lunacode.background.ProgressTracker;
import com.lunacode.config.ProviderConfig;
import com.lunacode.context.ContextManager;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationCompactionAccess;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.hook.HookRuntime;
import com.lunacode.hook.NoOpHookRuntime;
import com.lunacode.permission.DefaultPathSandbox;
import com.lunacode.permission.DefaultPermissionEngine;
import com.lunacode.permission.BashPathScanner;
import com.lunacode.permission.DangerousCommandBlacklist;
import com.lunacode.permission.PermissionModePolicy;
import com.lunacode.permission.PermissionRuleMatcher;
import com.lunacode.permission.PermissionRuleStore;
import com.lunacode.permission.PermissionTargetExtractor;
import com.lunacode.permission.SensitivePathPolicy;
import com.lunacode.permission.YamlPermissionRuleStore;
import com.lunacode.prompt.PromptContextBuilder;
import com.lunacode.provider.ChatProvider;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.runtime.CancellationToken;
import com.lunacode.skill.ToolAccessPolicy;
import com.lunacode.tool.DefaultToolPermissionGateway;
import com.lunacode.tool.ToolBatchPlanner;
import com.lunacode.tool.ToolExecutor;
import com.lunacode.tool.ToolRegistry;
import com.lunacode.worktree.WorktreeManager;
import com.lunacode.worktree.WorktreeRemoveRequest;
import com.lunacode.worktree.WorktreeRemoveResult;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class DefaultSubAgentRunnerFactory implements SubAgentRunnerFactory {
    private final ChatProvider provider;
    private final ProviderConfig providerConfig;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final PromptContextBuilder promptContextBuilder;
    private final HookRuntime hookRuntime;
    private final Supplier<String> sessionIdSupplier;
    private final ToolPolicyResolver toolPolicyResolver;
    private final SubAgentModelResolver modelResolver;
    private final ExecutorService executor;
    private volatile WorktreeManager worktreeManager;

    public DefaultSubAgentRunnerFactory(
            ChatProvider provider,
            ProviderConfig providerConfig,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            PromptContextBuilder promptContextBuilder,
            HookRuntime hookRuntime,
            Supplier<String> sessionIdSupplier
    ) {
        this(provider, providerConfig, toolRegistry, toolExecutor, promptContextBuilder, hookRuntime, sessionIdSupplier,
                Executors.newCachedThreadPool(r -> {
                    Thread thread = new Thread(r, "lunacode-subagent");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    public DefaultSubAgentRunnerFactory(
            ChatProvider provider,
            ProviderConfig providerConfig,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            PromptContextBuilder promptContextBuilder,
            HookRuntime hookRuntime,
            Supplier<String> sessionIdSupplier,
            ExecutorService executor
    ) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.providerConfig = Objects.requireNonNull(providerConfig, "providerConfig");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.toolExecutor = toolExecutor;
        this.promptContextBuilder = promptContextBuilder == null ? new PromptContextBuilder() : promptContextBuilder;
        this.hookRuntime = hookRuntime == null ? NoOpHookRuntime.instance() : hookRuntime;
        this.sessionIdSupplier = sessionIdSupplier == null ? () -> "" : sessionIdSupplier;
        this.toolPolicyResolver = new ToolPolicyResolver(toolRegistry);
        this.modelResolver = new SubAgentModelResolver(providerConfig.agent());
        this.executor = executor == null ? Executors.newCachedThreadPool() : executor;
    }

    public void configureWorktreeManager(WorktreeManager worktreeManager) {
        this.worktreeManager = worktreeManager;
    }
    @Override
    public SubAgentRunHandle start(SubAgentLaunchRequest request) {
        Objects.requireNonNull(request, "request");
        CancellationToken token = new CancellationToken();
        ProgressTracker progress = new ProgressTracker();
        CompletableFuture<SubAgentResult> completion = new CompletableFuture<>();
        DefaultSubAgentRunHandle handle = new DefaultSubAgentRunHandle(completion, token, progress);
        executor.submit(() -> run(request, token, progress, completion));
        return handle;
    }

    private void run(SubAgentLaunchRequest request, CancellationToken token, ProgressTracker progress, CompletableFuture<SubAgentResult> completion) {
        ConversationManager childConversation = new DefaultConversationManager();
        try {
            AgentRunConfig childConfig = childConfig(request);
            if (request.kind() == SubAgentKind.FORK) {
                copyParentHistory(request, childConversation);
            }
            AgentLoop loop = createLoop(childConversation, childConfig);
            ProgressingSink sink = new ProgressingSink(progress);
            loop.run(new AgentRequest(request.task(), childConfig), sink, token);
            SubAgentResult result = resultFrom(childConversation, progress, sink.failureReason(), childConfig.maxIterations());
            completion.complete(finalizeWorktree(request, result));
        } catch (RuntimeException e) {
            completion.complete(new SubAgentResult(
                    "子 Agent 执行失败",
                    "子 Agent 执行失败: " + e.getMessage(),
                    progress.usage(),
                    progress.toolCallCount(),
                    false,
                    Optional.of("子 Agent 执行失败: " + e.getMessage())
            ));
        }
    }

    private AgentRunConfig childConfig(SubAgentLaunchRequest request) {
        SubAgentParentContext parent = request.parentContext();
        AgentRunConfig parentConfig = parent.parentConfig();
        if (request.worktree().isPresent()) {
            parentConfig = parentConfig.withWorkDir(request.worktree().orElseThrow().path());
        }
        AgentDefinition definition = request.definition().orElse(null);
        boolean background = request.requestedBackground() || request.kind() == SubAgentKind.FORK || parent.parentIsBackground();
        boolean fork = request.kind() == SubAgentKind.FORK;
        ToolAccessPolicy toolPolicy = toolPolicyResolver.resolve(parentConfig.toolAccessPolicy(), definition, background, fork);
        AgentRunConfig child = parentConfig
                .withToolAccessPolicy(toolPolicy)
                .withModelOverride(definition == null ? parentConfig.modelOverride() : modelResolver.resolve(definition, parentConfig))
                .asSubAgent(background, fork);
        if (definition != null) {
            child = child.withSubAgentSystemPrompt(definition.systemPrompt());
            if (definition.maxTurns().isPresent()) {
                child = child.withMaxIterations(definition.maxTurns().getAsInt());
            }
            if (definition.permissionMode().isPresent()) {
                child = child.withPermissionMode(definition.permissionMode().orElseThrow());
            }
        }
        return child;
    }

    private void copyParentHistory(SubAgentLaunchRequest request, ConversationManager childConversation) {
        ConversationManager parent = request.parentContext().parentConversation();
        if (!(parent instanceof ConversationCompactionAccess parentAccess) || !(childConversation instanceof ConversationCompactionAccess childAccess)) {
            return;
        }
        List<ConversationMessageSnapshot> snapshots = parentAccess.fullSnapshot().stream()
                .filter(message -> message.status() == MessageStatus.COMPLETE)
                .toList();
        childAccess.rewriteForCompaction(snapshots);
    }

    private AgentLoop createLoop(ConversationManager conversation, AgentRunConfig childConfig) {
        PermissionRuleStore ruleStore = new YamlPermissionRuleStore(childConfig.workDir());
        DefaultPathSandbox pathSandbox = new DefaultPathSandbox(childConfig.workDir(), providerConfig.sandbox());
        DefaultPermissionEngine permissionEngine = new DefaultPermissionEngine(
                ruleStore,
                new PermissionTargetExtractor(pathSandbox, new BashPathScanner(), new SensitivePathPolicy(), providerConfig.sandbox()),
                new PermissionRuleMatcher(),
                new PermissionModePolicy(),
                new DangerousCommandBlacklist()
        );
        AgentToolRunner toolRunner = new AgentToolRunner(
                toolRegistry,
                toolExecutor,
                new ToolBatchPlanner(),
                new DefaultToolPermissionGateway(childConfig.workDir(), permissionEngine),
                new DenyingPermissionConfirmationBroker(),
                ruleStore,
                hookRuntime,
                sessionIdSupplier
        );
        return new DefaultAgentLoop(
                conversation,
                providerConfig,
                toolRegistry,
                toolRunner,
                new AgentTurnRunner(conversation, provider),
                new LoopDecisionMaker(),
                promptContextBuilder,
                ContextManager.noop(),
                hookRuntime,
                sessionIdSupplier
        );
    }

    private SubAgentResult finalizeWorktree(SubAgentLaunchRequest request, SubAgentResult result) {
        if (request.worktree().isEmpty() || worktreeManager == null) {
            return result;
        }
        var record = request.worktree().orElseThrow();
        try {
            WorktreeRemoveResult cleanup = worktreeManager.remove(WorktreeRemoveRequest.automatic(record.name()));
            if (cleanup.kept()) {
                return result.withRetainedWorktree(record.path(), record.branchName());
            }
            return result;
        } catch (RuntimeException e) {
            return new SubAgentResult(
                    result.summary(),
                    result.fullResult() + "\n\nWorktree 清理检查失败，已保留目录。\npath: " + record.path() + "\nbranch: " + record.branchName() + "\nreason: " + e.getMessage(),
                    result.usage(),
                    result.toolCallCount(),
                    result.reachedMaxTurns(),
                    result.failureReason(),
                    java.util.Optional.of(record.path().toString()),
                    java.util.Optional.of(record.branchName())
            );
        }
    }
    private SubAgentResult resultFrom(ConversationManager conversation, ProgressTracker progress, Optional<String> sinkFailure, int maxTurns) {
        List<ConversationMessageSnapshot> snapshots = conversation instanceof ConversationCompactionAccess access
                ? access.fullSnapshot()
                : conversation.snapshot().stream()
                        .map(message -> new ConversationMessageSnapshot(message.id(), message.role(), message.status(), message.timestamp(), message.usage(), message.content(), List.of(new ContentBlock.Text(message.content())), null, message.errorSummary()))
                        .toList();
        Optional<ConversationMessageSnapshot> lastAssistant = snapshots.stream()
                .filter(message -> message.role() == MessageRole.ASSISTANT)
                .max(Comparator.comparing(ConversationMessageSnapshot::timestamp));
        String content = lastAssistant.map(ConversationMessageSnapshot::content).orElse("");
        Optional<String> failure = sinkFailure;
        boolean reachedMaxTurns = failure.map(reason -> reason.contains("最大迭代次数") || reason.contains(String.valueOf(maxTurns))).orElse(false);
        if (content.isBlank() && failure.isPresent()) {
            content = failure.get();
        }
        if (content.isBlank()) {
            content = "子 Agent 没有产出最终结果。";
        }
        return new SubAgentResult(compact(content), content, progress.usage(), progress.toolCallCount(), reachedMaxTurns, failure);
    }

    private String compact(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180) + "...";
    }

    private static final class ProgressingSink implements AgentEventSink {
        private final ProgressTracker progress;
        private volatile String failureReason = "";

        private ProgressingSink(ProgressTracker progress) {
            this.progress = progress;
        }

        @Override
        public void emit(AgentEvent event) {
            if (event instanceof AgentEvent.ToolUseStarted toolUseStarted) {
                progress.recordToolCall(toolUseStarted.toolName());
            } else if (event instanceof AgentEvent.ToolResultReady toolResultReady) {
                progress.recordActivity("工具完成: " + toolResultReady.toolName());
            } else if (event instanceof AgentEvent.UsageUpdated usageUpdated) {
                progress.recordUsage(usageUpdated.cumulativeUsage());
            } else if (event instanceof AgentEvent.ErrorOccurred errorOccurred) {
                failureReason = errorOccurred.message();
                progress.recordActivity(errorOccurred.message());
            } else if (event instanceof AgentEvent.StreamText streamText) {
                if (!streamText.text().isBlank()) {
                    progress.recordActivity("模型生成中");
                }
            }
        }

        private Optional<String> failureReason() {
            return failureReason == null || failureReason.isBlank() ? Optional.empty() : Optional.of(failureReason);
        }
    }
}
