package com.lunacode.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record McpConfig(
        Map<String, McpServerConfig> servers,
        List<String> warnings
) {
    public McpConfig(Map<String, McpServerConfig> servers) {
        this(servers, List.of());
    }

    public McpConfig {
        servers = servers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(servers));
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static McpConfig empty() {
        return new McpConfig(Map.of(), List.of());
    }
}
