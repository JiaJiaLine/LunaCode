package com.lunacode.config;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

public record McpHttpServerConfig(
        String name,
        URI url,
        Map<String, String> headers,
        Map<String, String> sensitiveValues
) implements McpServerConfig {
    public McpHttpServerConfig(String name, URI url, Map<String, String> headers) {
        this(name, url, headers, headers);
    }

    public McpHttpServerConfig {
        headers = headers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(headers));
        sensitiveValues = sensitiveValues == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(sensitiveValues));
    }

    @Override
    public McpTransportKind kind() {
        return McpTransportKind.STREAMABLE_HTTP;
    }
}
