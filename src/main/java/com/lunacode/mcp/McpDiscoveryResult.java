package com.lunacode.mcp;

import com.lunacode.tool.McpToolWrapper;

import java.util.List;

public record McpDiscoveryResult(
        List<McpToolWrapper> tools,
        List<McpServerStatus> statuses,
        List<String> warnings
) {
    public McpDiscoveryResult {
        tools = tools == null ? List.of() : List.copyOf(tools);
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static McpDiscoveryResult empty() {
        return new McpDiscoveryResult(List.of(), List.of(), List.of());
    }
}
