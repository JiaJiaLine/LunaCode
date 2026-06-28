package com.lunacode.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.ApiMessage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicProviderSystemPromptTest {
    @Test
    void writesSystemFieldWithoutReplacingToolsOrThinking() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ProviderConfig config = new ProviderConfig("anthropic", "claude-test", URI.create("https://api.anthropic.com"), "secret", new ThinkingConfig(true, 1024));
        var tools = mapper.createArrayNode();
        tools.addObject().put("name", "ReadFile").put("description", "read").set("input_schema", mapper.createObjectNode().put("type", "object"));

        String body = new AnthropicProvider(HttpClient.newHttpClient()).buildRequestBody(List.of(new ApiMessage("user", "hi")), config, tools, "system prompt");
        JsonNode root = mapper.readTree(body);

        assertEquals("system prompt", root.path("system").asText());
        assertEquals("ReadFile", root.path("tools").get(0).path("name").asText());
        assertEquals("enabled", root.path("thinking").path("type").asText());
    }
}