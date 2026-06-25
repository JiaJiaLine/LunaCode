package com.lunacode.provider;

import java.net.http.HttpClient;

public class ChatProviderFactory {
    private final HttpClient httpClient;

    public ChatProviderFactory() {
        this(HttpClient.newHttpClient());
    }

    public ChatProviderFactory(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public ChatProvider create(String protocol) {
        return switch (protocol == null ? "" : protocol.toLowerCase()) {
            case "openai" -> new OpenAiProvider(httpClient);
            case "anthropic" -> new AnthropicProvider(httpClient);
            default -> throw new IllegalArgumentException("不支持的 Provider 协议: " + protocol);
        };
    }
}