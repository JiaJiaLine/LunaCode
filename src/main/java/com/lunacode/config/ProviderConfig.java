package com.lunacode.config;

import java.net.URI;

public record ProviderConfig(
        String protocol,
        String model,
        URI baseUrl,
        String apiKey,
        ThinkingConfig thinking
) {
    public ProviderConfig {
        if (thinking == null) {
            thinking = ThinkingConfig.disabled();
        }
    }
}