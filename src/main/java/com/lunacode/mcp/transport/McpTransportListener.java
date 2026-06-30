package com.lunacode.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;

public interface McpTransportListener {
    void onMessage(JsonNode message);

    void onClosed(Throwable cause);

    void onDiagnostic(String message);
}
