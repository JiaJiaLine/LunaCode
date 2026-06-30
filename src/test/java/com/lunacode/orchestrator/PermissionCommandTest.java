package com.lunacode.orchestrator;

import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.permission.PermissionMode;
import com.lunacode.provider.ChatProvider;
import com.lunacode.stream.StreamEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionCommandTest {
    @Test
    void showsAndSwitchesPermissionModesWithoutCallingModel() {
        ConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider();
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(manager, provider, config(), () -> {}, new DirectExecutorService());

        orchestrator.submitUserMessage("/permissions");
        assertTrue(orchestrator.status().errorSummary().contains("default"));
        assertEquals(0, provider.calls);

        orchestrator.submitUserMessage("/permissions acceptEdits");
        assertEquals(PermissionMode.ACCEPT_EDITS, orchestrator.status().permissionMode());
        assertEquals(0, provider.calls);
    }

    @Test
    void bypassPermissionsRequiresSecondConfirmation() {
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(new DefaultConversationManager(), new CapturingProvider(), config(), () -> {}, new DirectExecutorService());

        orchestrator.submitUserMessage("/permissions bypassPermissions");
        assertEquals("waiting_permission", orchestrator.status().state());
        orchestrator.submitUserMessage("no");
        assertEquals(PermissionMode.DEFAULT, orchestrator.status().permissionMode());

        orchestrator.submitUserMessage("/permissions bypassPermissions");
        orchestrator.submitUserMessage("yes");
        assertEquals(PermissionMode.BYPASS_PERMISSIONS, orchestrator.status().permissionMode());
    }

    @Test
    void rejectsUnknownMode() {
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(new DefaultConversationManager(), new CapturingProvider(), config(), () -> {}, new DirectExecutorService());

        orchestrator.submitUserMessage("/permissions nope");

        assertEquals("error", orchestrator.status().state());
        assertTrue(orchestrator.status().errorSummary().contains("未知权限模式"));
    }

    private ProviderConfig config() {
        return new ProviderConfig("openai", "gpt-test", URI.create("https://api.openai.com"), "secret", ThinkingConfig.disabled());
    }

    private static class CapturingProvider implements ChatProvider {
        private int calls;

        @Override
        public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config) {
            calls++;
            return Stream.empty();
        }
    }

    private static class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public void execute(Runnable command) { command.run(); }
    }
}
