package com.lunacode.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.lunacode.agent.AgentLoop;
import com.lunacode.agent.AgentRequest;
import com.lunacode.agent.DefaultAgentLoop;
import com.lunacode.agent.event.AgentEvent;
import com.lunacode.agent.event.AgentEventSink;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.interaction.BlockingPermissionConfirmationBroker;
import com.lunacode.interaction.BlockingUserQuestionBroker;
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
    private final AgentLoop agentLoop;
    private final BlockingUserQuestionBroker questionBroker;
    private final BlockingPermissionConfirmationBroker permissionBroker;
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
        this.status = new AtomicReference<>(StatusSnapshot.idle(config.protocol(), config.model()));
        this.workspaceRoot = Path.of("").toAbsolutePath().normalize();
        this.lastPlanFile = resolvePlanFile(workspaceRoot);
        this.agentLoop = new DefaultAgentLoop(
                conversationManager,
                provider,
                config,
                safeRegistry,
                toolExecutor,
                new ToolBatchPlanner(),
                new DefaultToolPermissionGateway(workspaceRoot),
                permissionBroker
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
            status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), "responding", null));
            onChange.run();
            return;
        }
        if (questionBroker.hasPendingQuestion()) {
            questionBroker.answer(stripped);
            status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), "responding", null));
            onChange.run();
            return;
        }
        if ("/cancel".equalsIgnoreCase(stripped)) {
            cancelCurrentRun();
            return;
        }
        if (!responding.compareAndSet(false, true)) {
            status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), "responding", "Already responding; please wait"));
            onChange.run();
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
        status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), "responding", null));
        onChange.run();
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
        status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), "cancelled", "Cancelled by user"));
        onChange.run();
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
            status.set(new StatusSnapshot(config.protocol(), config.model(), usage.inputTokens(), usage.outputTokens(), old.state(), old.errorSummary(), old.toolName(), old.toolSummary()));
        } else if (event instanceof AgentEvent.ToolUseStarted toolUseStarted) {
            if ("AskUserQuestion".equals(toolUseStarted.toolName())) {
                String question = toolUseStarted.input().path("question").asText("Waiting for user answer");
                status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), "waiting_user", question, toolUseStarted.toolName(), summarize(toolUseStarted.input().toString())));
            } else {
                String summary = toolRunningSummary(toolUseStarted.toolName(), toolUseStarted.input());
                status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), "tool_running", summary, toolUseStarted.toolName(), summary));
            }
        } else if (event instanceof AgentEvent.PermissionRequested permissionRequested) {
            status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), "waiting_permission", permissionRequested.prompt(), permissionRequested.toolName(), permissionRequested.prompt()));
        } else if (event instanceof AgentEvent.ToolResultReady toolResultReady) {
            String state = toolResultReady.result().isError() ? "tool_error" : "tool_done";
            String summary = summarize(toolResultReady.result().content()) + " (" + toolResultReady.duration().toMillis() + "ms)";
            status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), state, toolResultReady.result().isError() ? toolResultReady.result().content() : null, toolResultReady.toolName(), summary));
        } else if (event instanceof AgentEvent.TurnComplete turnComplete) {
            status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), "responding", "turn=" + turnComplete.turnIndex()));
        } else if (event instanceof AgentEvent.LoopComplete) {
            StatusSnapshot old = status.get();
            boolean cancelled = currentToken.get() != null && currentToken.get().isCancellationRequested();
            if (cancelled) {
                status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), "cancelled", "Cancelled by user"));
            } else if ("error".equals(old.state()) || "tool_error".equals(old.state())) {
                status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), old.state(), old.errorSummary(), old.toolName(), old.toolSummary()));
            } else {
                status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), "idle", null));
            }
        } else if (event instanceof AgentEvent.ErrorOccurred errorOccurred) {
            boolean cancelled = currentToken.get() != null && currentToken.get().isCancellationRequested();
            status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), cancelled ? "cancelled" : "error", errorOccurred.message()));
        } else if (event instanceof AgentEvent.StreamText) {
            StatusSnapshot old = status.get();
            if (!"responding".equals(old.state())) {
                status.set(new StatusSnapshot(config.protocol(), config.model(), old.inputTokens(), old.outputTokens(), "responding", null, old.toolName(), old.toolSummary()));
            }
        }
        onChange.run();
    }

    private AgentRunConfig runConfig(AgentMode mode) {
        return new AgentRunConfig(
                workspaceRoot,
                mode,
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