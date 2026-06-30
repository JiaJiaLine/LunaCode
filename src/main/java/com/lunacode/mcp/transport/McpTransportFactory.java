package com.lunacode.mcp.transport;

import com.lunacode.config.McpHttpServerConfig;
import com.lunacode.config.McpServerConfig;
import com.lunacode.config.McpStdioServerConfig;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Objects;

public final class McpTransportFactory {
    private final Path workspaceRoot;
    private final HttpClient httpClient;

    public McpTransportFactory(Path workspaceRoot) {
        this(workspaceRoot, HttpClient.newHttpClient());
    }

    public McpTransportFactory(Path workspaceRoot, HttpClient httpClient) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    public McpTransport create(McpServerConfig config) {
        return switch (config.kind()) {
            case STDIO -> new StdioMcpTransport((McpStdioServerConfig) config, workspaceRoot);
            case STREAMABLE_HTTP -> new StreamableHttpMcpTransport((McpHttpServerConfig) config, httpClient);
        };
    }
}
