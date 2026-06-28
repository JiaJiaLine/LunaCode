package com.lunacode.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.conversation.MessageRole;
import com.lunacode.provider.ChatProvider;
import com.lunacode.stream.StreamEvent;
import com.lunacode.tool.DefaultToolPermissionGateway;
import com.lunacode.tool.DefaultToolRegistry;
import com.lunacode.tool.ToolBatchPlanner;
import com.lunacode.tool.ToolExecutor;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ToolUse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAgentLoopTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void runsMultipleTurnsAndFeedsToolResultsBack() {
        ConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider(List.of(
                Stream.of(new StreamEvent.ToolUse("toolu_1", "ReadFile", mapper.createObjectNode().put("path", "pom.xml")), new StreamEvent.MessageStop(com.lunacode.conversation.TokenUsage.unknown())),
                Stream.of(new StreamEvent.ContentDelta(0, "done"), new StreamEvent.MessageStop(com.lunacode.conversation.TokenUsage.unknown()))
        ));
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new StubReadTool());
        List<AgentEvent> events = new ArrayList<>();
        ToolExecutor executor = use -> ToolResult.success("tool result", Map.of());
        DefaultAgentLoop loop = new DefaultAgentLoop(manager, provider, config(), registry, executor, new ToolBatchPlanner(), new DefaultToolPermissionGateway(Path.of(".").toAbsolutePath().normalize()));

        loop.run(new AgentRequest("read", new AgentRunConfig(Path.of("."), AgentMode.DEFAULT, Path.of("plan.md"), 4, 3, Clock.systemUTC())), events::add, new CancellationToken());

        assertEquals(2, provider.calls);
        assertEquals(MessageRole.TOOL, manager.snapshot().get(2).role());
        assertTrue(events.stream().anyMatch(AgentEvent.ToolResultReady.class::isInstance));
        assertTrue(events.stream().anyMatch(AgentEvent.LoopComplete.class::isInstance));
    }

    @Test
    void stopsAfterConsecutiveUnknownTools() {
        ConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider(List.of(
                Stream.of(new StreamEvent.ToolUse("1", "Missing", mapper.createObjectNode()), new StreamEvent.MessageStop(com.lunacode.conversation.TokenUsage.unknown())),
                Stream.of(new StreamEvent.ToolUse("2", "Missing", mapper.createObjectNode()), new StreamEvent.MessageStop(com.lunacode.conversation.TokenUsage.unknown()))
        ));
        List<AgentEvent> events = new ArrayList<>();
        DefaultAgentLoop loop = new DefaultAgentLoop(manager, provider, config(), new DefaultToolRegistry(), use -> ToolResult.success("never", Map.of()), new ToolBatchPlanner(), new DefaultToolPermissionGateway(Path.of(".").toAbsolutePath().normalize()));

        loop.run(new AgentRequest("bad", new AgentRunConfig(Path.of("."), AgentMode.DEFAULT, Path.of("plan.md"), 8, 1, Clock.systemUTC())), events::add, new CancellationToken());

        assertEquals(2, provider.calls);
        assertTrue(events.stream().anyMatch(event -> event instanceof AgentEvent.ErrorOccurred error && error.message().contains("未知工具")));
    }

    private ProviderConfig config() {
        return new ProviderConfig("anthropic", "claude-test", URI.create("https://api.anthropic.com"), "secret", ThinkingConfig.disabled());
    }

    private class CapturingProvider implements ChatProvider {
        private final List<Stream<StreamEvent>> streams;
        private int calls;

        private CapturingProvider(List<Stream<StreamEvent>> streams) {
            this.streams = streams;
        }

        @Override
        public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config) {
            return streams.get(calls++);
        }

        @Override
        public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config, ArrayNode enabledTools, String systemPrompt) {
            assertNotNull(systemPrompt);
            return streamChat(messages, config);
        }
    }

    private class StubReadTool implements com.lunacode.tool.Tool {
        @Override public String name() { return "ReadFile"; }
        @Override public String description() { return "read"; }
        @Override public com.fasterxml.jackson.databind.JsonNode inputSchema() { return mapper.createObjectNode().put("type", "object"); }
        @Override public ToolResult execute(com.lunacode.tool.ToolExecutionContext context, com.fasterxml.jackson.databind.JsonNode input) { return ToolResult.success("ok", Map.of()); }
        @Override public boolean isReadOnly() { return true; }
        @Override public boolean isDestructive() { return false; }
        @Override public boolean isConcurrencySafe(com.fasterxml.jackson.databind.JsonNode input) { return true; }
        @Override public String category() { return "test"; }
        @Override public com.lunacode.tool.ValidationError validateInput(com.fasterxml.jackson.databind.JsonNode input) { return null; }
    }
}