package com.lunacode.app;

import com.lunacode.interaction.BlockingUserQuestionBroker;
import com.lunacode.config.ConfigLoader;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.orchestrator.DefaultChatOrchestrator;
import com.lunacode.provider.ChatProvider;
import com.lunacode.provider.ChatProviderFactory;
import com.lunacode.tool.AskUserQuestionTool;
import com.lunacode.tool.BashTool;
import com.lunacode.tool.DefaultToolExecutor;
import com.lunacode.tool.DefaultToolRegistry;
import com.lunacode.tool.EditFileTool;
import com.lunacode.tool.GlobTool;
import com.lunacode.tool.GrepTool;
import com.lunacode.tool.ReadFileTool;
import com.lunacode.tool.SensitiveValueMasker;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.WorkspacePathResolver;
import com.lunacode.tool.WriteFileTool;
import com.lunacode.tui.LanternaLunaTui;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

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
        WorkspacePathResolver resolver = new WorkspacePathResolver(workspaceRoot);
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new ReadFileTool(resolver));
        registry.register(new WriteFileTool(resolver));
        registry.register(new EditFileTool(resolver));
        registry.register(new BashTool());
        registry.register(new GlobTool(resolver));
        registry.register(new GrepTool(resolver));
        registry.register(new AskUserQuestionTool());
        SensitiveValueMasker masker = new SensitiveValueMasker();
        masker.add(config.apiKey());
        BlockingUserQuestionBroker questionBroker = new BlockingUserQuestionBroker();
        ToolExecutionContext toolContext = new ToolExecutionContext(workspaceRoot, Duration.ofSeconds(30), 20_000, masker, questionBroker);
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
        tui.start();
    }
}
