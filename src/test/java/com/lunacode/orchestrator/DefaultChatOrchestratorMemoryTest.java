package com.lunacode.orchestrator;

import com.lunacode.agent.event.AgentEvent;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.conversation.MessageRole;
import com.lunacode.memory.AutoMemoryUpdater;
import com.lunacode.memory.MemoryIndexSnapshot;
import com.lunacode.memory.MemoryRuntimeState;
import com.lunacode.memory.MemoryUpdateRequest;
import com.lunacode.provider.ChatProvider;
import com.lunacode.stream.StreamEvent;
import com.lunacode.tool.DefaultToolRegistry;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultChatOrchestratorMemoryTest {
    @Test
    void loopCompleteSchedulesAutoMemoryUpdateWithTurnDelta() {
        DefaultConversationManager manager = new DefaultConversationManager();
        manager.addMessage(MessageRole.USER, "用户喜欢中文回复");
        manager.addAssistantMessage(List.of(new ContentBlock.Text("已记住")));
        CapturingAutoMemoryUpdater updater = new CapturingAutoMemoryUpdater();
        MemoryRuntimeState runtimeState = new MemoryRuntimeState(true);
        MemoryIndexSnapshot index = new MemoryIndexSnapshot("", "", "# 用户级记忆", 1, 16);
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(
                manager,
                new EmptyProvider(),
                config(),
                new DefaultToolRegistry(),
                null,
                new com.lunacode.interaction.BlockingUserQuestionBroker(),
                null,
                null,
                updater,
                runtimeState,
                () -> "20250115-143000-a3f7",
                () -> index,
                null,
                () -> {},
                new DirectExecutorService()
        );

        orchestrator.emit(new AgentEvent.LoopComplete(1));

        assertNotNull(updater.request);
        assertEquals("20250115-143000-a3f7", updater.request.sessionId());
        assertEquals(index.mergedContent(), updater.request.currentIndex().mergedContent());
        assertEquals(2, updater.request.turnDelta().size());
        assertTrue(orchestrator.status().memoryAutoUpdateEnabled());
        assertEquals("20250115-143000-a3f7", orchestrator.status().sessionShortId());
    }

    private ProviderConfig config() {
        return new ProviderConfig("openai", "gpt-test", URI.create("https://api.openai.com"), "secret", ThinkingConfig.disabled());
    }

    private static class CapturingAutoMemoryUpdater implements AutoMemoryUpdater {
        private MemoryUpdateRequest request;

        @Override
        public void updateAsync(MemoryUpdateRequest request) {
            this.request = request;
        }
    }

    private static class EmptyProvider implements ChatProvider {
        @Override
        public Stream<StreamEvent> streamChat(List<com.lunacode.conversation.ApiMessage> messages, ProviderConfig config) {
            return Stream.empty();
        }
    }

    private static class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
