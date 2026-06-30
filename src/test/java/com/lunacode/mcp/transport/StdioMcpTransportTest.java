package com.lunacode.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.config.McpStdioServerConfig;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StdioMcpTransportTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path workspace = Path.of("target", "stdio-mcp-transport-test").toAbsolutePath().normalize();

    @Test
    void sendsUtf8LineJsonReadsStdoutAndCapturesStderrDiagnostics() throws Exception {
        Files.createDirectories(workspace);
        StdioMcpTransport transport = new StdioMcpTransport(config(), workspace);
        CapturingListener listener = new CapturingListener();

        transport.start(listener).get(5, TimeUnit.SECONDS);
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", "1");
        request.put("method", "echo");
        request.putObject("params").put("message", "你好 MCP");

        transport.send(request).get(5, TimeUnit.SECONDS);
        JsonNode response = listener.nextMessage.get(5, TimeUnit.SECONDS);

        assertEquals("1", response.path("id").asText());
        assertEquals("你好 MCP", response.path("result").path("message").asText());
        assertEquals(workspace.toAbsolutePath().normalize().toString(), response.path("result").path("cwd").asText());
        assertEquals("stdio-secret", response.path("result").path("env").asText());
        assertTrue(listener.diagnostics.stream().anyMatch(line -> line.contains("diagnostic:echo")));

        transport.closeAsync().get(5, TimeUnit.SECONDS);
    }

    @Test
    void invalidJsonFromStdoutClosesTransport() throws Exception {
        Files.createDirectories(workspace);
        McpStdioServerConfig badConfig = new McpStdioServerConfig(
                "bad-stdio",
                javaCommand(),
                List.of("-cp", System.getProperty("java.class.path"), EchoServer.class.getName(), "invalid-json"),
                Map.of()
        );
        StdioMcpTransport transport = new StdioMcpTransport(badConfig, workspace);
        CapturingListener listener = new CapturingListener();

        transport.start(listener).get(5, TimeUnit.SECONDS);
        Throwable cause = listener.closed.get(5, TimeUnit.SECONDS);

        assertNotNull(cause);
        transport.closeAsync().get(5, TimeUnit.SECONDS);
    }

    private McpStdioServerConfig config() {
        return new McpStdioServerConfig(
                "stdio",
                javaCommand(),
                List.of("-cp", System.getProperty("java.class.path"), EchoServer.class.getName()),
                Map.of("TEST_TOKEN", "stdio-secret")
        );
    }

    private static String javaCommand() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static final class CapturingListener implements McpTransportListener {
        private final CompletableFuture<JsonNode> nextMessage = new CompletableFuture<>();
        private final CompletableFuture<Throwable> closed = new CompletableFuture<>();
        private final CopyOnWriteArrayList<String> diagnostics = new CopyOnWriteArrayList<>();

        @Override
        public void onMessage(JsonNode message) {
            nextMessage.complete(message);
        }

        @Override
        public void onClosed(Throwable cause) {
            closed.complete(cause);
        }

        @Override
        public void onDiagnostic(String message) {
            diagnostics.add(message);
        }
    }

    public static final class EchoServer {
        public static void main(String[] args) throws Exception {
            if (args.length > 0 && "invalid-json".equals(args[0])) {
                System.out.println("not-json");
                System.out.flush();
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    JsonNode request = MAPPER.readTree(line);
                    System.err.println("diagnostic:" + request.path("method").asText());
                    ObjectNode response = MAPPER.createObjectNode();
                    response.put("jsonrpc", "2.0");
                    response.set("id", request.path("id"));
                    ObjectNode result = response.putObject("result");
                    result.put("message", request.path("params").path("message").asText());
                    result.put("cwd", Path.of("").toAbsolutePath().normalize().toString());
                    result.put("env", System.getenv("TEST_TOKEN"));
                    writer.println(MAPPER.writeValueAsString(response));
                }
            }
        }
    }
}