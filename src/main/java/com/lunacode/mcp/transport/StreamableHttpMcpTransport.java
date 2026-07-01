package com.lunacode.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.config.McpHttpServerConfig;
import com.lunacode.stream.SseParser;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StreamableHttpMcpTransport implements McpTransport {
    private static final String PROTOCOL_VERSION = "2025-06-18";
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpHttpServerConfig config;
    private final HttpClient httpClient;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile McpTransportListener listener;
    private volatile String sessionId;

    public StreamableHttpMcpTransport(McpHttpServerConfig config, HttpClient httpClient) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public String serverName() {
        return config.name();
    }

    @Override
    public CompletableFuture<Void> start(McpTransportListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> send(JsonNode message) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("MCP HTTP 连接已关闭: " + serverName()));
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(config.url())
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(message.toString()));
        config.headers().forEach(builder::header);
        if (sessionId != null && !sessionId.isBlank()) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(this::handleResponse);
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        closed.set(true);
        return CompletableFuture.completedFuture(null);
    }

    private void handleResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new CompletionException(new IllegalStateException("MCP HTTP 返回状态码 " + response.statusCode()));
        }
        response.headers().firstValue("Mcp-Session-Id")
                .or(() -> response.headers().firstValue("mcp-session-id"))
                .filter(value -> !value.isBlank())
                .ifPresent(value -> sessionId = value);
        String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();
        try {
            if (contentType.contains("text/event-stream")) {
                handleSse(response.body());
            } else if (response.body() != null && !response.body().isBlank()) {
                listener.onMessage(mapper.readTree(response.body()));
            }
        } catch (Exception e) {
            throw new CompletionException(new IllegalStateException("解析 MCP HTTP 响应失败: " + e.getMessage(), e));
        }
    }

    private void handleSse(String body) {
        SseParser parser = new SseParser();
        for (String line : Optional.ofNullable(body).orElse("").lines().toList()) {
            parser.accept(line).ifPresent(event -> emitSseData(event.data()));
        }
        parser.finish().ifPresent(event -> emitSseData(event.data()));
    }

    private void emitSseData(String data) {
        if (data == null || data.isBlank() || "[DONE]".equals(data.strip())) {
            return;
        }
        try {
            listener.onMessage(mapper.readTree(data));
        } catch (Exception e) {
            throw new CompletionException(new IllegalStateException("解析 MCP SSE data 失败: " + e.getMessage(), e));
        }
    }
}
