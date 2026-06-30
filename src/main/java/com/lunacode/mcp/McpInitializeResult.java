package com.lunacode.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public record McpInitializeResult(
        String serverName,
        String protocolVersion,
        String serverInfoName,
        JsonNode capabilities
) {}
