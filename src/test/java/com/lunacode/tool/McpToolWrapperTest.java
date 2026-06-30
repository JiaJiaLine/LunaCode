package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.mcp.McpSession;
import com.lunacode.mcp.McpToolDefinition;
import com.lunacode.mcp.transport.McpTransport;
import com.lunacode.mcp.transport.McpTransportListener;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class McpToolWrapperTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exposesConservativeToolAttributesAndDefersByDefault() {
        McpToolWrapper wrapper = new McpToolWrapper(definition(), initializedSession(new ScriptedTransport(false)));

        assertEquals("mcp_demo_public", wrapper.name());
        assertFalse(wrapper.isReadOnly());
        assertTrue(wrapper.isDestructive());
        assertFalse(wrapper.isConcurrencySafe(mapper.createObjectNode()));
        assertEquals("mcp", wrapper.category());
        assertTrue(wrapper.shouldDefer());
    }

    @Test
    void callsOriginalToolNameWithAgentArgumentsAndRendersSuccess() {
        ScriptedTransport transport = new ScriptedTransport(false);
        McpToolWrapper wrapper = new McpToolWrapper(definition(), initializedSession(transport));
        ObjectNode input = mapper.createObjectNode().put("message", "hello");

        ToolResult result = wrapper.execute(context(), input);

        assertFalse(result.isError(), result.content());
        assertTrue(result.content().contains("hello"));
        assertEquals(1, transport.toolCalls.get());
        assertEquals("remote_echo", transport.lastToolName.get());
        assertEquals("hello", transport.lastArguments.get().path("message").asText());
    }

    @Test
    void convertsProtocolFailureToOrdinaryToolError() {
        McpToolWrapper wrapper = new McpToolWrapper(definition(), initializedSession(new ScriptedTransport(true)));

        ToolResult result = wrapper.execute(context(), mapper.createObjectNode());

        assertTrue(result.isError());
        assertEquals("mcp_protocol_error", result.metadata().get("errorType"));
        assertEquals("demo", result.metadata().get("server"));
        assertEquals("remote_echo", result.metadata().get("remoteTool"));
    }

    @Test
    void rejectsNonObjectArguments() {
        McpToolWrapper wrapper = new McpToolWrapper(definition(), initializedSession(new ScriptedTransport(false)));

        ValidationError error = wrapper.validateInput(mapper.createArrayNode());

        assertNotNull(error);
        assertEquals("invalid_arguments", error.code());
    }

    private McpToolDefinition definition() {
        return new McpToolDefinition(
                "demo",
                "remote_echo",
                "mcp_demo_public",
                "远端 echo 工具",
                mapper.createObjectNode().put("type", "object")
        );
    }

    private McpSession initializedSession(ScriptedTransport transport) {
        McpSession session = new McpSession(transport, mapper);
        try {
            session.initialize(Duration.ofSeconds(1)).get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return session;
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext(Path.of("."), Duration.ofSeconds(1), 10_000, new SensitiveValueMasker());
    }

    private final class ScriptedTransport implements McpTransport {
        private final boolean failToolCall;
        private final AtomicInteger toolCalls = new AtomicInteger();
        private final AtomicReference<String> lastToolName = new AtomicReference<>();
        private final AtomicReference<JsonNode> lastArguments = new AtomicReference<>();
        private McpTransportListener listener;

        private ScriptedTransport(boolean failToolCall) {
            this.failToolCall = failToolCall;
        }

        @Override
        public String serverName() {
            return "demo";
        }

        @Override
        public CompletableFuture<Void> start(McpTransportListener listener) {
            this.listener = listener;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> send(JsonNode message) {
            if (!message.has("id")) {
                return CompletableFuture.completedFuture(null);
            }
            ObjectNode response = mapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", message.path("id"));
            String method = message.path("method").asText();
            if ("initialize".equals(method)) {
                ObjectNode result = response.putObject("result");
                result.put("protocolVersion", McpSession.PROTOCOL_VERSION);
                result.putObject("capabilities").putObject("tools");
                result.putObject("serverInfo").put("name", "demo");
            } else if ("tools/call".equals(method)) {
                toolCalls.incrementAndGet();
                lastToolName.set(message.path("params").path("name").asText());
                lastArguments.set(message.path("params").path("arguments"));
                if (failToolCall) {
                    response.putObject("error").put("code", -32000).put("message", "remote boom");
                } else {
                    response.putObject("result")
                            .putArray("content")
                            .addObject()
                            .put("type", "text")
                            .put("text", "echo: " + lastArguments.get().path("message").asText());
                }
            }
            listener.onMessage(response);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> closeAsync() {
            return CompletableFuture.completedFuture(null);
        }
    }
}