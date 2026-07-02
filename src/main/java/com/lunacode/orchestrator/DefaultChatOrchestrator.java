package com.lunacode.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.lunacode.agent.AgentLoop;
import com.lunacode.agent.AgentRequest;
import com.lunacode.agent.DefaultAgentLoop;
import com.lunacode.agent.LoopDecisionMaker;
import com.lunacode.agent.event.AgentEvent;
import com.lunacode.agent.event.AgentEventSink;
import com.lunacode.agent.execution.AgentToolRunner;
import com.lunacode.agent.turn.AgentTurnRunner;
import com.lunacode.config.ProviderConfig;
import com.lunacode.context.CompactTrigger;
import com.lunacode.context.ContextManager;
import com.lunacode.context.ContextPreparationRequest;
import com.lunacode.context.ContextPreparationResult;
import com.lunacode.context.DefaultContextManager;
import com.lunacode.conversation.ConversationCompactionAccess;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.interaction.BlockingPermissionConfirmationBroker;
import com.lunacode.interaction.BlockingUserQuestionBroker;
import com.lunacode.memory.AutoMemoryUpdater;
import com.lunacode.memory.MemoryCommandHandler;
import com.lunacode.memory.MemoryIndexSnapshot;
import com.lunacode.memory.MemoryRuntimeState;
import com.lunacode.memory.MemoryUpdateRequest;
import com.lunacode.permission.BashPathScanner;
import com.lunacode.permission.DangerousCommandBlacklist;
import com.lunacode.permission.DefaultPathSandbox;
import com.lunacode.permission.DefaultPermissionEngine;
import com.lunacode.permission.PathSandbox;
import com.lunacode.permission.PermissionMode;
import com.lunacode.permission.PermissionModePolicy;
import com.lunacode.permission.PermissionModeSession;
import com.lunacode.permission.PermissionRuleMatcher;
import com.lunacode.permission.PermissionRuleStore;
import com.lunacode.permission.PermissionTargetExtractor;
import com.lunacode.permission.SensitivePathPolicy;
import com.lunacode.permission.YamlPermissionRuleStore;
import com.lunacode.prompt.PromptContextBuilder;
import com.lunacode.provider.ChatProvider;
import com.lunacode.runtime.AgentMode;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.runtime.CancellationToken;
import com.lunacode.session.SessionCommandHandler;
import com.lunacode.tool.DefaultToolPermissionGateway;
import com.lunacode.tool.DefaultToolRegistry;
import com.lunacode.tool.ToolBatchPlanner;
import com.lunacode.tool.ToolExecutor;
import com.lunacode.tool.ToolRegistry;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class DefaultChatOrchestrator implements ChatOrchestrator, AgentEventSink {
    private final ProviderConfig config;
    private final ConversationManager conversationManager;
    private final ChatProvider provider;
    private final ToolRegistry toolRegistry;
    private final ContextManager contextManager;
    private final PromptContextBuilder promptContextBuilder;
    private final Runnable onChange;
    private final ExecutorService executor;
    private final AtomicBoolean responding = new AtomicBoolean(false);
    private final AtomicReference<StatusSnapshot> status;
    private final AtomicReference<CancellationToken> currentToken = new AtomicReference<>();
    private final AtomicReference<PermissionMode> pendingModeSwitch = new AtomicReference<>();
    private final AgentLoop agentLoop;
    private final BlockingUserQuestionBroker questionBroker;
    private final BlockingPermissionConfirmationBroker permissionBroker;
    private final PermissionModeSession permissionModeSession;
    private final PermissionRuleStore permissionRuleStore;
    private final SessionCommandHandler sessionCommandHandler;
    private final MemoryCommandHandler memoryCommandHandler;
    private final AutoMemoryUpdater autoMemoryUpdater;
    private final MemoryRuntimeState memoryRuntimeState;
    private final Supplier<String> sessionIdSupplier;
    private final Supplier<MemoryIndexSnapshot> memoryIndexSupplier;
    private final Path workspaceRoot;
    private volatile Path lastPlanFile;
    private volatile int currentTurnStartIndex;

    public DefaultChatOrchestrator(ConversationManager conversationManager, ChatProvider provider, ProviderConfig config, Runnable onChange) {
        this(conversationManager, provider, config, new DefaultToolRegistry(), null, onChange, newAgentExecutor());
    }

    public DefaultChatOrchestrator(ConversationManager conversationManager, ChatProvider provider, ProviderConfig config, ToolRegistry toolRegistry, ToolExecutor toolExecutor, Runnable onChange) {
        this(conversationManager, provider, config, toolRegistry, toolExecutor, onChange, newAgentExecutor());
    }

    public DefaultChatOrchestrator(ConversationManager conversationManager, ChatProvider provider, ProviderConfig config, ToolRegistry toolRegistry, ToolExecutor toolExecutor, BlockingUserQuestionBroker questionBroker, Runnable onChange) {
        this(conversationManager, provider, config, toolRegistry, toolExecutor, questionBroker, null, onChange, newAgentExecutor());
    }

    public DefaultChatOrchestrator(ConversationManager conversationManager, ChatProvider provider, ProviderConfig config, ToolRegistry toolRegistry, ToolExecutor toolExecutor, BlockingUserQuestionBroker questionBroker, SessionCommandHandler sessionCommandHandler, Runnable onChange) {
        this(conversationManager, provider, config, toolRegistry, toolExecutor, questionBroker, sessionCommandHandler, onChange, newAgentExecutor());
    }

    public DefaultChatOrchestrator(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig config,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            BlockingUserQuestionBroker questionBroker,
            SessionCommandHandler sessionCommandHandler,
            MemoryCommandHandler memoryCommandHandler,
            AutoMemoryUpdater autoMemoryUpdater,
            MemoryRuntimeState memoryRuntimeState,
            Supplier<String> sessionIdSupplier,
            Supplier<MemoryIndexSnapshot> memoryIndexSupplier,
            PromptContextBuilder promptContextBuilder,
            Runnable onChange
    ) {
        this(
                conversationManager,
                provider,
                config,
                toolRegistry,
                toolExecutor,
                questionBroker,
                sessionCommandHandler,
                memoryCommandHandler,
                autoMemoryUpdater,
                memoryRuntimeState,
                sessionIdSupplier,
                memoryIndexSupplier,
                promptContextBuilder,
                onChange,
                newAgentExecutor()
        );
    }

    DefaultChatOrchestrator(ConversationManager conversationManager, ChatProvider provider, ProviderConfig config, Runnable onChange, ExecutorService executor) {
        this(conversationManager, provider, config, new DefaultToolRegistry(), null, onChange, executor);
    }

    DefaultChatOrchestrator(ConversationManager conversationManager, ChatProvider provider, ProviderConfig config, ToolRegistry toolRegistry, ToolExecutor toolExecutor, Runnable onChange, ExecutorService executor) {
        this(conversationManager, provider, config, toolRegistry, toolExecutor, new BlockingUserQuestionBroker(), null, onChange, executor);
    }

    DefaultChatOrchestrator(ConversationManager conversationManager, ChatProvider provider, ProviderConfig config, ToolRegistry toolRegistry, ToolExecutor toolExecutor, BlockingUserQuestionBroker questionBroker, Runnable onChange, ExecutorService executor) {
        this(conversationManager, provider, config, toolRegistry, toolExecutor, questionBroker, null, onChange, executor);
    }

    DefaultChatOrchestrator(ConversationManager conversationManager, ChatProvider provider, ProviderConfig config, ToolRegistry toolRegistry, ToolExecutor toolExecutor, BlockingUserQuestionBroker questionBroker, SessionCommandHandler sessionCommandHandler, Runnable onChange, ExecutorService executor) {
        this(
                conversationManager,
                provider,
                config,
                toolRegistry,
                toolExecutor,
                questionBroker,
                sessionCommandHandler,
                null,
                null,
                null,
                null,
                null,
                null,
                onChange,
                executor
        );
    }

    DefaultChatOrchestrator(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig config,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            BlockingUserQuestionBroker questionBroker,
            SessionCommandHandler sessionCommandHandler,
            MemoryCommandHandler memoryCommandHandler,
            AutoMemoryUpdater autoMemoryUpdater,
            MemoryRuntimeState memoryRuntimeState,
            Supplier<String> sessionIdSupplier,
            Supplier<MemoryIndexSnapshot> memoryIndexSupplier,
            PromptContextBuilder promptContextBuilder,
            Runnable onChange,
            ExecutorService executor
    ) {
        this.conversationManager = Objects.requireNonNull(conversationManager, "conversationManager");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.config = Objects.requireNonNull(config, "config");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.questionBroker = Objects.requireNonNull(questionBroker, "questionBroker");
        this.sessionCommandHandler = sessionCommandHandler;
        this.memoryCommandHandler = memoryCommandHandler;
        this.autoMemoryUpdater = autoMemoryUpdater;
        this.memoryRuntimeState = memoryRuntimeState;
        this.sessionIdSupplier = sessionIdSupplier;
        this.memoryIndexSupplier = memoryIndexSupplier;
        this.permissionBroker = new BlockingPermissionConfirmationBroker();
        this.onChange = onChange == null ? () -> {} : onChange;
        this.executor = Objects.requireNonNull(executor, "executor");
        this.workspaceRoot = Path.of("").toAbsolutePath().normalize();
        this.promptContextBuilder = promptContextBuilder == null ? new PromptContextBuilder() : promptContextBuilder;
        this.contextManager = DefaultContextManager.createDefault(workspaceRoot, config.context(), new com.lunacode.tool.SensitiveValueMasker(java.util.List.of(config.apiKey())));
        this.lastPlanFile = resolvePlanFile(workspaceRoot);
        this.permissionModeSession = new PermissionModeSession(config.permissions().mode());
        this.permissionRuleStore = new YamlPermissionRuleStore(workspaceRoot);
        this.status = new AtomicReference<>(StatusSnapshot.idle(config.protocol(), config.model()).withPermissionMode(permissionModeSession.currentMode()));
        this.agentLoop = createAgentLoop(this.conversationManager, this.provider, config, this.toolRegistry, toolExecutor);
    }

    private static ExecutorService newAgentExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "lunacode-agent");
            thread.setDaemon(true);
            return thread;
        });
    }

    private AgentLoop createAgentLoop(ConversationManager conversationManager, ChatProvider provider, ProviderConfig config, ToolRegistry toolRegistry, ToolExecutor toolExecutor) {
        PathSandbox pathSandbox = new DefaultPathSandbox(workspaceRoot, config.sandbox());
        SensitivePathPolicy sensitivePathPolicy = new SensitivePathPolicy();
        DefaultPermissionEngine permissionEngine = new DefaultPermissionEngine(permissionRuleStore, new PermissionTargetExtractor(pathSandbox, new BashPathScanner(), sensitivePathPolicy), new PermissionRuleMatcher(), new PermissionModePolicy(), new DangerousCommandBlacklist());
        AgentToolRunner toolRunner = new AgentToolRunner(toolRegistry, toolExecutor, new ToolBatchPlanner(), new DefaultToolPermissionGateway(workspaceRoot, permissionEngine), permissionBroker, permissionRuleStore);
        AgentTurnRunner turnRunner = new AgentTurnRunner(conversationManager, provider);
        return new DefaultAgentLoop(conversationManager, config, toolRegistry, toolRunner, turnRunner, new LoopDecisionMaker(), promptContextBuilder, contextManager);
    }

    @Override
    public void submitUserMessage(String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        String stripped = content.strip();
        if ("/compact".equalsIgnoreCase(stripped)) {
            handleCompactCommand();
            return;
        }
        if (sessionCommandHandler != null && sessionCommandHandler.matches(stripped)) {
            boolean busy = responding.get()
                    || permissionBroker.hasPendingConfirmation()
                    || questionBroker.hasPendingQuestion()
                    || pendingModeSwitch.get() != null;
            SessionCommandHandler.CommandResult result = sessionCommandHandler.handle(stripped, busy);
            setStatus(result.state(), result.message());
            return;
        }
        if (memoryCommandHandler != null && memoryCommandHandler.matches(stripped)) {
            MemoryCommandHandler.CommandResult result = memoryCommandHandler.handle(stripped);
            setStatus(result.state(), result.message());
            return;
        }
        if (permissionBroker.hasPendingConfirmation()) {
            permissionBroker.answer(stripped);
            setStatus("responding", null);
            return;
        }
        if (questionBroker.hasPendingQuestion()) {
            questionBroker.answer(stripped);
            setStatus("responding", null);
            return;
        }
        if (pendingModeSwitch.get() != null) {
            answerModeSwitch(stripped);
            return;
        }
        if ("/cancel".equalsIgnoreCase(stripped)) {
            cancelCurrentRun();
            return;
        }
        if (stripped.startsWith("/permissions")) {
            handlePermissionCommand(stripped);
            return;
        }
        if (!responding.compareAndSet(false, true)) {
            setStatus("responding", "Already responding; please wait");
            return;
        }
        AgentMode mode = AgentMode.DEFAULT;
        String userMessage = stripped;
        if (stripped.startsWith("/plan")) {
            mode = AgentMode.PLAN;
            userMessage = stripped.substring("/plan".length()).strip();
            if (userMessage.isBlank()) {
                userMessage = "Enter plan mode. Clarify requirements first, then write a plan.";
            }
            lastPlanFile = resolvePlanFile(workspaceRoot);
        } else if (stripped.startsWith("/do")) {
            userMessage = stripped.substring("/do".length()).strip();
            if (userMessage.isBlank()) {
                userMessage = "Continue execution using plan file: " + lastPlanFile;
            } else {
                userMessage = userMessage + "\n\nUse plan file as context: " + lastPlanFile;
            }
        }
        currentTurnStartIndex = fullSnapshotSize();
        CancellationToken token = new CancellationToken();
        currentToken.set(token);
        setStatus("responding", null);
        AgentRequest request = new AgentRequest(userMessage, runConfig(mode));
        executor.submit(() -> {
            try {
                agentLoop.run(request, this, token);
            } finally {
                responding.set(false);
                currentToken.compareAndSet(token, null);
                onChange.run();
            }
        });
    }

    @Override
    public void cancelCurrentRun() {
        CancellationToken token = currentToken.get();
        if (token != null) {
            token.cancel();
        }
        questionBroker.cancelPending();
        permissionBroker.cancelPending();
        pendingModeSwitch.set(null);
        setStatus("cancelled", "Cancelled by user");
    }

    @Override
    public StatusSnapshot status() {
        return enrich(status.get());
    }

    @Override
    public void emit(AgentEvent event) {
        if (event instanceof AgentEvent.UsageUpdated usageUpdated) {
            TokenUsage usage = usageUpdated.cumulativeUsage();
            StatusSnapshot old = status.get();
            status.set(new StatusSnapshot(config.protocol(), config.model(), usage.inputTokens(), usage.outputTokens(), old.state(), old.errorSummary(), old.toolName(), old.toolSummary(), permissionModeSession.currentMode()));
        } else if (event instanceof AgentEvent.ToolUseStarted toolUseStarted) {
            if ("AskUserQuestion".equals(toolUseStarted.toolName())) {
                String question = toolUseStarted.input().path("question").asText("Waiting for user answer");
                setStatus("waiting_user", question, toolUseStarted.toolName(), summarize(toolUseStarted.input().toString()));
            } else {
                String summary = toolRunningSummary(toolUseStarted.toolName(), toolUseStarted.input());
                setStatus("tool_running", summary, toolUseStarted.toolName(), summary);
            }
        } else if (event instanceof AgentEvent.PermissionRequested permissionRequested) {
            setStatus("waiting_permission", permissionRequested.prompt(), permissionRequested.toolName(), permissionRequested.prompt());
        } else if (event instanceof AgentEvent.PermissionModeChanged permissionModeChanged) {
            permissionModeSession.setCurrentMode(permissionModeChanged.mode());
            setStatus("idle", "权限模式已切换为 " + permissionModeChanged.mode().configValue());
        } else if (event instanceof AgentEvent.PermissionRuleWarning warning) {
            setStatus("warning", warning.message());
        } else if (event instanceof AgentEvent.ToolResultReady toolResultReady) {
            String state = toolResultReady.result().isError() ? "tool_error" : "tool_done";
            String summary = summarize(toolResultReady.result().content()) + " (" + toolResultReady.duration().toMillis() + "ms)";
            setStatus(state, toolResultReady.result().isError() ? toolResultReady.result().content() : null, toolResultReady.toolName(), summary);
        } else if (event instanceof AgentEvent.CompactionStarted compactionStarted) {
            setStatus("compacting", "Luna 正在压缩上下文，估算 token=" + compactionStarted.estimatedTokensBefore());
        } else if (event instanceof AgentEvent.CompactionCompleted compactionCompleted) {
            setStatus("responding", "上下文压缩完成：覆盖消息 " + compactionCompleted.summarizedMessages()
                    + " 条，外置工具结果 " + compactionCompleted.externalizedToolResults()
                    + " 个，恢复文件 " + compactionCompleted.restoredFiles() + " 个");
        } else if (event instanceof AgentEvent.CompactionFailed compactionFailed) {
            setStatus("warning", compactionFailed.reason());
        } else if (event instanceof AgentEvent.TurnComplete turnComplete) {
            setStatus("responding", "turn=" + turnComplete.turnIndex());
        } else if (event instanceof AgentEvent.LoopComplete) {
            StatusSnapshot old = status.get();
            boolean cancelled = currentToken.get() != null && currentToken.get().isCancellationRequested();
            if (cancelled) {
                setStatus("cancelled", "Cancelled by user");
            } else if ("error".equals(old.state()) || "tool_error".equals(old.state())) {
                status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), old.state(), old.errorSummary(), old.toolName(), old.toolSummary(), permissionModeSession.currentMode()));
            } else {
                setStatus("idle", null);
                triggerAutoMemoryUpdate();
            }
        } else if (event instanceof AgentEvent.ErrorOccurred errorOccurred) {
            boolean cancelled = currentToken.get() != null && currentToken.get().isCancellationRequested();
            setStatus(cancelled ? "cancelled" : "error", errorOccurred.message());
        } else if (event instanceof AgentEvent.StreamText) {
            StatusSnapshot old = status.get();
            if (!"responding".equals(old.state())) {
                status.set(new StatusSnapshot(config.protocol(), config.model(), old.inputTokens(), old.outputTokens(), "responding", null, old.toolName(), old.toolSummary(), permissionModeSession.currentMode()));
            }
        }
        onChange.run();
    }

    private void handleCompactCommand() {
        if (permissionBroker.hasPendingConfirmation() || questionBroker.hasPendingQuestion() || pendingModeSwitch.get() != null || responding.get()) {
            setStatus("warning", "当前有任务运行，请结束后再压缩上下文");
            return;
        }
        if (!responding.compareAndSet(false, true)) {
            setStatus("warning", "当前有任务运行，请结束后再压缩上下文");
            return;
        }
        setStatus("compacting", "Luna 正在压缩上下文...");
        executor.submit(() -> {
            try {
                ContextPreparationResult result = contextManager.compactManually(new ContextPreparationRequest(config, runConfig(AgentMode.DEFAULT), 0, conversationManager, toolRegistry, provider, promptContextBuilder, this, CompactTrigger.MANUAL));
                String message = result.userVisibleMessage() == null ? "上下文压缩已完成" : result.userVisibleMessage();
                setStatus(result.compacted() ? "idle" : "warning", message);
            } finally {
                responding.set(false);
                onChange.run();
            }
        });
    }

    private void handlePermissionCommand(String stripped) {
        String[] parts = stripped.split("\\s+");
        if (parts.length == 1) {
            setStatus("idle", "当前权限模式: " + permissionModeSession.currentMode().configValue());
            return;
        }
        if (parts.length > 2) {
            setStatus("error", "用法: /permissions [default|acceptEdits|plan|bypassPermissions]");
            return;
        }
        PermissionMode mode;
        try {
            mode = PermissionMode.fromConfig(parts[1]);
        } catch (IllegalArgumentException e) {
            setStatus("error", "未知权限模式: " + parts[1]);
            return;
        }
        if (mode == PermissionMode.BYPASS_PERMISSIONS) {
            pendingModeSwitch.set(mode);
            setStatus("waiting_permission", "bypassPermissions 是危险模式。输入 yes/确认 切换，其他输入取消。", "permissions", "切换到 bypassPermissions");
            return;
        }
        permissionModeSession.setCurrentMode(mode);
        setStatus("idle", "权限模式已切换为 " + mode.configValue());
    }

    private void answerModeSwitch(String answer) {
        PermissionMode mode = pendingModeSwitch.getAndSet(null);
        if (mode == null) {
            return;
        }
        if (isApproval(answer)) {
            permissionModeSession.setCurrentMode(mode);
            setStatus("idle", "权限模式已切换为 " + mode.configValue());
        } else {
            setStatus("idle", "已取消切换权限模式，当前仍为 " + permissionModeSession.currentMode().configValue());
        }
    }

    private boolean isApproval(String answer) {
        String normalized = answer == null ? "" : answer.strip().toLowerCase(Locale.ROOT);
        return normalized.equals("y") || normalized.equals("yes") || normalized.equals("ok") || normalized.equals("confirm") || normalized.equals("确认") || normalized.equals("允许") || normalized.equals("同意");
    }

    private AgentRunConfig runConfig(AgentMode mode) {
        return new AgentRunConfig(workspaceRoot, mode, permissionModeSession.modeFor(mode), lastPlanFile, config.agent().maxIterations(), config.agent().maxConsecutiveUnknownTools(), Clock.systemDefaultZone());
    }

    private Path resolvePlanFile(Path root) {
        Path configured = config.agent().planFile();
        return configured.isAbsolute() ? configured.toAbsolutePath().normalize() : root.resolve(configured).normalize();
    }

    private TokenUsage tokens() {
        StatusSnapshot current = status.get();
        return new TokenUsage(current.inputTokens(), current.outputTokens(), null);
    }

    private void setStatus(String state, String errorSummary) {
        status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), state, errorSummary, null, null, permissionModeSession.currentMode()));
        onChange.run();
    }

    private void setStatus(String state, String errorSummary, String toolName, String toolSummary) {
        status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), state, errorSummary, toolName, toolSummary, permissionModeSession.currentMode()));
        onChange.run();
    }

    private int fullSnapshotSize() {
        if (conversationManager instanceof ConversationCompactionAccess access) {
            return access.fullSnapshot().size();
        }
        return conversationManager.snapshot().size();
    }

    private void triggerAutoMemoryUpdate() {
        if (autoMemoryUpdater == null) {
            return;
        }
        if (!(conversationManager instanceof ConversationCompactionAccess access)) {
            return;
        }
        List<ConversationMessageSnapshot> snapshots = access.fullSnapshot();
        int start = Math.max(0, Math.min(currentTurnStartIndex, snapshots.size()));
        List<ConversationMessageSnapshot> delta = snapshots.subList(start, snapshots.size()).stream()
                .filter(message -> message.status() == MessageStatus.COMPLETE)
                .toList();
        if (delta.isEmpty()) {
            return;
        }
        autoMemoryUpdater.updateAsync(new MemoryUpdateRequest(currentSessionId(), delta, currentMemoryIndex(), Instant.now()));
        onChange.run();
    }

    private MemoryIndexSnapshot currentMemoryIndex() {
        if (memoryIndexSupplier == null) {
            return MemoryIndexSnapshot.empty();
        }
        try {
            MemoryIndexSnapshot snapshot = memoryIndexSupplier.get();
            return snapshot == null ? MemoryIndexSnapshot.empty() : snapshot;
        } catch (RuntimeException e) {
            return MemoryIndexSnapshot.empty();
        }
    }

    private String currentSessionId() {
        if (sessionIdSupplier == null) {
            return "";
        }
        try {
            String id = sessionIdSupplier.get();
            return id == null ? "" : id;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private StatusSnapshot enrich(StatusSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.withSessionAndMemory(shortSessionId(), memoryAutoUpdateEnabled(), memoryLatestState());
    }

    private String shortSessionId() {
        String id = currentSessionId();
        if (id.isBlank()) {
            return null;
        }
        if (id.length() <= 20) {
            return id;
        }
        return id.substring(0, Math.min(15, id.length())) + "-" + id.substring(id.length() - 4);
    }

    private Boolean memoryAutoUpdateEnabled() {
        return memoryRuntimeState == null ? null : memoryRuntimeState.autoUpdateEnabled();
    }

    private String memoryLatestState() {
        return memoryRuntimeState == null ? null : memoryRuntimeState.latestState();
    }

    private String toolRunningSummary(String toolName, JsonNode input) {
        String safeToolName = toolName == null || toolName.isBlank() ? "UnknownTool" : toolName;
        return switch (safeToolName) {
            case "WriteFile" -> "Luna正在使用\"" + safeToolName + "\"工具写入\"" + compact(text(input, "path", "file_path"), "未知文件") + "\"";
            case "EditFile" -> "Luna正在使用\"" + safeToolName + "\"工具修改\"" + compact(text(input, "path", "file_path"), "未知文件") + "\"";
            case "ReadFile" -> "Luna正在使用\"" + safeToolName + "\"工具读取\"" + compact(text(input, "path", "file_path"), "未知文件") + "\"";
            case "Bash" -> "Luna正在使用\"" + safeToolName + "\"工具执行\"" + compact(text(input, "command"), "未知命令") + "\"";
            case "Glob" -> "Luna正在使用\"" + safeToolName + "\"工具查找\"" + compact(text(input, "pattern"), "未知模式") + "\"";
            case "Grep" -> grepSummary(safeToolName, input);
            default -> "Luna正在使用\"" + safeToolName + "\"工具处理\"" + compact(input == null ? null : input.toString(), "请求") + "\"";
        };
    }

    private String grepSummary(String toolName, JsonNode input) {
        String pattern = compact(text(input, "pattern"), "未知模式");
        String path = compact(text(input, "path"), "");
        if (path.isBlank()) {
            return "Luna正在使用\"" + toolName + "\"工具搜索\"" + pattern + "\"";
        }
        return "Luna正在使用\"" + toolName + "\"工具在\"" + path + "\"中搜索\"" + pattern + "\"";
    }

    private String text(JsonNode input, String... names) {
        if (input == null || names == null) {
            return null;
        }
        for (String name : names) {
            if (input.hasNonNull(name) && !input.path(name).asText().isBlank()) {
                return input.path(name).asText();
            }
        }
        return null;
    }

    private String compact(String value, String fallback) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        if (normalized.isBlank()) {
            normalized = fallback;
        }
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private String summarize(String value) {
        if (value == null) {
            return null;
        }
        String oneLine = value.replace('\n', ' ').replace('\r', ' ').strip();
        return oneLine.length() <= 160 ? oneLine : oneLine.substring(0, 160) + "...";
    }
}
