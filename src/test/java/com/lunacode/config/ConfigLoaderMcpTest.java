package com.lunacode.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderMcpTest {
    @TempDir
    Path tempDir;

    @Test
    void mergesUserAndProjectMcpServersWithProjectOverride() throws Exception {
        Path userConfig = tempDir.resolve("user-config.yaml");
        Files.writeString(userConfig, """
                mcp:
                  servers:
                    shared:
                      command: node
                      args:
                        - ${SCRIPT}
                    user_only:
                      url: http://user.example/mcp
                """);
        Path projectConfig = writeProjectConfig("""
                mcp:
                  servers:
                    shared:
                      url: http://project.example/${ROUTE}
                      headers:
                        Authorization: Bearer ${TOKEN}
                    project_only:
                      command: node
                """);

        ProviderConfig loaded = new ConfigLoader(Map.of(
                "SCRIPT", "toolTest/mcp-test-server.js",
                "ROUTE", "mcp",
                "TOKEN", "secret-token"
        ), userConfig).load(projectConfig);

        assertEquals(3, loaded.mcp().servers().size());
        assertInstanceOf(McpHttpServerConfig.class, loaded.mcp().servers().get("shared"));
        McpHttpServerConfig shared = (McpHttpServerConfig) loaded.mcp().servers().get("shared");
        assertEquals("http://project.example/mcp", shared.url().toString());
        assertEquals("Bearer secret-token", shared.headers().get("Authorization"));
        assertTrue(loaded.mcp().servers().containsKey("user_only"));
        assertTrue(loaded.mcp().servers().containsKey("project_only"));
    }

    @Test
    void skipsInvalidSingleServerAndKeepsOthers() throws Exception {
        Path projectConfig = writeProjectConfig("""
                mcp:
                  servers:
                    bad:
                      command: node
                      env:
                        TOKEN: ${MISSING_TOKEN}
                    good:
                      command: node
                """);

        ProviderConfig loaded = new ConfigLoader(Map.of(), tempDir.resolve("missing-user.yaml")).load(projectConfig);

        assertEquals(1, loaded.mcp().servers().size());
        assertTrue(loaded.mcp().servers().containsKey("good"));
        assertEquals(1, loaded.mcp().warnings().size());
        assertTrue(loaded.mcp().warnings().get(0).contains("bad"));
        assertTrue(loaded.mcp().warnings().get(0).contains("MISSING_TOKEN"));
    }

    private Path writeProjectConfig(String extra) throws Exception {
        Path config = tempDir.resolve("project-config.yaml");
        Files.writeString(config, """
                protocol: anthropic
                model: claude-test
                base_url: https://api.anthropic.com
                api_key: key-test
                """ + extra);
        return config;
    }
}
