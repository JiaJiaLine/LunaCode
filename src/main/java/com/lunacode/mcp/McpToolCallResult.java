package com.lunacode.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public record McpToolCallResult(
        boolean protocolError,
        boolean remoteError,
        JsonNode result,
        String failureReason
) {
    public static McpToolCallResult success(JsonNode result) {
        return new McpToolCallResult(false, false, result, null);
    }

    public static McpToolCallResult remoteError(JsonNode result) {
        return new McpToolCallResult(false, true, result, null);
    }

    public static McpToolCallResult failure(String failureReason) {
        return new McpToolCallResult(true, false, null, failureReason);
    }

    public boolean isError() {
        return protocolError || remoteError;
    }
}
