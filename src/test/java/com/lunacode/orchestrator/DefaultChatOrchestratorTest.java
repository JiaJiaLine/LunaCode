package com.lunacode.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.agent.event.AgentEvent;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.provider.ChatProvider;
import com.lunacode.stream.StreamEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DefaultChatOrchestratorTest {
    @Test
    void streamsAssistantMessageAndSendsApiMessagesBeforePlaceholder() {
        ConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider(Stream.of(
                new StreamEvent.MessageStart(new TokenUsage(2, null, null)),
                new StreamEvent.ContentDelta(0, "你好"),
                new StreamEvent.MessageDelta(new TokenUsage(null, 3, 5), "stop"),
                new StreamEvent.MessageStop(new TokenUsage(2, 3, 5))
        ));
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(manager, provider, config(), () -> {}, new DirectExecutorService());

        orchestrator.submitUserMessage("你好");

        assertEquals(List.of(new ApiMessage("user", "你好")), provider.capturedMessages);
        assertEquals(2, manager.snapshot().size());
        assertEquals(MessageRole.ASSISTANT, manager.snapshot().get(1).role());
        assertEquals(MessageStatus.COMPLETE, manager.snapshot().get(1).status());
        assertEquals("你好", manager.snapshot().get(1).content());
        assertEquals("idle", orchestrator.status().state());
        assertEquals(3, orchestrator.status().outputTokens());
    }

    @Test
    void errorEventMarksAssistantAsErrorAndKeepsErrorStatus() {
        ConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider(Stream.of(new StreamEvent.Error("模拟错误", null)));
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(manager, provider, config(), () -> {}, new DirectExecutorService());

        orchestrator.submitUserMessage("你好");

        assertEquals(MessageStatus.ERROR, manager.snapshot().get(1).status());
        assertEquals("模拟错误", manager.snapshot().get(1).errorSummary());
        assertEquals("error", orchestrator.status().state());
    }

    @Test
    void toolUseStartedCreatesReadableToolStatus() {
        ConversationManager manager = new DefaultConversationManager();
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(manager, new CapturingProvider(Stream.empty()), config(), () -> {}, new DirectExecutorService());
        ObjectMapper mapper = new ObjectMapper();

        orchestrator.emit(new AgentEvent.ToolUseStarted("toolu_1", "WriteFile", mapper.createObjectNode().put("path", "src/Main.java")));

        assertEquals("tool_running", orchestrator.status().state());
        assertEquals("Luna正在使用\"WriteFile\"工具写入\"src/Main.java\"", orchestrator.status().errorSummary());
        assertEquals(orchestrator.status().errorSummary(), orchestrator.status().toolSummary());

        orchestrator.emit(new AgentEvent.ToolUseStarted("toolu_2", "Bash", mapper.createObjectNode().put("command", "mvn test")));

        assertEquals("Luna正在使用\"Bash\"工具执行\"mvn test\"", orchestrator.status().toolSummary());
    }

    @Test
    void permissionRequestKeepsPromptVisibleInStatus() {
        ConversationManager manager = new DefaultConversationManager();
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(manager, new CapturingProvider(Stream.empty()), config(), () -> {}, new DirectExecutorService());

        orchestrator.emit(new AgentEvent.PermissionRequested("toolu_1", "WriteFile", "确认写入 src/Main.java"));

        assertEquals("waiting_permission", orchestrator.status().state());
        assertEquals("确认写入 src/Main.java", orchestrator.status().errorSummary());
        assertEquals("确认写入 src/Main.java", orchestrator.status().toolSummary());
    }
    private ProviderConfig config() {
        return new ProviderConfig("openai", "gpt-test", URI.create("https://api.openai.com"), "secret", ThinkingConfig.disabled());
    }

    private static class CapturingProvider implements ChatProvider {
        private final Stream<StreamEvent> events;
        private List<ApiMessage> capturedMessages;

        private CapturingProvider(Stream<StreamEvent> events) {
            this.events = events;
        }

        @Override
        public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config) {
            capturedMessages = List.copyOf(messages);
            return events;
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