package com.lunacode.config;

import java.util.Map;

public sealed interface McpServerConfig permits McpStdioServerConfig, McpHttpServerConfig {
    String name();

    McpTransportKind kind();

    Map<String, String> sensitiveValues();
}
