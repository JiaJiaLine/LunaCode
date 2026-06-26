package com.lunacode.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.provider.ChatProvider;
import com.lunacode.stream.StreamEvent;
import com.lunacode.tool.DefaultToolRegistry;
import com.lunacode.tool.ToolExecutor;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ToolUse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ToolOrchestratorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void executesToolAddsToolResultAndRequestsFinalReplyWithoutTools() {
        ConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider(List.of(
                Stream.of(
                        new StreamEvent.MessageStart(com.lunacode.conversation.TokenUsage.unknown()),
                        new StreamEvent.ContentDelta(0, "我来读取。"),
                        new StreamEvent.ToolUse("toolu_1", "ReadFile", mapper.createObjectNode().put("path", "pom.xml")),
                        new StreamEvent.MessageStop(com.lunacode.conversation.TokenUsage.unknown())
                ),
                Stream.of(
                        new StreamEvent.ContentDelta(0, "pom.xml 里包含 Maven 依赖。"),
                        new StreamEvent.MessageStop(com.lunacode.conversation.TokenUsage.unknown())
                )
        ));
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new StubTool("ReadFile"));
        ToolExecutor executor = use -> ToolResult.success("1\t<project></project>", Map.of("path", "pom.xml"));
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(manager, provider, config(), registry, executor, () -> {}, new DirectExecutorService());

        orchestrator.submitUserMessage("读取 pom.xml");

        assertEquals(2, provider.capturedMessages.size());
        assertFalse(provider.capturedTools.get(0).isEmpty());
        assertTrue(provider.capturedTools.get(1).isEmpty());
        assertEquals(4, manager.snapshot().size());
        assertEquals(MessageRole.TOOL, manager.snapshot().get(2).role());
        assertEquals("1\t<project></project>", manager.snapshot().get(2).content());
        assertEquals(MessageStatus.COMPLETE, manager.snapshot().get(3).status());
        assertTrue(manager.snapshot().get(3).content().contains("Maven"));
        assertEquals("idle", orchestrator.status().state());

        ApiMessage assistantWithTool = provider.capturedMessages.get(1).get(1);
        assertEquals("assistant", assistantWithTool.role());
        assertTrue(assistantWithTool.content().stream().anyMatch(ContentBlock.ToolUseBlock.class::isInstance));
        ApiMessage resultMessage = provider.capturedMessages.get(1).get(2);
        assertEquals("user", resultMessage.role());
        assertInstanceOf(ContentBlock.ToolResultBlock.class, resultMessage.content().get(0));
    }

    @Test
    void secondToolUseIsNotExecuted() {
        ConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider(List.of(
                Stream.of(new StreamEvent.ToolUse("toolu_1", "ReadFile", mapper.createObjectNode()), new StreamEvent.MessageStop(com.lunacode.conversation.TokenUsage.unknown())),
                Stream.of(new StreamEvent.ToolUse("toolu_2", "ReadFile", mapper.createObjectNode()), new StreamEvent.MessageStop(com.lunacode.conversation.TokenUsage.unknown()))
        ));
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new StubTool("ReadFile"));
        List<ToolUse> executed = new ArrayList<>();
        ToolExecutor executor = use -> {
            executed.add(use);
            return ToolResult.success("ok", Map.of());
        };
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(manager, provider, config(), registry, executor, () -> {}, new DirectExecutorService());

        orchestrator.submitUserMessage("读文件");

        assertEquals(1, executed.size());
        assertTrue(manager.snapshot().get(3).content().contains("不支持工具结果回灌后的连环工具调用"));
    }

    private ProviderConfig config() {
        return new ProviderConfig("anthropic", "claude-test", URI.create("https://api.anthropic.com"), "secret", ThinkingConfig.disabled());
    }

    private static class CapturingProvider implements ChatProvider {
        private final List<Stream<StreamEvent>> streams;
        private final List<List<ApiMessage>> capturedMessages = new ArrayList<>();
        private final List<ArrayNode> capturedTools = new ArrayList<>();
        private int index;

        private CapturingProvider(List<Stream<StreamEvent>> streams) {
            this.streams = streams;
        }

        @Override
        public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config) {
            return streamChat(messages, config, new ObjectMapper().createArrayNode());
        }

        @Override
        public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config, ArrayNode enabledTools) {
            capturedMessages.add(List.copyOf(messages));
            capturedTools.add(enabledTools == null ? new ObjectMapper().createArrayNode() : enabledTools.deepCopy());
            return streams.get(index++);
        }
    }

    private static class StubTool implements com.lunacode.tool.Tool {
        private final String name;

        private StubTool(String name) {
            this.name = name;
        }

        @Override public String name() { return name; }
        @Override public String description() { return "stub"; }
        @Override public com.fasterxml.jackson.databind.JsonNode inputSchema() { return new ObjectMapper().createObjectNode().put("type", "object"); }
        @Override public ToolResult execute(com.lunacode.tool.ToolExecutionContext context, com.fasterxml.jackson.databind.JsonNode input) { return ToolResult.success("stub", Map.of()); }
        @Override public boolean isReadOnly() { return true; }
        @Override public boolean isDestructive() { return false; }
        @Override public boolean isConcurrencySafe(com.fasterxml.jackson.databind.JsonNode input) { return true; }
        @Override public String category() { return "test"; }
        @Override public com.lunacode.tool.ValidationError validateInput(com.fasterxml.jackson.databind.JsonNode input) { return null; }
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