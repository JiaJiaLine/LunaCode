package com.lunacode.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.config.McpConfig;
import com.lunacode.config.McpHttpServerConfig;
import com.lunacode.config.McpServerConfig;
import com.lunacode.config.McpStdioServerConfig;
import com.lunacode.tool.McpToolWrapper;
import com.lunacode.tool.SensitiveValueMasker;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class McpClientManagerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path workspace;

    @Test
    void discoversStdioAndHttpToolsAndCollectsBadSchemaWarning() throws Exception {
        ScriptedHttpServer http = ScriptedHttpServer.start("http_tool", true, 0);
        McpClientManager manager = new McpClientManager(workspace, Set.of());
        try {
            McpDiscoveryResult result = manager.discoverAll(config(Map.of(
                    "stdio", stdioConfig("stdio_tool"),
                    "http", http.config("http")
            )), Duration.ofSeconds(3));

            Set<String> names = result.tools().stream().map(McpToolWrapper::name).collect(Collectors.toSet());
            assertTrue(names.contains("mcp_stdio_stdio_tool"));
            assertTrue(names.contains("mcp_http_http_tool"));
            assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("inputSchema") && warning.contains("bad_tool")));
            assertTrue(result.statuses().stream().anyMatch(status -> status.serverName().equals("stdio") && status.state() == McpServerStatus.State.READY));
            assertTrue(result.statuses().stream().anyMatch(status -> status.serverName().equals("http") && status.state() == McpServerStatus.State.READY));
        } finally {
            manager.close();
            http.stop();
        }
    }

    @Test
    void slowServerTimeoutDoesNotDropFastServerTools() throws Exception {
        ScriptedHttpServer fast = ScriptedHttpServer.start("fast_tool", false, 0);
        ScriptedHttpServer slow = ScriptedHttpServer.start("slow_tool", false, 1_000);
        McpClientManager manager = new McpClientManager(workspace, Set.of());
        try {
            McpDiscoveryResult result = manager.discoverAll(config(Map.of(
                    "fast", fast.config("fast"),
                    "slow", slow.config("slow")
            )), Duration.ofMillis(200));

            assertEquals(1, result.tools().size());
            assertEquals("mcp_fast_fast_tool", result.tools().get(0).name());
            assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("slow")));
            assertTrue(manager.session("fast").isPresent());
        } finally {
            manager.close();
            fast.stop();
            slow.stop();
        }
    }

    @Test
    void repeatedToolCallsReuseDiscoveredSessionWithoutRelistingTools() throws Exception {
        ScriptedHttpServer http = ScriptedHttpServer.start("echo", false, 0);
        McpClientManager manager = new McpClientManager(workspace, Set.of());
        try {
            McpDiscoveryResult result = manager.discoverAll(config(Map.of("http", http.config("http"))), Duration.ofSeconds(3));
            McpToolWrapper wrapper = result.tools().get(0);
            ToolExecutionContext context = new ToolExecutionContext(workspace, Duration.ofSeconds(3), 10_000, new SensitiveValueMasker());

            ToolResult first = wrapper.execute(context, MAPPER.createObjectNode().put("message", "one"));
            ToolResult second = wrapper.execute(context, MAPPER.createObjectNode().put("message", "two"));

            assertFalse(first.isError(), first.content());
            assertFalse(second.isError(), second.content());
            assertEquals(1, http.initializeCount.get());
            assertEquals(1, http.listCount.get());
            assertEquals(2, http.callCount.get());
        } finally {
            manager.close();
            http.stop();
        }
    }

    private McpConfig config(Map<String, McpServerConfig> servers) {
        return new McpConfig(new LinkedHashMap<>(servers));
    }

    private McpStdioServerConfig stdioConfig(String toolName) {
        return new McpStdioServerConfig(
                "stdio",
                javaCommand(),
                List.of("-cp", System.getProperty("java.class.path"), StdioServer.class.getName(), toolName),
                Map.of()
        );
    }

    private static String javaCommand() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static final class ScriptedHttpServer {
        private final HttpServer server;
        private final String toolName;
        private final boolean includeBadTool;
        private final long delayMillis;
        private final AtomicInteger initializeCount = new AtomicInteger();
        private final AtomicInteger listCount = new AtomicInteger();
        private final AtomicInteger callCount = new AtomicInteger();

        private ScriptedHttpServer(HttpServer server, String toolName, boolean includeBadTool, long delayMillis) {
            this.server = server;
            this.toolName = toolName;
            this.includeBadTool = includeBadTool;
            this.delayMillis = delayMillis;
        }

        private static ScriptedHttpServer start(String toolName, boolean includeBadTool, long delayMillis) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ScriptedHttpServer scripted = new ScriptedHttpServer(server, toolName, includeBadTool, delayMillis);
            server.createContext("/mcp", scripted::handle);
            server.start();
            return scripted;
        }

        private McpHttpServerConfig config(String name) {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");
            return new McpHttpServerConfig(name, uri, Map.of());
        }

        private void stop() {
            server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            JsonNode request = MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ObjectNode response = MAPPER.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", request.path("id"));
            String method = request.path("method").asText();
            if ("initialize".equals(method)) {
                initializeCount.incrementAndGet();
                ObjectNode result = response.putObject("result");
                result.put("protocolVersion", McpSession.PROTOCOL_VERSION);
                result.putObject("capabilities").putObject("tools");
                result.putObject("serverInfo").put("name", "http-test");
            } else if ("tools/list".equals(method)) {
                listCount.incrementAndGet();
                ObjectNode result = response.putObject("result");
                var tools = result.putArray("tools");
                tools.addObject()
                        .put("name", toolName)
                        .put("description", "http test tool")
                        .putObject("inputSchema")
                        .put("type", "object");
                if (includeBadTool) {
                    tools.addObject()
                            .put("name", "bad_tool")
                            .put("description", "bad")
                            .putObject("inputSchema")
                            .put("type", "string");
                }
            } else if ("tools/call".equals(method)) {
                callCount.incrementAndGet();
                ObjectNode result = response.putObject("result");
                result.putArray("content").addObject()
                        .put("type", "text")
                        .put("text", "called " + request.path("params").path("name").asText());
            }
            byte[] bytes = MAPPER.writeValueAsBytes(response);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    public static final class StdioServer {
        public static void main(String[] args) throws Exception {
            String toolName = args.length == 0 ? "stdio_tool" : args[0];
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    JsonNode request = MAPPER.readTree(line);
                    if (!request.has("id")) {
                        continue;
                    }
                    ObjectNode response = MAPPER.createObjectNode();
                    response.put("jsonrpc", "2.0");
                    response.set("id", request.path("id"));
                    String method = request.path("method").asText();
                    if ("initialize".equals(method)) {
                        ObjectNode result = response.putObject("result");
                        result.put("protocolVersion", McpSession.PROTOCOL_VERSION);
                        result.putObject("capabilities").putObject("tools");
                        result.putObject("serverInfo").put("name", "stdio-test");
                    } else if ("tools/list".equals(method)) {
                        ObjectNode result = response.putObject("result");
                        result.putArray("tools").addObject()
                                .put("name", toolName)
                                .put("description", "stdio test tool")
                                .putObject("inputSchema")
                                .put("type", "object");
                    } else if ("tools/call".equals(method)) {
                        response.putObject("result")
                                .putArray("content")
                                .addObject()
                                .put("type", "text")
                                .put("text", "stdio called");
                    }
                    writer.println(MAPPER.writeValueAsString(response));
                }
            }
        }
    }
}