package com.lunacode.config;

import java.net.URI;

public record ProviderConfig(
        String protocol,
        String model,
        URI baseUrl,
        String apiKey,
        ThinkingConfig thinking,
        AgentConfig agent
) {
    public ProviderConfig(String protocol, String model, URI baseUrl, String apiKey, ThinkingConfig thinking) {
        this(protocol, model, baseUrl, apiKey, thinking, AgentConfig.defaults());
    }

    public ProviderConfig {
        if (thinking == null) {
            thinking = ThinkingConfig.disabled();
        }
        if (agent == null) {
            agent = AgentConfig.defaults();
        }
    }
}
