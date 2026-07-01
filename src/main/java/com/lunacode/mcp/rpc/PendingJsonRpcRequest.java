package com.lunacode.mcp.rpc;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

public record PendingJsonRpcRequest(
        String id,
        String method,
        CompletableFuture<JsonNode> future,
        ScheduledFuture<?> timeoutTask
) {}
