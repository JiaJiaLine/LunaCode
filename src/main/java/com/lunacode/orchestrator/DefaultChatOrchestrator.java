package com.lunacode.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.lunacode.background.BackgroundTaskManager;
import com.lunacode.background.ForegroundSubAgentTracker;
import com.lunacode.background.TaskNotificationFormatter;
import com.lunacode.agent.AgentLoop;
import com.lunacode.agent.AgentRequest;
import com.lunacode.agent.DefaultAgentLoop;
import com.lunacode.agent.LoopDecisionMaker;
import com.lunacode.agent.event.AgentEvent;
import com.lunacode.agent.event.AgentEventSink;
import com.lunacode.agent.execution.AgentToolRunner;
import com.lunacode.agent.turn.AgentTurnRunner;
import com.lunacode.command.BuiltinSlashCommands;
import com.lunacode.command.CommandRuntime;
import com.lunacode.command.CommandRuntimeStatus;
import com.lunacode.command.CommandUiController;
import com.lunacode.command.DispatchResult;
import com.lunacode.command.SlashCommandCompleter;
import com.lunacode.command.SlashCommandCompletion;
import com.lunacode.command.SlashCommandDispatcher;
import com.lunacode.command.SlashCommandParser;
import com.lunacode.command.SlashCommandRegistry;
import com.lunacode.command.SkillCommandRegistrar;
import com.lunacode.config.ProviderConfig;
import com.lunacode.context.CompactTrigger;
import com.lunacode.context.ContextManager;
import com.lunacode.context.ContextPreparationRequest;
import com.lunacode.context.ContextPreparationResult;
import com.lunacode.context.DefaultContextManager;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationCompactionAccess;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.hook.HookRuntime;
import com.lunacode.hook.NoOpHookRuntime;
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
import com.lunacode.skill.DefaultSkillForkRunner;
import com.lunacode.skill.LoadedSkillContext;
import com.lunacode.skill.SkillCatalog;
import com.lunacode.skill.SkillExecutionMode;
import com.lunacode.skill.SkillForkResult;
import com.lunacode.skill.SkillForkRunner;
import com.lunacode.skill.SkillInvocationPlan;
import com.lunacode.skill.SkillInvocationPlanner;
import com.lunacode.skill.SkillInvocationRequest;
import com.lunacode.skill.SkillPromptContext;
import com.lunacode.session.SessionCommandHandler;
import com.lunacode.tool.DefaultToolPermissionGateway;
import com.lunacode.tool.DefaultToolRegistry;
import com.lunacode.tool.ToolBatchPlanner;
import com.lunacode.tool.ToolExecutor;
import com.lunacode.tool.ToolRegistry;
import com.lunacode.worktree.DefaultWorktreeCommandHandler;
import com.lunacode.worktree.WorktreeCommandHandler;
import com.lunacode.worktree.WorktreeManager;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class DefaultChatOrchestrator implements ChatOrchestrator, AgentEventSink, CommandRuntime {
    private final ProviderConfig config;
    private final ConversationManager conversationManager;
    private final ChatProvider provider;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
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
    private final HookRuntime hookRuntime;
    private final SlashCommandDispatcher commandDispatcher;
    private final SlashCommandCompleter commandCompleter;
    private final SlashCommandRegistry commandRegistry;
    private final SkillCommandRegistrar skillCommandRegistrar = new SkillCommandRegistrar();
    private final Path workspaceRoot;
    private volatile SkillCatalog skillCatalog;
    private volatile SkillInvocationPlanner skillInvocationPlanner;
    private volatile SkillForkRunner skillForkRunner;
    private volatile Path lastPlanFile;
    private volatile int currentTurnStartIndex;
    private volatile CommandUiController commandUiController = () -> {};
    private volatile AgentMode agentMode = AgentMode.DEFAULT;
    private volatile PermissionMode permissionModeBeforePlan;
    private volatile boolean planPermissionManuallyChanged;
    private volatile ForegroundSubAgentTracker foregroundSubAgentTracker;
    private volatile WorktreeManager worktreeManager;
    private volatile WorktreeCommandHandler worktreeCommandHandler;

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
                NoOpHookRuntime.instance(),
                onChange,
                newAgentExecutor()
        );
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
            HookRuntime hookRuntime,
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
                hookRuntime,
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
                NoOpHookRuntime.instance(),
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
                NoOpHookRuntime.instance(),
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
            HookRuntime hookRuntime,
            Runnable onChange,
            ExecutorService executor
    ) {
        this.conversationManager = Objects.requireNonNull(conversationManager, "conversationManager");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.config = Objects.requireNonNull(config, "config");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.toolExecutor = toolExecutor;
        this.questionBroker = Objects.requireNonNull(questionBroker, "questionBroker");
        this.sessionCommandHandler = sessionCommandHandler;
        this.memoryCommandHandler = memoryCommandHandler;
        this.autoMemoryUpdater = autoMemoryUpdater;
        this.memoryRuntimeState = memoryRuntimeState;
        this.sessionIdSupplier = sessionIdSupplier;
        this.memoryIndexSupplier = memoryIndexSupplier;
        this.hookRuntime = hookRuntime == null ? NoOpHookRuntime.instance() : hookRuntime;
        this.permissionBroker = new BlockingPermissionConfirmationBroker();
        this.onChange = onChange == null ? () -> {} : onChange;
        this.executor = Objects.requireNonNull(executor, "executor");
        this.workspaceRoot = Path.of("").toAbsolutePath().normalize();
        this.promptContextBuilder = promptContextBuilder == null ? new PromptContextBuilder() : promptContextBuilder;
        CommandBundle commands = defaultCommandBundle();
        this.commandRegistry = commands.registry();
        this.commandDispatcher = commands.dispatcher();
        this.commandCompleter = commands.completer();
        this.contextManager = DefaultContextManager.createDefault(workspaceRoot, config.context(), new com.lunacode.tool.SensitiveValueMasker(java.util.List.of(config.apiKey())), this.hookRuntime, this::currentSessionId);
        this.lastPlanFile = resolvePlanFile(effectiveWorkDir());
        this.permissionModeSession = new PermissionModeSession(config.permissions().mode());
        this.permissionModeBeforePlan = this.permissionModeSession.currentMode();
        this.permissionRuleStore = new YamlPermissionRuleStore(workspaceRoot);
        this.status = new AtomicReference<>(StatusSnapshot.idle(config.protocol(), config.model()).withPermissionMode(permissionModeSession.currentMode()));
        this.agentLoop = createAgentLoop(this.conversationManager, this.provider, config, this.toolRegistry, toolExecutor);
    }

    private record CommandBundle(SlashCommandRegistry registry, SlashCommandDispatcher dispatcher, SlashCommandCompleter completer) {
    }

    private CommandBundle defaultCommandBundle() {
        SlashCommandRegistry registry = new SlashCommandRegistry();
        BuiltinSlashCommands.registerAll(registry);
        return new CommandBundle(registry, new SlashCommandDispatcher(registry, new SlashCommandParser(), hookRuntime, this::currentSessionId, this::effectiveWorkDir), new SlashCommandCompleter(registry));
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
        DefaultPermissionEngine permissionEngine = new DefaultPermissionEngine(permissionRuleStore, new PermissionTargetExtractor(pathSandbox, new BashPathScanner(), sensitivePathPolicy, config.sandbox()), new PermissionRuleMatcher(), new PermissionModePolicy(), new DangerousCommandBlacklist());
        AgentToolRunner toolRunner = new AgentToolRunner(toolRegistry, toolExecutor, new ToolBatchPlanner(), new DefaultToolPermissionGateway(workspaceRoot, permissionEngine), permissionBroker, permissionRuleStore, hookRuntime, this::currentSessionId);
        AgentTurnRunner turnRunner = new AgentTurnRunner(conversationManager, provider);
        return new DefaultAgentLoop(conversationManager, config, toolRegistry, toolRunner, turnRunner, new LoopDecisionMaker(), promptContextBuilder, contextManager, hookRuntime, this::currentSessionId);
    }

    @Override
    public void submitUserMessage(String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        String stripped = content.strip();
        refreshSkillCommands();
        DispatchResult commandResult = commandDispatcher.dispatch(stripped, this);
        if (commandResult == DispatchResult.HANDLED) {
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
        startAgentRequest(new AgentRequest(stripped, runConfig(agentMode)));
    }

    @Override
    public void submitSkillInvocation(SkillInvocationRequest request) {
        if (request == null || request.name().isBlank()) {
            setStatus("error", "Skill 名称不能为空");
            return;
        }
        if (permissionBroker.hasPendingConfirmation() || questionBroker.hasPendingQuestion() || pendingModeSwitch.get() != null) {
            setStatus("warning", "当前正在等待用户输入，完成后再调用 Skill");
            return;
        }
        SkillInvocationPlanner planner = skillInvocationPlanner;
        if (planner == null) {
            setStatus("error", "Skill 系统尚未初始化");
            return;
        }
        SkillInvocationPlan plan;
        try {
            plan = planner.plan(request);
        } catch (RuntimeException e) {
            setStatus("error", "Skill 调用失败: " + e.getMessage());
            return;
        }
        if (plan.definition().mode() == SkillExecutionMode.FORK) {
            startForkSkill(request, plan);
        } else {
            startInlineSkill(request, plan);
        }
    }

    public void configureWorktrees(WorktreeManager manager, WorktreeCommandHandler handler) {
        this.worktreeManager = manager;
        this.worktreeCommandHandler = handler == null && manager != null ? new DefaultWorktreeCommandHandler(manager) : handler;
    }
    public void configureSkills(SkillCatalog catalog, SkillInvocationPlanner planner, SkillForkRunner forkRunner) {
        this.skillCatalog = catalog;
        this.skillInvocationPlanner = planner;
        this.skillForkRunner = forkRunner == null
                ? new DefaultSkillForkRunner(childConversation -> createAgentLoop(childConversation, provider, config, toolRegistry, toolExecutor))
                : forkRunner;
        refreshSkillCommands();
    }

    private void startInlineSkill(SkillInvocationRequest request, SkillInvocationPlan plan) {
        startAgentRequest(new AgentRequest(
                visibleSkillRequest(request),
                plan.renderedPrompt(),
                skillRunConfig(plan)
        ));
    }

    private void startForkSkill(SkillInvocationRequest request, SkillInvocationPlan plan) {
        SkillForkRunner runner = skillForkRunner;
        if (runner == null) {
            setStatus("error", "fork Skill 运行器尚未初始化");
            return;
        }
        if (!responding.compareAndSet(false, true)) {
            setStatus("warning", "当前正忙，请稍后再试");
            return;
        }
        currentTurnStartIndex = fullSnapshotSize();
        String visibleRequest = visibleSkillRequest(request);
        conversationManager.addMessage(MessageRole.USER, visibleRequest);
        CancellationToken token = new CancellationToken();
        currentToken.set(token);
        setStatus("responding", "正在运行 fork Skill /" + plan.definition().name());
        executor.submit(() -> {
            try {
                SkillForkResult result = runner.runFork(plan, skillRunConfig(plan), conversationManager, token);
                if (!token.isCancellationRequested()) {
                    conversationManager.addAssistantMessage(List.of(new ContentBlock.Text(formatForkSummary(result, visibleRequest))));
                    setStatus("idle", null);
                    triggerAutoMemoryUpdate();
                }
            } catch (RuntimeException e) {
                setStatus("error", "fork Skill 执行失败: " + e.getMessage());
            } finally {
                responding.set(false);
                currentToken.compareAndSet(token, null);
                onChange.run();
            }
        });
    }

    private void startAgentRequest(AgentRequest request) {
        if (!responding.compareAndSet(false, true)) {
            setStatus("warning", "当前正忙，请稍后再试");
            return;
        }
        currentTurnStartIndex = fullSnapshotSize();
        CancellationToken token = new CancellationToken();
        currentToken.set(token);
        setStatus("responding", null);
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

    private AgentRunConfig skillRunConfig(SkillInvocationPlan plan) {
        LoadedSkillContext loadedSkill = new LoadedSkillContext(
                plan.definition().name(),
                plan.renderedPrompt(),
                plan.definition().resourceRoot()
        );
        return runConfig(agentMode)
                .withToolAccessPolicy(plan.toolAccessPolicy())
                .withModelOverride(plan.modelOverride())
                .withSkillPromptContext(new SkillPromptContext(List.of(), Optional.of(loadedSkill)));
    }

    private String visibleSkillRequest(SkillInvocationRequest request) {
        String name = request.name().startsWith("/") ? request.name() : "/" + request.name();
        String args = request.rawArguments() == null ? "" : request.rawArguments().strip();
        return args.isBlank() ? name : name + " " + args;
    }

    private String formatForkSummary(SkillForkResult result, String visibleRequest) {
        StringBuilder out = new StringBuilder();
        out.append("fork Skill ").append(visibleRequest).append(" 已完成。");
        if (!result.summary().isBlank()) {
            out.append("\n\n").append(result.summary().strip());
        }
        if (!result.artifactPaths().isEmpty()) {
            out.append("\n\n产物：").append(String.join(", ", result.artifactPaths()));
        }
        if (!result.nextSteps().isEmpty()) {
            out.append("\n\n后续建议：").append(String.join("；", result.nextSteps()));
        }
        return out.toString();
    }

    private void refreshSkillCommands() {
        SkillCatalog catalog = skillCatalog;
        if (catalog == null) {
            return;
        }
        try {
            skillCommandRegistrar.registerSkillCommands(commandRegistry, catalog, this);
        } catch (RuntimeException e) {
            setStatus("warning", "Skill 命令刷新失败: " + e.getMessage());
        }
    }

    public void configureBackgroundTasks(BackgroundTaskManager manager, ForegroundSubAgentTracker tracker, TaskNotificationFormatter formatter) {
        this.foregroundSubAgentTracker = tracker;
        TaskNotificationFormatter safeFormatter = formatter == null ? new TaskNotificationFormatter() : formatter;
        if (manager != null) {
            manager.addListener(taskId -> manager.get(taskId).ifPresent(snapshot -> {
                conversationManager.addAssistantMessage(List.of(new ContentBlock.Text(safeFormatter.format(snapshot))));
                setStatus("idle", "后台任务已完成: " + taskId);
                triggerAutoMemoryUpdate();
                onChange.run();
            }));
        }
    }

    @Override
    public void backgroundCurrentSubAgentOrCancel() {
        ForegroundSubAgentTracker tracker = foregroundSubAgentTracker;
        if (tracker != null) {
            Optional<String> taskId = tracker.adoptCurrentToBackground();
            if (taskId.isPresent()) {
                setStatus("idle", "子 Agent 已切入后台: " + taskId.get());
                return;
            }
        }
        cancelCurrentRun();
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
    public SlashCommandCompletion completeSlashCommand(String input, int cursorIndex) {
        refreshSkillCommands();
        return commandCompleter.complete(input, cursorIndex);
    }

    @Override
    public void setCommandUiController(CommandUiController controller) {
        this.commandUiController = controller == null ? () -> {} : controller;
    }

    @Override
    public boolean isBusy() {
        return responding.get();
    }

    @Override
    public boolean hasPendingUserAnswer() {
        return questionBroker.hasPendingQuestion();
    }

    @Override
    public boolean hasPendingPermissionAnswer() {
        return permissionBroker.hasPendingConfirmation();
    }

    @Override
    public boolean hasPendingDangerousModeConfirmation() {
        return pendingModeSwitch.get() != null;
    }

    @Override
    public CommandRuntimeStatus runtimeStatus() {
        StatusSnapshot snapshot = status();
        return new CommandRuntimeStatus(
                snapshot.agentMode(),
                snapshot.permissionMode(),
                snapshot.provider(),
                snapshot.model(),
                snapshot.inputTokens(),
                snapshot.outputTokens(),
                snapshot.state(),
                snapshot.sessionShortId(),
                snapshot.memoryAutoUpdateEnabled(),
                snapshot.memoryLatestState()
        );
    }

    @Override
    public void showInfo(String message) {
        setStatus("idle", message);
    }

    @Override
    public void showWarning(String message) {
        setStatus("warning", message);
    }

    @Override
    public void showError(String message) {
        setStatus("error", message);
    }

    @Override
    public void requestRender() {
        onChange.run();
    }

    @Override
    public void clearVisibleScreen() {
        commandUiController.clearVisibleScreen();
        setStatus("idle", "已清屏");
    }

    @Override
    public void sendUserMessage(String message) {
        submitUserMessage(message);
    }

    @Override
    public void compactContext() {
        handleCompactCommand();
    }

    @Override
    public void enterPlanMode() {
        if (agentMode != AgentMode.PLAN) {
            permissionModeBeforePlan = permissionModeSession.currentMode();
        }
        agentMode = AgentMode.PLAN;
        planPermissionManuallyChanged = false;
        lastPlanFile = resolvePlanFile(effectiveWorkDir());
        permissionModeSession.setCurrentMode(PermissionMode.PLAN);
        setStatus("idle", "已进入计划模式");
    }

    @Override
    public void enterDefaultMode() {
        if (agentMode == AgentMode.PLAN && !planPermissionManuallyChanged && permissionModeBeforePlan != null) {
            permissionModeSession.setCurrentMode(permissionModeBeforePlan);
        }
        agentMode = AgentMode.DEFAULT;
        setStatus("idle", "已进入执行模式");
    }

    @Override
    public void switchPermissionMode(PermissionMode mode) {
        permissionModeSession.setCurrentMode(Objects.requireNonNull(mode, "mode"));
        if (agentMode == AgentMode.PLAN) {
            planPermissionManuallyChanged = true;
        }
        setStatus("idle", "权限模式已切换为 " + mode.configValue());
    }

    @Override
    public void requestDangerousPermissionMode(PermissionMode mode) {
        pendingModeSwitch.set(Objects.requireNonNull(mode, "mode"));
        setStatus("waiting_permission", "bypassPermissions 是危险模式。输入 yes/确认 切换，其他输入取消。", "permissions", "切换到 bypassPermissions");
    }

    @Override
    public void runSessionCommand(String rawInput) {
        if (sessionCommandHandler == null) {
            setStatus("error", "当前未启用会话命令");
            return;
        }
        boolean busy = isBusy() || hasPendingPermissionAnswer() || hasPendingUserAnswer() || hasPendingDangerousModeConfirmation();
        SessionCommandHandler.CommandResult result = sessionCommandHandler.handle(rawInput, busy);
        setStatus(result.state(), result.message());
    }

    @Override
    public void runMemoryCommand(String rawInput) {
        if (memoryCommandHandler == null) {
            setStatus("error", "当前未启用记忆命令");
            return;
        }
        MemoryCommandHandler.CommandResult result = memoryCommandHandler.handle(rawInput);
        setStatus(result.state(), result.message());
    }

    @Override
    public void runWorktreeCommand(String rawInput) {
        WorktreeCommandHandler handler = worktreeCommandHandler;
        if (handler == null) {
            setStatus("error", "当前未启用 Worktree 命令");
            return;
        }
        boolean busy = isBusy() || hasPendingPermissionAnswer() || hasPendingUserAnswer() || hasPendingDangerousModeConfirmation();
        WorktreeCommandHandler.CommandResult result = handler.handle(rawInput, busy);
        setStatus(result.state(), result.message());
    }
    @Override
    public StatusSnapshot status() {
        StatusSnapshot snapshot = enrich(status.get());
        return snapshot == null ? null : snapshot.withAgentMode(agentMode).withPermissionMode(permissionModeSession.currentMode());
    }

    @Override
    public void emit(AgentEvent event) {
        if (event instanceof AgentEvent.UsageUpdated usageUpdated) {
            TokenUsage usage = usageUpdated.cumulativeUsage();
            StatusSnapshot old = status.get();
            status.set(new StatusSnapshot(config.protocol(), config.model(), usage.inputTokens(), usage.outputTokens(), old.state(), old.errorSummary(), old.toolName(), old.toolSummary(), agentMode, permissionModeSession.currentMode()));
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
                status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), old.state(), old.errorSummary(), old.toolName(), old.toolSummary(), agentMode, permissionModeSession.currentMode()));
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
                status.set(new StatusSnapshot(config.protocol(), config.model(), old.inputTokens(), old.outputTokens(), "responding", null, old.toolName(), old.toolSummary(), agentMode, permissionModeSession.currentMode()));
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

    private void answerModeSwitch(String answer) {
        PermissionMode mode = pendingModeSwitch.getAndSet(null);
        if (mode == null) {
            return;
        }
        if (isApproval(answer)) {
            switchPermissionMode(mode);
        } else {
            setStatus("idle", "已取消切换权限模式，当前仍为 " + permissionModeSession.currentMode().configValue());
        }
    }

    private boolean isApproval(String answer) {
        String normalized = answer == null ? "" : answer.strip().toLowerCase(Locale.ROOT);
        return normalized.equals("y") || normalized.equals("yes") || normalized.equals("ok") || normalized.equals("confirm") || normalized.equals("确认") || normalized.equals("允许") || normalized.equals("同意");
    }

    private AgentRunConfig runConfig(AgentMode mode) {
        Path workDir = effectiveWorkDir();
        return new AgentRunConfig(workDir, mode, permissionModeSession.modeFor(mode), resolvePlanFile(workDir), config.agent().maxIterations(), config.agent().maxConsecutiveUnknownTools(), Clock.systemDefaultZone());
    }

    private Path effectiveWorkDir() {
        WorktreeManager manager = worktreeManager;
        return manager == null ? workspaceRoot : manager.effectiveWorkDir();
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
        status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), state, errorSummary, null, null, agentMode, permissionModeSession.currentMode()));
        onChange.run();
    }

    private void setStatus(String state, String errorSummary, String toolName, String toolSummary) {
        status.set(new StatusSnapshot(config.protocol(), config.model(), tokens().inputTokens(), tokens().outputTokens(), state, errorSummary, toolName, toolSummary, agentMode, permissionModeSession.currentMode()));
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
