package com.lunacode.mcp.rpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.mcp.transport.McpTransport;
import com.lunacode.mcp.transport.McpTransportListener;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class JsonRpcClient implements McpTransportListener, AutoCloseable {
    private static final int METHOD_NOT_FOUND = -32601;
    private final ObjectMapper mapper;
    private final McpTransport transport;
    private final AtomicLong ids = new AtomicLong();
    private final ConcurrentHashMap<String, PendingJsonRpcRequest> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public JsonRpcClient(McpTransport transport) {
        this(transport, new ObjectMapper());
    }

    public JsonRpcClient(McpTransport transport, ObjectMapper mapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory("mcp-jsonrpc-timeout-" + transport.serverName()));
    }

    public CompletableFuture<JsonNode> request(String method, ObjectNode params, Duration timeout) {
        Objects.requireNonNull(method, "method");
        Duration safeTimeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        String id = Long.toString(ids.incrementAndGet());
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        ObjectNode message = mapper.createObjectNode();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("method", method);
        if (params != null) {
            message.set("params", params);
        }
        var timeoutTask = scheduler.schedule(() -> {
            PendingJsonRpcRequest removed = pending.remove(id);
            if (removed != null) {
                removed.future().completeExceptionally(new JsonRpcException(-32000, "请求超时: " + method, null));
            }
        }, safeTimeout.toMillis(), TimeUnit.MILLISECONDS);
        pending.put(id, new PendingJsonRpcRequest(id, method, future, timeoutTask));
        transport.send(message).whenComplete((ignored, error) -> {
            if (error != null) {
                PendingJsonRpcRequest removed = pending.remove(id);
                if (removed != null) {
                    removed.timeoutTask().cancel(false);
                    removed.future().completeExceptionally(error);
                }
            }
        });
        return future;
    }

    public CompletableFuture<Void> notify(String method, JsonNode params) {
        Objects.requireNonNull(method, "method");
        ObjectNode message = mapper.createObjectNode();
        message.put("jsonrpc", "2.0");
        message.put("method", method);
        if (params != null) {
            message.set("params", params);
        }
        return transport.send(message);
    }

    @Override
    public void onMessage(JsonNode message) {
        if (message == null || !message.isObject()) {
            return;
        }
        if (message.hasNonNull("method")) {
            handleServerMethod(message);
            return;
        }
        if (!message.has("id") || message.path("id").isNull()) {
            return;
        }
        String id = message.path("id").asText();
        PendingJsonRpcRequest request = pending.remove(id);
        if (request == null) {
            return;
        }
        request.timeoutTask().cancel(false);
        if (message.has("error")) {
            JsonNode error = message.path("error");
            int code = error.path("code").asInt(-32000);
            String text = error.path("message").asText("MCP JSON-RPC 错误");
            request.future().completeExceptionally(new JsonRpcException(code, text, error.path("data")));
            return;
        }
        request.future().complete(message.path("result"));
    }

    @Override
    public void onClosed(Throwable cause) {
        failAll(cause == null ? new IllegalStateException("MCP 连接已关闭") : cause);
    }

    @Override
    public void onDiagnostic(String message) {
        // 诊断信息由更上层聚合为中文 warning；JSON-RPC 层不解析 stderr/log。
    }

    public CompletableFuture<Void> closeAsync() {
        failAll(new IllegalStateException("MCP JSON-RPC 客户端已关闭"));
        scheduler.shutdownNow();
        return transport.closeAsync();
    }

    @Override
    public void close() {
        closeAsync().join();
    }

    private void handleServerMethod(JsonNode message) {
        if (!message.has("id") || message.path("id").isNull()) {
            return;
        }
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", message.path("id"));
        ObjectNode error = response.putObject("error");
        error.put("code", METHOD_NOT_FOUND);
        error.put("message", "客户端不支持 Server 发起的方法: " + message.path("method").asText());
        transport.send(response);
    }

    private void failAll(Throwable cause) {
        List<PendingJsonRpcRequest> requests = new ArrayList<>(pending.values());
        pending.clear();
        for (PendingJsonRpcRequest request : requests) {
            request.timeoutTask().cancel(false);
            request.future().completeExceptionally(cause);
        }
    }

    public static final class JsonRpcException extends RuntimeException {
        private final int code;
        private final JsonNode data;

        public JsonRpcException(int code, String message, JsonNode data) {
            super(message);
            this.code = code;
            this.data = data;
        }

        public int code() {
            return code;
        }

        public JsonNode data() {
            return data;
        }
    }

    private record DaemonThreadFactory(String name) implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        }
    }
}
