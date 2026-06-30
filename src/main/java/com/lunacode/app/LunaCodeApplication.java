package com.lunacode.app;

import com.lunacode.interaction.BlockingUserQuestionBroker;
import com.lunacode.config.ConfigLoader;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.mcp.McpClientManager;
import com.lunacode.mcp.McpDiscoveryResult;
import com.lunacode.orchestrator.DefaultChatOrchestrator;
import com.lunacode.permission.DefaultPathSandbox;
import com.lunacode.permission.PathSandbox;
import com.lunacode.provider.ChatProvider;
import com.lunacode.provider.ChatProviderFactory;
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

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
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

        ConversationManager conversationManager = new DefaultConversationManager();
        ChatProvider provider;
        try {
            provider = new ChatProviderFactory().create(config.protocol());
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }

        Path workspaceRoot = Path.of("").toAbsolutePath().normalize();
        PathSandbox pathSandbox;
        try {
            pathSandbox = new DefaultPathSandbox(workspaceRoot, config.sandbox());
        } catch (IllegalArgumentException e) {
            System.err.println("沙箱配置无效: " + e.getMessage());
            return;
        }
        WorkspacePathResolver resolver = new WorkspacePathResolver(workspaceRoot, pathSandbox);
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new ReadFileTool(resolver));
        registry.register(new WriteFileTool(resolver));
        registry.register(new EditFileTool(resolver));
        registry.register(new BashTool());
        registry.register(new GlobTool(resolver));
        registry.register(new GrepTool(resolver));
        registry.register(new AskUserQuestionTool());
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
                pathSandbox.roots()
        );
        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(registry, toolContext);

        AtomicReference<LanternaLunaTui> tuiRef = new AtomicReference<>();
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(
                conversationManager,
                provider,
                config,
                registry,
                toolExecutor,
                questionBroker,
                () -> {
                    LanternaLunaTui tui = tuiRef.get();
                    if (tui != null) {
                        tui.requestRender();
                    }
                }
        );
        LanternaLunaTui tui = new LanternaLunaTui(conversationManager, orchestrator);
        tuiRef.set(tui);
        try {
            tui.start();
        } finally {
            mcpManager.closeAsync().join();
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
}
