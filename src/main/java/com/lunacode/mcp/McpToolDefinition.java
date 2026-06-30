package com.lunacode.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record McpToolDefinition(
        String serverName,
        String originalName,
        String publicName,
        String description,
        ObjectNode inputSchema
) {
    public McpToolDefinition withPublicName(String publicName) {
        return new McpToolDefinition(serverName, originalName, publicName, description, inputSchema);
    }
}
