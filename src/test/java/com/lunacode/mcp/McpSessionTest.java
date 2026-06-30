package com.lunacode.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.mcp.transport.McpTransport;
import com.lunacode.mcp.transport.McpTransportListener;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class McpSessionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void initializesListsPagedToolsAndCallsTool() throws Exception {
        ScriptedTransport transport = new ScriptedTransport();
        McpSession session = new McpSession(transport, mapper);

        McpInitializeResult initialized = session.initialize(Duration.ofSeconds(1)).get();
        List<McpToolDefinition> tools = session.listTools(Duration.ofSeconds(1)).get();
        McpToolCallResult call = session.callTool("echo", mapper.createObjectNode().put("message", "hi"), Duration.ofSeconds(1)).get();

        assertEquals("2025-06-18", initialized.protocolVersion());
        assertEquals(2, tools.size());
        assertEquals("echo", tools.get(0).originalName());
        assertFalse(call.isError());
        assertEquals("hi", call.result().path("content").get(0).path("text").asText());
    }

    @Test
    void rejectsIncompatibleProtocolVersion() {
        ScriptedTransport transport = new ScriptedTransport("2024-11-05");
        McpSession session = new McpSession(transport, mapper);

        assertThrows(Exception.class, () -> session.initialize(Duration.ofSeconds(1)).get());
    }

    private final class ScriptedTransport implements McpTransport {
        private final String protocolVersion;
        private McpTransportListener listener;

        private ScriptedTransport() {
            this("2025-06-18");
        }

        private ScriptedTransport(String protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        @Override public String serverName() { return "scripted"; }
        @Override public CompletableFuture<Void> start(McpTransportListener listener) {
            this.listener = listener;
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> send(JsonNode message) {
            if (!message.has("id")) {
                return CompletableFuture.completedFuture(null);
            }
            String method = message.path("method").asText();
            ObjectNode response = mapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", message.path("id"));
            if ("initialize".equals(method)) {
                ObjectNode result = response.putObject("result");
                result.put("protocolVersion", protocolVersion);
                result.putObject("capabilities").putObject("tools");
                result.putObject("serverInfo").put("name", "scripted");
            } else if ("tools/list".equals(method)) {
                ObjectNode result = response.putObject("result");
                var tools = result.putArray("tools");
                if (!message.path("params").hasNonNull("cursor")) {
                    tools.addObject()
                            .put("name", "echo")
                            .put("description", "echo")
                            .putObject("inputSchema")
                            .put("type", "object");
                    result.put("nextCursor", "page-2");
                } else {
                    tools.addObject()
                            .put("name", "status")
                            .put("description", "status")
                            .putObject("inputSchema")
                            .put("type", "object");
                }
            } else if ("tools/call".equals(method)) {
                ObjectNode result = response.putObject("result");
                result.putArray("content").addObject()
                        .put("type", "text")
                        .put("text", message.path("params").path("arguments").path("message").asText());
            }
            listener.onMessage(response);
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> closeAsync() { return CompletableFuture.completedFuture(null); }
    }
}
