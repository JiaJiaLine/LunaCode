package com.lunacode.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.concurrent.CompletableFuture;

public interface McpTransport extends AutoCloseable {
    String serverName();

    CompletableFuture<Void> start(McpTransportListener listener);

    CompletableFuture<Void> send(JsonNode message);

    CompletableFuture<Void> closeAsync();

    @Override
    default void close() {
        closeAsync().join();
    }
}
