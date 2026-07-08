package com.lunacode.app;

import com.lunacode.background.DefaultBackgroundTaskManager;
import com.lunacode.background.DefaultForegroundSubAgentTracker;
import com.lunacode.background.TaskNotificationFormatter;
import com.lunacode.command.DefaultTeamCommandHandler;
import com.lunacode.config.ConfigLoader;
import com.lunacode.config.DefaultFeatureGateService;
import com.lunacode.config.FeatureGateService;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.coordinator.DefaultCoordinatorModeResolver;
import com.lunacode.hook.NoOpHookRuntime;
import com.lunacode.interaction.BlockingUserQuestionBroker;
import com.lunacode.mcp.McpClientManager;
import com.lunacode.mcp.McpDiscoveryResult;
import com.lunacode.orchestrator.DefaultChatOrchestrator;
import com.lunacode.permission.DefaultPathSandbox;
import com.lunacode.permission.PathSandbox;
import com.lunacode.permission.SandboxRoot;
import com.lunacode.prompt.PromptContextBuilder;
import com.lunacode.provider.ChatProvider;
import com.lunacode.provider.ChatProviderFactory;
import com.lunacode.session.DefaultSessionService;
import com.lunacode.session.JsonlSessionStore;
import com.lunacode.session.SessionBackedConversationManager;
import com.lunacode.session.SessionCommandHandler;
import com.lunacode.session.SessionRecoveryResult;
import com.lunacode.session.SessionService;
import com.lunacode.subagent.AgentDefinitionCatalog;
import com.lunacode.subagent.BuiltinAgentDefinitionSource;
import com.lunacode.subagent.DefaultAgentDefinitionCatalog;
import com.lunacode.subagent.DefaultSubAgentRunnerFactory;
import com.lunacode.subagent.DefaultSubAgentService;
import com.lunacode.subagent.FileSystemAgentDefinitionSource;
import com.lunacode.subagent.FrontmatterAgentDefinitionParser;
import com.lunacode.subagent.PluginAgentDefinitionSource;
import com.lunacode.subagent.SubAgentService;
import com.lunacode.team.DefaultTeamManager;
import com.lunacode.team.JsonTeamStore;
import com.lunacode.team.TeamManager;
import com.lunacode.team.TeamPaths;
import com.lunacode.team.tool.SendMessageTool;
import com.lunacode.team.tool.TaskCreateTool;
import com.lunacode.team.tool.TaskGetTool;
import com.lunacode.team.tool.TaskListTool;
import com.lunacode.team.tool.TaskUpdateTool;
import com.lunacode.team.tool.TeamCreateTool;
import com.lunacode.team.tool.TeamDeleteTool;
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
import com.lunacode.tool.McpToolWrapper;
import com.lunacode.tool.ReadFileTool;
import com.lunacode.tool.SensitiveValueMasker;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolSearchTool;
import com.lunacode.tool.WorkspacePathResolver;
import com.lunacode.tool.WriteFileTool;
import com.lunacode.tui.LanternaLunaTui;
import com.lunacode.worktree.DefaultWorktreeCommandHandler;
import com.lunacode.worktree.DefaultWorktreeEnvironmentInitializer;
import com.lunacode.worktree.DefaultWorktreeManager;
import com.lunacode.worktree.ProcessGitWorktreeClient;
import com.lunacode.worktree.WorktreeCommandHandler;
import com.lunacode.worktree.WorktreeManager;

import java.nio.file.Files;
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

        ChatProvider provider;
        try {
            provider = new ChatProviderFactory().create(config.protocol());
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }

        Path workspaceRoot = Path.of("").toAbsolutePath().normalize();
        Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        SessionService sessionService = new DefaultSessionService(new JsonlSessionStore(workspaceRoot), config.context());
        SessionBackedConversationManager conversationManager = new SessionBackedConversationManager(new DefaultConversationManager(), sessionService);
        SessionRecoveryResult sessionRecovery = sessionService.restoreLatestOrCreate();
        conversationManager.restoreHistory(sessionRecovery.messages());
        sessionRecovery.warnings().forEach(System.err::println);
        SessionCommandHandler sessionCommandHandler = new SessionCommandHandler(sessionService, conversationManager);
        FeatureGateService featureGateService = new DefaultFeatureGateService(config);
        List<SandboxRoot> sandboxRoots;
        PathSandbox pathSandbox;
        try {
            sandboxRoots = buildSandboxRoots(workspaceRoot, userHome, config.sandbox());
            pathSandbox = new DefaultPathSandbox(workspaceRoot, sandboxRoots);
        } catch (IllegalArgumentException e) {
            System.err.println("沙箱配置无效: " + e.getMessage());
            return;
        }

        ProcessGitWorktreeClient gitWorktreeClient = new ProcessGitWorktreeClient();
        WorktreeManager worktreeManager = new DefaultWorktreeManager(
                workspaceRoot,
                gitWorktreeClient,
                new DefaultWorktreeEnvironmentInitializer(gitWorktreeClient)
        );
        WorktreeCommandHandler worktreeCommandHandler = new DefaultWorktreeCommandHandler(worktreeManager);
        TeamManager teamManager = new DefaultTeamManager(
                new JsonTeamStore(new TeamPaths(userHome, workspaceRoot)),
                worktreeManager,
                featureGateService
        );

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
        registry.register(new AgentTool(subAgentServiceRef::get));
        registry.register(new TeamCreateTool(teamManager));
        registry.register(new TeamDeleteTool(teamManager));
        registry.register(new TaskCreateTool(teamManager));
        registry.register(new TaskGetTool(teamManager));
        registry.register(new TaskListTool(teamManager));
        registry.register(new TaskUpdateTool(teamManager));
        registry.register(new SendMessageTool(teamManager));
        registry.register(new ToolSearchTool(registry));

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
        PromptContextBuilder promptContextBuilder = new PromptContextBuilder();

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
        DefaultSubAgentRunnerFactory subAgentRunnerFactory = new DefaultSubAgentRunnerFactory(
                provider,
                config,
                registry,
                toolExecutor,
                promptContextBuilder,
                NoOpHookRuntime.instance(),
                () -> ""
        );
        subAgentRunnerFactory.configureWorktreeManager(worktreeManager);
        DefaultBackgroundTaskManager backgroundTaskManager = new DefaultBackgroundTaskManager(subAgentRunnerFactory);
        DefaultForegroundSubAgentTracker foregroundSubAgentTracker = new DefaultForegroundSubAgentTracker(backgroundTaskManager);
        SubAgentService subAgentService = new DefaultSubAgentService(
                agentDefinitionCatalog,
                subAgentRunnerFactory,
                backgroundTaskManager,
                foregroundSubAgentTracker,
                config,
                worktreeManager,
                teamManager
        );
        subAgentServiceRef.set(subAgentService);

        AtomicReference<LanternaLunaTui> tuiRef = new AtomicReference<>();
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(
                conversationManager,
                provider,
                config,
                registry,
                toolExecutor,
                questionBroker,
                sessionCommandHandler,
                null,
                null,
                null,
                () -> sessionService.currentSession().id(),
                null,
                promptContextBuilder,
                () -> {
                    LanternaLunaTui tui = tuiRef.get();
                    if (tui != null) {
                        tui.requestRender();
                    }
                }
        );
        orchestrator.configureWorktrees(worktreeManager, worktreeCommandHandler);
        orchestrator.configureTeams(teamManager, new DefaultTeamCommandHandler(teamManager, () -> ""), new DefaultCoordinatorModeResolver(featureGateService));
        orchestrator.configureBackgroundTasks(backgroundTaskManager, foregroundSubAgentTracker, new TaskNotificationFormatter());

        LanternaLunaTui tui = new LanternaLunaTui(conversationManager, orchestrator);
        tuiRef.set(tui);
        try {
            tui.start();
        } finally {
            mcpManager.closeAsync().join();
        }
    }

    static List<SandboxRoot> buildSandboxRoots(Path workspaceRoot, Path userHome, com.lunacode.config.SandboxConfig config) {
        List<SandboxRoot> roots = new ArrayList<>(SandboxRoot.build(workspaceRoot, config));
        Path userSkillRoot = userHome == null ? null : userHome.resolve(".lunacode").resolve("skills");
        if (userSkillRoot != null && Files.isDirectory(userSkillRoot)) {
            roots.add(SandboxRoot.readOnly("user-skills", userSkillRoot, "/roots/user-skills"));
        }
        return Collections.unmodifiableList(roots);
    }

    private Set<String> reservedToolNames(DefaultToolRegistry registry) {
        return registry.getEnabledTools().stream()
                .map(Tool::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }
}