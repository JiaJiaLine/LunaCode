package com.lunacode.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatProviderFactoryTest {
    @Test
    void createsKnownProviders() {
        ChatProviderFactory factory = new ChatProviderFactory();

        assertInstanceOf(OpenAiProvider.class, factory.create("openai"));
        assertInstanceOf(AnthropicProvider.class, factory.create("anthropic"));
    }

    @Test
    void rejectsUnknownProvider() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new ChatProviderFactory().create("deepseek"));

        assertTrue(error.getMessage().contains("deepseek"));
    }
}