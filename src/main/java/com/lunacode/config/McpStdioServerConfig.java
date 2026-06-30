package com.lunacode.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record McpStdioServerConfig(
        String name,
        String command,
        List<String> args,
        Map<String, String> env,
        Map<String, String> sensitiveValues
) implements McpServerConfig {
    public McpStdioServerConfig(String name, String command, List<String> args, Map<String, String> env) {
        this(name, command, args, env, env);
    }

    public McpStdioServerConfig {
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(env));
        sensitiveValues = sensitiveValues == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(sensitiveValues));
    }

    @Override
    public McpTransportKind kind() {
        return McpTransportKind.STDIO;
    }
}
