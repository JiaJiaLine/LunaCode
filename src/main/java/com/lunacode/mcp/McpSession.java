package com.lunacode.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.mcp.rpc.JsonRpcClient;
import com.lunacode.mcp.transport.McpTransport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class McpSession implements AutoCloseable {
    public static final String PROTOCOL_VERSION = "2025-06-18";
    private final ObjectMapper mapper;
    private final McpTransport transport;
    private final JsonRpcClient rpc;
    private final AtomicReference<McpServerStatus.State> state = new AtomicReference<>(McpServerStatus.State.CONNECTING);
    private volatile JsonNode capabilities;
    private volatile boolean initialized;

    public McpSession(McpTransport transport) {
        this(transport, new ObjectMapper());
    }

    public McpSession(McpTransport transport, ObjectMapper mapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.rpc = new JsonRpcClient(transport, mapper);
    }

    public String serverName() {
        return transport.serverName();
    }

    public CompletableFuture<McpInitializeResult> initialize(Duration timeout) {
        ObjectNode params = mapper.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.set("capabilities", mapper.createObjectNode());
        ObjectNode clientInfo = mapper.createObjectNode();
        clientInfo.put("name", "LunaCode");
        clientInfo.put("version", "0.1.0");
        params.set("clientInfo", clientInfo);
        return transport.start(rpc)
                .thenCompose(ignored -> rpc.request("initialize", params, timeout))
                .thenCompose(result -> {
                    String version = result.path("protocolVersion").asText("");
                    if (!PROTOCOL_VERSION.equals(version)) {
                        state.set(McpServerStatus.State.FAILED);
                        return CompletableFuture.failedFuture(new IllegalStateException(
                                "协议版本不兼容，期望 " + PROTOCOL_VERSION + "，实际 " + (version.isBlank() ? "<缺失>" : version)
                        ));
                    }
                    capabilities = result.path("capabilities");
                    ObjectNode empty = mapper.createObjectNode();
                    return rpc.notify("notifications/initialized", empty)
                            .thenApply(v -> {
                                initialized = true;
                                state.set(McpServerStatus.State.READY);
                                return new McpInitializeResult(
                                        serverName(),
                                        version,
                                        result.path("serverInfo").path("name").asText(serverName()),
                                        capabilities
                                );
                            });
                });
    }

    public CompletableFuture<List<McpToolDefinition>> listTools(Duration timeout) {
        if (!initialized || state.get() != McpServerStatus.State.READY) {
            return CompletableFuture.failedFuture(new IllegalStateException("MCP Server 尚未初始化: " + serverName()));
        }
        if (capabilities == null || !capabilities.has("tools")) {
            return CompletableFuture.completedFuture(List.of());
        }
        return listToolsPage(null, new ArrayList<>(), timeout);
    }

    public CompletableFuture<McpToolCallResult> callTool(String originalToolName, JsonNode arguments, Duration timeout) {
        if (!initialized || state.get() != McpServerStatus.State.READY) {
            return CompletableFuture.completedFuture(McpToolCallResult.failure("连接不可用或尚未初始化"));
        }
        ObjectNode params = mapper.createObjectNode();
        params.put("name", originalToolName);
        params.set("arguments", arguments != null && arguments.isObject() ? arguments : mapper.createObjectNode());
        return rpc.request("tools/call", params, timeout)
                .thenApply(result -> result.path("isError").asBoolean(false)
                        ? McpToolCallResult.remoteError(result)
                        : McpToolCallResult.success(result))
                .exceptionally(error -> McpToolCallResult.failure(summarize(error)));
    }

    public McpServerStatus status() {
        return new McpServerStatus(serverName(), state.get(), state.get().name().toLowerCase());
    }

    public CompletableFuture<Void> closeAsync() {
        state.set(McpServerStatus.State.CLOSED);
        return rpc.closeAsync();
    }

    @Override
    public void close() {
        closeAsync().join();
    }

    private CompletableFuture<List<McpToolDefinition>> listToolsPage(String cursor, List<McpToolDefinition> collected, Duration timeout) {
        ObjectNode params = mapper.createObjectNode();
        if (cursor != null && !cursor.isBlank()) {
            params.put("cursor", cursor);
        }
        return rpc.request("tools/list", params, timeout)
                .thenCompose(result -> {
                    JsonNode tools = result.path("tools");
                    if (tools.isArray()) {
                        for (JsonNode tool : tools) {
                            parseTool(tool).ifPresent(collected::add);
                        }
                    }
                    String nextCursor = result.path("nextCursor").asText("");
                    if (!nextCursor.isBlank()) {
                        return listToolsPage(nextCursor, collected, timeout);
                    }
                    return CompletableFuture.completedFuture(List.copyOf(collected));
                });
    }

    private java.util.Optional<McpToolDefinition> parseTool(JsonNode tool) {
        String name = tool.path("name").asText("");
        if (name.isBlank()) {
            return java.util.Optional.empty();
        }
        JsonNode rawSchema = tool.path("inputSchema");
        ObjectNode schema = objectSchema(rawSchema).orElse(null);
        if (schema == null) {
            return java.util.Optional.empty();
        }
        String description = tool.path("description").asText("MCP 远端工具: " + name);
        return java.util.Optional.of(new McpToolDefinition(serverName(), name, name, description, schema));
    }

    private java.util.Optional<ObjectNode> objectSchema(JsonNode rawSchema) {
        if (rawSchema == null || rawSchema.isMissingNode() || rawSchema.isNull()) {
            return java.util.Optional.of(mapper.createObjectNode().put("type", "object"));
        }
        if (!rawSchema.isObject()) {
            return java.util.Optional.empty();
        }
        ObjectNode schema = rawSchema.deepCopy();
        JsonNode type = schema.path("type");
        if (!type.isMissingNode() && !type.isNull() && !"object".equals(type.asText())) {
            return java.util.Optional.empty();
        }
        schema.put("type", "object");
        if (!schema.has("properties") || !schema.path("properties").isObject()) {
            schema.set("properties", mapper.createObjectNode());
        }
        return java.util.Optional.of(schema);
    }

    private String summarize(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getMessage() != null && current.getMessage().contains("CompletionException")) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        String oneLine = message.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= 240 ? oneLine : oneLine.substring(0, 240) + "...";
    }
}
