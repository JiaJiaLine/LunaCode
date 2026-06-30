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
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.interaction.BlockingPermissionConfirmationBroker;
import com.lunacode.interaction.BlockingUserQuestionBroker;
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
import com.lunacode.tool.DefaultToolPermissionGateway;
import com.lunacode.tool.DefaultToolRegistry;
import com.lunacode.tool.ToolBatchPlanner;
import com.lunacode.tool.ToolExecutor;
import com.lunacode.tool.ToolRegistry;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultChatOrchestrator implements ChatOrchestrator, AgentEventSink {
    private final ProviderConfig config;
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
    private final Path workspaceRoot;
    private volatile Path lastPlanFile;

    public DefaultChatOrchestrator(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig config,
            Runnable onChange
    ) {
        this(conversationManager, provider, config, new DefaultToolRegistry(), null, onChange, Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "lunacode-agent");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public DefaultChatOrchestrator(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig config,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            Runnable onChange
    ) {
        this(conversationManager, provider, config, toolRegistry, toolExecutor, onChange, Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "lunacode-agent");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public DefaultChatOrchestrator(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig config,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            BlockingUserQuestionBroker questionBroker,
            Runnable onChange
    ) {
        this(conversationManager, provider, config, toolRegistry, toolExecutor, questionBroker, onChange, Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "lunacode-agent");
            thread.setDaemon(true);
            return thread;
        }));
    }

    DefaultChatOrchestrator(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig config,
            Runnable onChange,
            ExecutorService executor
    ) {
        this(conversationManager, provider, config, new DefaultToolRegistry(), null, onChange, executor);
    }

    DefaultChatOrchestrator(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig config,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            Runnable onChange,
            ExecutorService executor
    ) {
        this(conversationManager, provider, config, toolRegistry, toolExecutor, new BlockingUserQuestionBroker(), onChange, executor);
    }

    DefaultChatOrchestrator(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig config,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            BlockingUserQuestionBroker questionBroker,
            Runnable onChange,
            ExecutorService executor
    ) {
        Objects.requireNonNull(conversationManager, "conversationManager");
        Objects.requireNonNull(provider, "provider");
        this.config = Objects.requireNonNull(config, "config");
        ToolRegistry safeRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.questionBroker = Objects.requireNonNull(questionBroker, "questionBroker");
        this.permissionBroker = new BlockingPermissionConfirmationBroker();
        this.onChange = onChange == null ? () -> {} : onChange;
        this.executor = Objects.requireNonNull(executor, "executor");
        this.workspaceRoot = Path.of("").toAbsolutePath().normalize();
        this.lastPlanFile = resolvePlanFile(workspaceRoot);
        this.permissionModeSession = new PermissionModeSession(config.permissions().mode());
        this.permissionRuleStore = new YamlPermissionRuleStore(workspaceRoot);
        this.status = new AtomicReference<>(StatusSnapshot.idle(config.protocol(), config.model()).withPermissionMode(permissionModeSession.currentMode()));
        this.agentLoop = createAgentLoop(conversationManager, provider, config, safeRegistry, toolExecutor);
    }

    private AgentLoop createAgentLoop(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig config,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor
    ) {
        PathSandbox pathSandbox = new DefaultPathSandbox(workspaceRoot, config.sandbox());
        SensitivePathPolicy sensitivePathPolicy = new SensitivePathPolicy();
        DefaultPermissionEngine permissionEngine = new DefaultPermissionEngine(
                permissionRuleStore,
                new PermissionTargetExtractor(pathSandbox, new BashPathScanner(), sensitivePathPolicy),
                new PermissionRuleMatcher(),
                new PermissionModePolicy(),
                new DangerousCommandBlacklist()
        );
        AgentToolRunner toolRunner = new AgentToolRunner(
                toolRegistry,
                toolExecutor,
                new ToolBatchPlanner(),
                new DefaultToolPermissionGateway(workspaceRoot, permissionEngine),
                permissionBroker,
                permissionRuleStore
        );
        AgentTurnRunner turnRunner = new AgentTurnRunner(conversationManager, provider);
        return new DefaultAgentLoop(
                conversationManager,
                config,
                toolRegistry,
                toolRunner,
                turnRunner,
                new LoopDecisionMaker(),
                new PromptContextBuilder()
        );
    }

    @Override
    public void submitUserMessage(String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        String stripped = content.strip();
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
        return status.get();
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
        return normalized.equals("y")
                || normalized.equals("yes")
                || normalized.equals("ok")
                || normalized.equals("confirm")
                || normalized.equals("确认")
                || normalized.equals("允许")
                || normalized.equals("同意");
    }

    private AgentRunConfig runConfig(AgentMode mode) {
        return new AgentRunConfig(
                workspaceRoot,
                mode,
                permissionModeSession.modeFor(mode),
                lastPlanFile,
                config.agent().maxIterations(),
                config.agent().maxConsecutiveUnknownTools(),
                Clock.systemDefaultZone()
        );
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
