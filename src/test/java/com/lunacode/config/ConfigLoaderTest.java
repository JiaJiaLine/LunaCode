package com.lunacode.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsValidConfig() throws Exception {
        Path config = writeConfig("""
                protocol: anthropic
                model: claude-test
                base_url: https://api.anthropic.com
                api_key: key-test
                thinking:
                  enabled: true
                  budget_tokens: 1024
                """);

        ProviderConfig loaded = new ConfigLoader().load(config);

        assertEquals("anthropic", loaded.protocol());
        assertEquals("claude-test", loaded.model());
        assertEquals("https://api.anthropic.com", loaded.baseUrl().toString());
        assertEquals("key-test", loaded.apiKey());
        assertTrue(loaded.thinking().enabled());
        assertEquals(1024, loaded.thinking().budgetTokens());
    }

    @Test
    void rejectsMissingRequiredField() throws Exception {
        Path config = writeConfig("""
                protocol: openai
                base_url: https://api.openai.com
                api_key: key-test
                """);

        ConfigLoader.ConfigException error = assertThrows(
                ConfigLoader.ConfigException.class,
                () -> new ConfigLoader().load(config)
        );

        assertTrue(error.getMessage().contains("model"));
        assertFalse(error.getMessage().contains("key-test"));
    }

    @Test
    void rejectsInvalidProtocol() throws Exception {
        Path config = writeConfig("""
                protocol: deepseek
                model: test
                base_url: https://api.example.com
                api_key: secret-value
                """);

        ConfigLoader.ConfigException error = assertThrows(
                ConfigLoader.ConfigException.class,
                () -> new ConfigLoader().load(config)
        );

        assertTrue(error.getMessage().contains("openai"));
        assertFalse(error.getMessage().contains("secret-value"));
    }

    @Test
    void resolvesEnvironmentPlaceholder() throws Exception {
        Path config = writeConfig("""
                protocol: openai
                model: gpt-test
                base_url: https://api.openai.com
                api_key: ${OPENAI_API_KEY}
                """);

        ProviderConfig loaded = new ConfigLoader(Map.of("OPENAI_API_KEY", "env-secret")).load(config);

        assertEquals("env-secret", loaded.apiKey());
    }

    @Test
    void reportsMissingEnvironmentWithoutLeakingPlaceholderAsSecret() throws Exception {
        Path config = writeConfig("""
                protocol: openai
                model: gpt-test
                base_url: https://api.openai.com
                api_key: ${OPENAI_API_KEY}
                """);

        ConfigLoader.ConfigException error = assertThrows(
                ConfigLoader.ConfigException.class,
                () -> new ConfigLoader(Map.of()).load(config)
        );

        assertTrue(error.getMessage().contains("OPENAI_API_KEY"));
    }

    private Path writeConfig(String content) throws Exception {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, content);
        return config;
    }
}