package com.lunacode.config;

import com.lunacode.memory.MemoryConfig;

import java.net.URI;

public record ProviderConfig(
        String protocol,
        String model,
        URI baseUrl,
        String apiKey,
        ThinkingConfig thinking,
        AgentConfig agent,
        PermissionConfig permissions,
        SandboxConfig sandbox,
        McpConfig mcp,
        ContextConfig context,
        MemoryConfig memory,
        FeatureConfig features
) {
    public ProviderConfig(String protocol, String model, URI baseUrl, String apiKey, ThinkingConfig thinking) {
        this(protocol, model, baseUrl, apiKey, thinking, AgentConfig.defaults(), PermissionConfig.defaults(), SandboxConfig.defaults(), McpConfig.empty(), ContextConfig.defaults(), MemoryConfig.defaults(), FeatureConfig.disabled());
    }

    public ProviderConfig(String protocol, String model, URI baseUrl, String apiKey, ThinkingConfig thinking, AgentConfig agent) {
        this(protocol, model, baseUrl, apiKey, thinking, agent, PermissionConfig.defaults(), SandboxConfig.defaults(), McpConfig.empty(), ContextConfig.defaults(), MemoryConfig.defaults(), FeatureConfig.disabled());
    }

    public ProviderConfig(String protocol, String model, URI baseUrl, String apiKey, ThinkingConfig thinking, AgentConfig agent, PermissionConfig permissions, SandboxConfig sandbox) {
        this(protocol, model, baseUrl, apiKey, thinking, agent, permissions, sandbox, McpConfig.empty(), ContextConfig.defaults(), MemoryConfig.defaults(), FeatureConfig.disabled());
    }

    public ProviderConfig(String protocol, String model, URI baseUrl, String apiKey, ThinkingConfig thinking, AgentConfig agent, PermissionConfig permissions, SandboxConfig sandbox, McpConfig mcp) {
        this(protocol, model, baseUrl, apiKey, thinking, agent, permissions, sandbox, mcp, ContextConfig.defaults(), MemoryConfig.defaults(), FeatureConfig.disabled());
    }

    public ProviderConfig(String protocol, String model, URI baseUrl, String apiKey, ThinkingConfig thinking, AgentConfig agent, PermissionConfig permissions, SandboxConfig sandbox, McpConfig mcp, ContextConfig context) {
        this(protocol, model, baseUrl, apiKey, thinking, agent, permissions, sandbox, mcp, context, MemoryConfig.defaults(), FeatureConfig.disabled());
    }

    public ProviderConfig(String protocol, String model, URI baseUrl, String apiKey, ThinkingConfig thinking, AgentConfig agent, PermissionConfig permissions, SandboxConfig sandbox, McpConfig mcp, ContextConfig context, MemoryConfig memory) {
        this(protocol, model, baseUrl, apiKey, thinking, agent, permissions, sandbox, mcp, context, memory, FeatureConfig.disabled());
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
        if (mcp == null) {
            mcp = McpConfig.empty();
        }
        if (context == null) {
            context = ContextConfig.defaults();
        }
        if (memory == null) {
            memory = MemoryConfig.defaults();
        }
        if (features == null) {
            features = FeatureConfig.disabled();
        }
    }
}