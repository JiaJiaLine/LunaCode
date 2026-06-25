package com.lunacode.app;

import com.lunacode.config.ConfigLoader;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.orchestrator.DefaultChatOrchestrator;
import com.lunacode.provider.ChatProvider;
import com.lunacode.provider.ChatProviderFactory;
import com.lunacode.tui.LanternaLunaTui;

import java.nio.file.Path;
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

        AtomicReference<LanternaLunaTui> tuiRef = new AtomicReference<>();
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(
                conversationManager,
                provider,
                config,
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