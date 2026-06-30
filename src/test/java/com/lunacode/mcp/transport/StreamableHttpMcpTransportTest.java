package com.lunacode.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.config.McpHttpServerConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StreamableHttpMcpTransportTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void handlesJsonResponsesAndReusesSessionHeader() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> secondSessionHeader = new AtomicReference<>();
        server.createContext("/mcp", exchange -> {
            int index = requests.incrementAndGet();
            JsonNode request = readRequest(exchange);
            ObjectNode response = response(request.path("id").asText(), index);
            if (index == 1) {
                exchange.getResponseHeaders().add("Mcp-Session-Id", "session-123");
            } else {
                secondSessionHeader.set(exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));
            }
            send(exchange, 200, "application/json", MAPPER.writeValueAsString(response));
        });
        server.start();
        try {
            CapturingListener listener = new CapturingListener();
            StreamableHttpMcpTransport transport = new StreamableHttpMcpTransport(config(server), HttpClient.newHttpClient());
            transport.start(listener).join();

            transport.send(request("1")).join();
            assertEquals(1, listener.messages.poll(2, TimeUnit.SECONDS).path("result").path("index").asInt());
            transport.send(request("2")).join();
            assertEquals(2, listener.messages.poll(2, TimeUnit.SECONDS).path("result").path("index").asInt());

            assertEquals("session-123", secondSessionHeader.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void handlesSseResponses() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            JsonNode request = readRequest(exchange);
            String event = "data: " + MAPPER.writeValueAsString(response(request.path("id").asText(), 7)) + "\n\n";
            send(exchange, 200, "text/event-stream", event);
        });
        server.start();
        try {
            CapturingListener listener = new CapturingListener();
            StreamableHttpMcpTransport transport = new StreamableHttpMcpTransport(config(server), HttpClient.newHttpClient());
            transport.start(listener).join();

            transport.send(request("sse-1")).join();
            JsonNode message = listener.messages.poll(2, TimeUnit.SECONDS);

            assertNotNull(message);
            assertEquals("sse-1", message.path("id").asText());
            assertEquals(7, message.path("result").path("index").asInt());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void non2xxResponseFailsOnlyTheSend() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> send(exchange, 500, "application/json", "{}"));
        server.start();
        try {
            StreamableHttpMcpTransport transport = new StreamableHttpMcpTransport(config(server), HttpClient.newHttpClient());
            transport.start(new CapturingListener()).join();

            CompletionException error = assertThrows(CompletionException.class, () -> transport.send(request("1")).join());
            assertTrue(error.getCause().getMessage().contains("500"));
        } finally {
            server.stop(0);
        }
    }

    private McpHttpServerConfig config(HttpServer server) {
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");
        return new McpHttpServerConfig("http", uri, Map.of("Authorization", "Bearer test-token"));
    }

    private ObjectNode request(String id) {
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", "demo");
        request.set("params", MAPPER.createObjectNode());
        return request;
    }

    private static ObjectNode response(String id, int index) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.putObject("result").put("index", index);
        return response;
    }

    private static JsonNode readRequest(HttpExchange exchange) throws IOException {
        return MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class CapturingListener implements McpTransportListener {
        private final LinkedBlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();

        @Override
        public void onMessage(JsonNode message) {
            messages.add(message);
        }

        @Override
        public void onClosed(Throwable cause) {
        }

        @Override
        public void onDiagnostic(String message) {
        }
    }
}