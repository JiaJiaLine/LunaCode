package com.lunacode.orchestrator;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lunacode.command.SlashCommandCompletion;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.permission.PermissionMode;
import com.lunacode.provider.ChatProvider;
import com.lunacode.runtime.AgentMode;
import com.lunacode.stream.StreamEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlashCommandOrchestratorTest {
    @Test
    void registeredSlashCommandDoesNotEnterAgentConversation() {
        ConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider();
        DefaultChatOrchestrator orchestrator = orchestrator(manager, provider);

        orchestrator.submitUserMessage("/status");

        assertEquals(0, provider.calls);
        assertTrue(manager.snapshot().isEmpty());
        assertTrue(orchestrator.status().errorSummary().contains("Agent 模式"));
    }

    @Test
    void normalInputStillRunsAgent() {
        ConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider();
        DefaultChatOrchestrator orchestrator = orchestrator(manager, provider);

        orchestrator.submitUserMessage("你好");

        assertEquals(1, provider.calls);
        assertEquals("你好", provider.capturedMessages.get(0).textContent());
    }

    @Test
    void unknownSlashCommandShowsHelpHint() {
        DefaultChatOrchestrator orchestrator = orchestrator(new DefaultConversationManager(), new CapturingProvider());

        orchestrator.submitUserMessage("/does-not-exist");

        assertEquals("error", orchestrator.status().state());
        assertTrue(orchestrator.status().errorSummary().contains("未知命令"));
        assertTrue(orchestrator.status().errorSummary().contains("/help"));
    }

    @Test
    void reviewCommandSendsPresetPromptThroughAgentFlow() {
        CapturingProvider provider = new CapturingProvider();
        DefaultChatOrchestrator orchestrator = orchestrator(new DefaultConversationManager(), provider);

        orchestrator.submitUserMessage("/review 并重点看异常处理");

        assertEquals(1, provider.calls);
        String prompt = provider.capturedMessages.get(0).textContent();
        assertTrue(prompt.contains("请审查当前 git diff 中的代码变更"));
        assertTrue(prompt.contains("1. 逻辑错误"));
        assertTrue(prompt.contains("2. 安全问题"));
        assertTrue(prompt.contains("3. 性能问题"));
        assertTrue(prompt.contains("4. 代码风格"));
        assertTrue(prompt.contains("额外关注：并重点看异常处理"));
    }

    @Test
    void planAndDoSwitchAgentModeAndRestorePermissionWhenUnchanged() {
        CapturingProvider provider = new CapturingProvider();
        DefaultChatOrchestrator orchestrator = orchestrator(new DefaultConversationManager(), provider);

        orchestrator.submitUserMessage("/permission acceptEdits");
        orchestrator.submitUserMessage("/plan");

        assertEquals(AgentMode.PLAN, orchestrator.status().agentMode());
        assertEquals(PermissionMode.PLAN, orchestrator.status().permissionMode());
        assertEquals(0, provider.calls);

        orchestrator.submitUserMessage("/do");

        assertEquals(AgentMode.DEFAULT, orchestrator.status().agentMode());
        assertEquals(PermissionMode.ACCEPT_EDITS, orchestrator.status().permissionMode());
    }

    @Test
    void doKeepsPermissionManuallyChangedDuringPlanMode() {
        DefaultChatOrchestrator orchestrator = orchestrator(new DefaultConversationManager(), new CapturingProvider());

        orchestrator.submitUserMessage("/plan");
        orchestrator.submitUserMessage("/permission acceptEdits");
        orchestrator.submitUserMessage("/do");

        assertEquals(AgentMode.DEFAULT, orchestrator.status().agentMode());
        assertEquals(PermissionMode.ACCEPT_EDITS, orchestrator.status().permissionMode());
    }

    @Test
    void dangerousPermissionConfirmationCanBeCancelledByCancelCommand() {
        DefaultChatOrchestrator orchestrator = orchestrator(new DefaultConversationManager(), new CapturingProvider());

        orchestrator.submitUserMessage("/permission bypassPermissions");
        assertEquals("waiting_permission", orchestrator.status().state());

        orchestrator.submitUserMessage("/x");

        assertEquals("cancelled", orchestrator.status().state());
        assertEquals(PermissionMode.DEFAULT, orchestrator.status().permissionMode());
    }

    @Test
    void exposesSlashCommandCompletion() {
        DefaultChatOrchestrator orchestrator = orchestrator(new DefaultConversationManager(), new CapturingProvider());

        SlashCommandCompletion completion = orchestrator.completeSlashCommand("/pe", 3);

        SlashCommandCompletion.Single single = assertInstanceOf(SlashCommandCompletion.Single.class, completion);
        assertEquals("/permission", single.replacement());
    }

    private DefaultChatOrchestrator orchestrator(ConversationManager manager, CapturingProvider provider) {
        return new DefaultChatOrchestrator(manager, provider, config(), () -> {}, new DirectExecutorService());
    }

    private ProviderConfig config() {
        return new ProviderConfig("openai", "gpt-test", URI.create("https://api.openai.com"), "secret", ThinkingConfig.disabled());
    }

    private static class CapturingProvider implements ChatProvider {
        private int calls;
        private List<ApiMessage> capturedMessages = List.of();

        @Override
        public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config) {
            calls++;
            capturedMessages = List.copyOf(messages);
            return Stream.empty();
        }

        @Override
        public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config, ArrayNode enabledTools, String systemPrompt) {
            calls++;
            capturedMessages = List.copyOf(messages);
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