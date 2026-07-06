package com.lunacode.app;

import com.lunacode.background.DefaultBackgroundTaskManager;
import com.lunacode.background.DefaultForegroundSubAgentTracker;
import com.lunacode.background.TaskNotificationFormatter;
import com.lunacode.command.BuiltinSlashCommands;
import com.lunacode.command.SlashCommandRegistry;
import com.lunacode.config.ConfigLoader;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.hook.CommandHookActionExecutor;
import com.lunacode.hook.DefaultHookActionExecutor;
import com.lunacode.hook.DefaultHookRuntime;
import com.lunacode.hook.FileHookLogWriter;
import com.lunacode.hook.HookConditionEvaluator;
import com.lunacode.hook.HookConfig;
import com.lunacode.hook.HookConfigException;
import com.lunacode.hook.HookConfigLoader;
import com.lunacode.hook.HookContext;
import com.lunacode.hook.HookEventName;
import com.lunacode.hook.HookExecutionScope;
import com.lunacode.hook.HookRuntime;
import com.lunacode.hook.HttpHookActionExecutor;
import com.lunacode.hook.InMemoryHookOnceTracker;
import com.lunacode.hook.InMemoryHookReminderStore;
import com.lunacode.hook.NoOpHookRuntime;
import com.lunacode.hook.PromptHookActionExecutor;
import com.lunacode.hook.ShellCommandRunner;
import com.lunacode.hook.RealSubAgentHookActionExecutor;
import com.lunacode.instructions.DefaultProjectInstructionLoader;
import com.lunacode.interaction.BlockingUserQuestionBroker;
import com.lunacode.mcp.McpClientManager;
import com.lunacode.mcp.McpDiscoveryResult;
import com.lunacode.memory.DefaultAutoMemoryUpdater;
import com.lunacode.memory.DefaultMemoryContextLoader;
import com.lunacode.memory.MarkdownMemoryStore;
import com.lunacode.memory.MemoryCommandHandler;
import com.lunacode.memory.MemoryRuntimeState;
import com.lunacode.memory.ProviderMemoryModelClient;
import com.lunacode.orchestrator.DefaultChatOrchestrator;
import com.lunacode.permission.DefaultPathSandbox;
import com.lunacode.permission.PathSandbox;
import com.lunacode.permission.SandboxRoot;
import com.lunacode.prompt.EnvironmentContextCollector;
import com.lunacode.prompt.MessageChannelBuilder;
import com.lunacode.prompt.PromptContextBuilder;
import com.lunacode.prompt.StaticSystemPromptBuilder;
import com.lunacode.prompt.SystemReminderBuilder;
import com.lunacode.provider.ChatProvider;
import com.lunacode.provider.ChatProviderFactory;
import com.lunacode.session.DefaultSessionService;
import com.lunacode.session.JsonlSessionStore;
import com.lunacode.session.SessionBackedConversationManager;
import com.lunacode.session.SessionCommandHandler;
import com.lunacode.session.SessionRecoveryResult;
import com.lunacode.skill.BuiltinSkillSource;
import com.lunacode.skill.DefaultSkillCatalog;
import com.lunacode.skill.DefaultSkillInvocationPlanner;
import com.lunacode.skill.DefaultSkillPromptContextLoader;
import com.lunacode.skill.FileSystemSkillSource;
import com.lunacode.skill.FrontmatterSkillParser;
import com.lunacode.skill.SkillCatalog;
import com.lunacode.skill.SkillDiagnostic;
import com.lunacode.subagent.AgentDefinitionCatalog;
import com.lunacode.subagent.AgentDefinitionDiagnostic;
import com.lunacode.subagent.BuiltinAgentDefinitionSource;
import com.lunacode.subagent.DefaultAgentDefinitionCatalog;
import com.lunacode.subagent.DefaultSubAgentRunnerFactory;
import com.lunacode.subagent.DefaultSubAgentService;
import com.lunacode.subagent.FileSystemAgentDefinitionSource;
import com.lunacode.subagent.FrontmatterAgentDefinitionParser;
import com.lunacode.subagent.PluginAgentDefinitionSource;
import com.lunacode.subagent.SubAgentService;
import com.lunacode.tool.AgentTool;
import com.lunacode.tool.AskUserQuestionTool;
import com.lunacode.tool.BashTool;
import com.lunacode.tool.BubblewrapCommandSandbox;
import com.lunacode.tool.CommandSandbox;
import com.lunacode.tool.DefaultToolExecutor;
import com.lunacode.tool.DefaultToolRegistry;
import com.lunacode.tool.DirectCommandSandbox;
import com.lunacode.tool.EditFileTool;
import com.lunacode.tool.GlobTool;
import com.lunacode.tool.GrepTool;
import com.lunacode.tool.LoadSkillTool;
import com.lunacode.tool.McpToolWrapper;
import com.lunacode.tool.ReadFileTool;
import com.lunacode.tool.SensitiveValueMasker;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolSearchTool;
import com.lunacode.tool.WorkspacePathResolver;
import com.lunacode.tool.WriteFileTool;
import com.lunacode.tui.LanternaLunaTui;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class LunaCodeApplication {
    public void run(String[] args) {
        Path configPath = Path.of(args.length > 0 ? args[0] : "config.yaml");
        ProviderConfig config;
        try {
            config = new ConfigLoader().load(configPath);
        } catch (ConfigLoader.ConfigException e) {
            System.err.println(e.getMessage());
            return;
        }

        Path workspaceRoot = Path.of("").toAbsolutePath().normalize();
        Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        JsonlSessionStore sessionStore = new JsonlSessionStore(workspaceRoot);
        DefaultSessionService sessionService = new DefaultSessionService(sessionStore, config.context());
        SessionBackedConversationManager conversationManager = new SessionBackedConversationManager(new DefaultConversationManager(), sessionService);
        SessionRecoveryResult recoveryResult = sessionService.restoreLatestOrCreate();
        conversationManager.restoreHistory(recoveryResult.messages());

        MarkdownMemoryStore memoryStore = new MarkdownMemoryStore(workspaceRoot);
        DefaultMemoryContextLoader memoryContextLoader = new DefaultMemoryContextLoader(memoryStore);
        MemoryRuntimeState memoryRuntimeState = new MemoryRuntimeState(config.memory().autoUpdate());

        ChatProvider provider;
        try {
            provider = new ChatProviderFactory().create(config.protocol());
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }

        PathSandbox pathSandbox;
        List<SandboxRoot> sandboxRoots;
        try {
            sandboxRoots = buildSandboxRoots(workspaceRoot, userHome, config.sandbox());
            pathSandbox = new DefaultPathSandbox(workspaceRoot, sandboxRoots);
        } catch (IllegalArgumentException e) {
            System.err.println("沙箱配置无效: " + e.getMessage());
            return;
        }
        WorkspacePathResolver resolver = new WorkspacePathResolver(workspaceRoot, pathSandbox);
        AtomicReference<SubAgentService> subAgentServiceRef = new AtomicReference<>();
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new ReadFileTool(resolver));
        registry.register(new WriteFileTool(resolver));
        registry.register(new EditFileTool(resolver));
        registry.register(new BashTool());
        registry.register(new GlobTool(resolver));
        registry.register(new GrepTool(resolver));
        registry.register(new AskUserQuestionTool());
        registry.register(new ToolSearchTool(registry));
        registry.register(new AgentTool(subAgentServiceRef::get));

        SlashCommandRegistry builtinCommandProbe = new SlashCommandRegistry();
        BuiltinSlashCommands.registerAll(builtinCommandProbe);
        SkillCatalog skillCatalog = new DefaultSkillCatalog(
                List.of(new BuiltinSkillSource(), FileSystemSkillSource.user(), FileSystemSkillSource.project()),
                new FrontmatterSkillParser(),
                workspaceRoot,
                userHome,
                builtinCommandProbe::builtinCommandNames,
                () -> registry.getEnabledTools().stream()
                        .map(Tool::name)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
        );
        DefaultSkillInvocationPlanner skillPlanner = new DefaultSkillInvocationPlanner(skillCatalog);
        registry.register(new LoadSkillTool(skillPlanner));

        SensitiveValueMasker masker = new SensitiveValueMasker();
        masker.add(config.apiKey());
        config.mcp().servers().values().forEach(server -> server.sensitiveValues().values().forEach(masker::add));

        McpClientManager mcpManager = new McpClientManager(workspaceRoot, reservedToolNames(registry));
        McpDiscoveryResult discovery = mcpManager.discoverAll(config.mcp(), Duration.ofSeconds(30));
        discovery.warnings().forEach(System.err::println);
        for (McpToolWrapper tool : discovery.tools()) {
            try {
                registry.register(tool);
            } catch (IllegalArgumentException e) {
                System.err.println("MCP 工具注册失败，已跳过 " + tool.name() + ": " + e.getMessage());
            }
        }

        BlockingUserQuestionBroker questionBroker = new BlockingUserQuestionBroker();
        CommandSandbox commandSandbox = isLinux() ? new BubblewrapCommandSandbox() : new DirectCommandSandbox();
        ToolExecutionContext toolContext = new ToolExecutionContext(
                workspaceRoot,
                Duration.ofSeconds(30),
                20_000,
                masker,
                questionBroker,
                commandSandbox,
                config.sandbox(),
                sandboxRoots
        );
        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(registry, toolContext);

        InMemoryHookReminderStore hookReminderStore = new InMemoryHookReminderStore();
        HookRuntime hookRuntime;
        try {
            HookConfig hookConfig = new HookConfigLoader().load(workspaceRoot, userHome);
            hookRuntime = hookConfig.isEmpty()
                    ? NoOpHookRuntime.instance()
                    : new DefaultHookRuntime(
                            hookConfig,
                            new HookConditionEvaluator(),
                            new DefaultHookActionExecutor(
                                    new CommandHookActionExecutor(new ShellCommandRunner(), toolContext),
                                    new PromptHookActionExecutor(),
                                    new HttpHookActionExecutor(),
                                    new RealSubAgentHookActionExecutor(subAgentServiceRef::get)
                            ),
                            new InMemoryHookOnceTracker(),
                            hookReminderStore,
                            new FileHookLogWriter(workspaceRoot, masker)
                    );
        } catch (HookConfigException e) {
            System.err.println(e.getMessage());
            return;
        }

        AtomicReference<LanternaLunaTui> tuiRef = new AtomicReference<>();
        Runnable requestRender = () -> {
            LanternaLunaTui tui = tuiRef.get();
            if (tui != null) {
                tui.requestRender();
            }
        };
        DefaultAutoMemoryUpdater autoMemoryUpdater = new DefaultAutoMemoryUpdater(
                new ProviderMemoryModelClient(provider, config),
                memoryStore,
                memoryRuntimeState,
                requestRender
        );
        MemoryCommandHandler memoryCommandHandler = new MemoryCommandHandler(memoryStore, memoryContextLoader, memoryRuntimeState);
        PromptContextBuilder promptContextBuilder = new PromptContextBuilder(
                new StaticSystemPromptBuilder(),
                new EnvironmentContextCollector(),
                new MessageChannelBuilder(new SystemReminderBuilder(), hookReminderStore, () -> sessionService.currentSession().id()),
                new DefaultProjectInstructionLoader(),
                memoryContextLoader,
                new DefaultSkillPromptContextLoader(skillCatalog)
        );

        AgentDefinitionCatalog agentDefinitionCatalog = new DefaultAgentDefinitionCatalog(
                List.of(new PluginAgentDefinitionSource(), new BuiltinAgentDefinitionSource(config.agent()), FileSystemAgentDefinitionSource.user(), FileSystemAgentDefinitionSource.project()),
                new FrontmatterAgentDefinitionParser(),
                workspaceRoot,
                userHome,
                () -> registry.getEnabledTools().stream()
                        .map(Tool::name)
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                config.agent()
        );
        printAgentDiagnostics(agentDefinitionCatalog);
        DefaultSubAgentRunnerFactory subAgentRunnerFactory = new DefaultSubAgentRunnerFactory(
                provider,
                config,
                registry,
                toolExecutor,
                promptContextBuilder,
                hookRuntime,
                () -> sessionService.currentSession().id()
        );
        DefaultBackgroundTaskManager backgroundTaskManager = new DefaultBackgroundTaskManager(subAgentRunnerFactory);
        DefaultForegroundSubAgentTracker foregroundSubAgentTracker = new DefaultForegroundSubAgentTracker(backgroundTaskManager);
        SubAgentService subAgentService = new DefaultSubAgentService(
                agentDefinitionCatalog,
                subAgentRunnerFactory,
                backgroundTaskManager,
                foregroundSubAgentTracker,
                config
        );
        subAgentServiceRef.set(subAgentService);
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(
                conversationManager,
                provider,
                config,
                registry,
                toolExecutor,
                questionBroker,
                new SessionCommandHandler(sessionService, conversationManager),
                memoryCommandHandler,
                autoMemoryUpdater,
                memoryRuntimeState,
                () -> sessionService.currentSession().id(),
                memoryContextLoader::loadForPrompt,
                promptContextBuilder,
                hookRuntime,
                requestRender
        );
        orchestrator.configureSkills(skillCatalog, skillPlanner, null);
        orchestrator.configureBackgroundTasks(backgroundTaskManager, foregroundSubAgentTracker, new TaskNotificationFormatter());
        HookExecutionScope applicationHookScope = new HookExecutionScope(sessionService.currentSession().id(), 0, workspaceRoot);
        hookRuntime.emit(HookEventName.STARTUP, HookContext.empty(HookEventName.STARTUP), applicationHookScope);
        hookRuntime.emit(HookEventName.SESSION_START, HookContext.empty(HookEventName.SESSION_START), applicationHookScope);
        printSkillDiagnostics(skillCatalog);

        LanternaLunaTui tui = new LanternaLunaTui(conversationManager, orchestrator);
        orchestrator.setCommandUiController(tui);
        tuiRef.set(tui);
        try {
            tui.start();
        } finally {
            hookRuntime.emit(HookEventName.SESSION_END, HookContext.empty(HookEventName.SESSION_END), applicationHookScope);
            hookRuntime.emit(HookEventName.SHUTDOWN, HookContext.empty(HookEventName.SHUTDOWN), applicationHookScope);
            hookRuntime.close();
            mcpManager.closeAsync().join();
        }
    }

    private void printAgentDiagnostics(AgentDefinitionCatalog catalog) {
        if (catalog == null) {
            return;
        }
        for (AgentDefinitionDiagnostic diagnostic : catalog.diagnostics()) {
            System.err.println("Agent " + diagnostic.level() + " [" + diagnostic.sourceId() + "]: " + diagnostic.message());
        }
    }
    private void printSkillDiagnostics(SkillCatalog skillCatalog) {
        if (skillCatalog == null) {
            return;
        }
        for (SkillDiagnostic diagnostic : skillCatalog.diagnostics()) {
            System.err.println("Skill " + diagnostic.level() + " [" + diagnostic.sourceId() + "]: " + diagnostic.message());
        }
    }
    private Set<String> reservedToolNames(DefaultToolRegistry registry) {
        return registry.getEnabledTools().stream()
                .map(Tool::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    static List<SandboxRoot> buildSandboxRoots(Path workspaceRoot, Path userHome, com.lunacode.config.SandboxConfig config) {
        List<SandboxRoot> roots = new ArrayList<>(SandboxRoot.build(workspaceRoot, config));
        Path userSkillRoot = userHome == null ? null : userHome.resolve(".lunacode").resolve("skills");
        if (userSkillRoot != null && java.nio.file.Files.isDirectory(userSkillRoot)) {
            roots.add(SandboxRoot.readOnly("user-skills", userSkillRoot, "/roots/user-skills"));
        }
        return Collections.unmodifiableList(roots);
    }
}
