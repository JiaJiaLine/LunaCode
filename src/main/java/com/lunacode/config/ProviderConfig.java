package com.lunacode.config;

import java.net.URI;

public record ProviderConfig(
        String protocol,
        String model,
        URI baseUrl,
        String apiKey,
        ThinkingConfig thinking,
        AgentConfig agent,
        PermissionConfig permissions,
<<<<<<< Updated upstream
        SandboxConfig sandbox
) {
    public ProviderConfig(String protocol, String model, URI baseUrl, String apiKey, ThinkingConfig thinking) {
        this(protocol, model, baseUrl, apiKey, thinking, AgentConfig.defaults(), PermissionConfig.defaults(), SandboxConfig.defaults());
    }

    public ProviderConfig(String protocol, String model, URI baseUrl, String apiKey, ThinkingConfig thinking, AgentConfig agent) {
        this(protocol, model, baseUrl, apiKey, thinking, agent, PermissionConfig.defaults(), SandboxConfig.defaults());
=======
        SandboxConfig sandbox,
        McpConfig mcp,
        ContextConfig context
) {
    public ProviderConfig(String protocol, String model, URI baseUrl, String apiKey, ThinkingConfig thinking) {
        this(protocol, model, baseUrl, apiKey, thinking, AgentConfig.defaults(), PermissionConfig.defaults(), SandboxConfig.defaults(), McpConfig.empty(), ContextConfig.defaults());
    }

    public ProviderConfig(String protocol, String model, URI baseUrl, String apiKey, ThinkingConfig thinking, AgentConfig agent) {
        this(protocol, model, baseUrl, apiKey, thinking, agent, PermissionConfig.defaults(), SandboxConfig.defaults(), McpConfig.empty(), ContextConfig.defaults());
    }

    public ProviderConfig(String protocol, String model, URI baseUrl, String apiKey, ThinkingConfig thinking, AgentConfig agent, PermissionConfig permissions, SandboxConfig sandbox) {
        this(protocol, model, baseUrl, apiKey, thinking, agent, permissions, sandbox, McpConfig.empty(), ContextConfig.defaults());
    }

    public ProviderConfig(String protocol, String model, URI baseUrl, String apiKey, ThinkingConfig thinking, AgentConfig agent, PermissionConfig permissions, SandboxConfig sandbox, McpConfig mcp) {
        this(protocol, model, baseUrl, apiKey, thinking, agent, permissions, sandbox, mcp, ContextConfig.defaults());
>>>>>>> Stashed changes
    }

    public ProviderConfig {
        if (thinking == null) {
            thinking = ThinkingConfig.disabled();
        }
        if (agent == null) {
            agent = AgentConfig.defaults();
        }
        if (permissions == null) {
            permissions = PermissionConfig.defaults();
        }
        if (sandbox == null) {
            sandbox = SandboxConfig.defaults();
        }
<<<<<<< Updated upstream
=======
        if (mcp == null) {
            mcp = McpConfig.empty();
        }
        if (context == null) {
            context = ContextConfig.defaults();
        }
>>>>>>> Stashed changes
    }
}
