package com.lunacode.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.ApiMessage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProviderRequestBodyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void anthropicBodyContainsMessagesStreamAndThinking() throws Exception {
        ProviderConfig config = new ProviderConfig(
                "anthropic",
                "claude-test",
                URI.create("https://api.anthropic.com"),
                "secret-key",
                new ThinkingConfig(true, 1024)
        );

        String body = new AnthropicProvider(HttpClient.newHttpClient()).buildRequestBody(messages(), config);
        JsonNode root = mapper.readTree(body);

        assertEquals("claude-test", root.path("model").asText());
        assertTrue(root.path("stream").asBoolean());
        assertEquals("user", root.path("messages").get(0).path("role").asText());
        assertEquals("enabled", root.path("thinking").path("type").asText());
        assertEquals(1024, root.path("thinking").path("budget_tokens").asInt());
        assertFalse(body.contains("secret-key"));
    }

    @Test
    void openAiBodyContainsMessagesStreamAndUsageOption() throws Exception {
        ProviderConfig config = new ProviderConfig(
                "openai",
                "gpt-test",
                URI.create("https://api.openai.com"),
                "secret-key",
                ThinkingConfig.disabled()
        );

        String body = new OpenAiProvider(HttpClient.newHttpClient()).buildRequestBody(messages(), config);
        JsonNode root = mapper.readTree(body);

        assertEquals("gpt-test", root.path("model").asText());
        assertTrue(root.path("stream").asBoolean());
        assertEquals("user", root.path("messages").get(0).path("role").asText());
        assertTrue(root.path("stream_options").path("include_usage").asBoolean());
        assertFalse(body.contains("secret-key"));
    }

    @Test
    void openAiBodyContainsEnabledToolsAsFunctions() throws Exception {
        ProviderConfig config = new ProviderConfig(
                "openai",
                "gpt-test",
                URI.create("https://api.openai.com"),
                "secret-key",
                ThinkingConfig.disabled()
        );
        ArrayNode enabledTools = mapper.createArrayNode();
        enabledTools.addObject()
                .put("name", "WriteFile")
                .put("description", "write a file")
                .set("input_schema", mapper.createObjectNode()
                        .put("type", "object")
                        .set("properties", mapper.createObjectNode()
                                .set("path", mapper.createObjectNode().put("type", "string"))));

        String body = new OpenAiProvider(HttpClient.newHttpClient()).buildRequestBody(messages(), config, enabledTools);
        JsonNode root = mapper.readTree(body);

        assertEquals("auto", root.path("tool_choice").asText());
        assertEquals("function", root.path("tools").get(0).path("type").asText());
        JsonNode function = root.path("tools").get(0).path("function");
        assertEquals("WriteFile", function.path("name").asText());
        assertEquals("write a file", function.path("description").asText());
        assertEquals("object", function.path("parameters").path("type").asText());
    }

    private List<ApiMessage> messages() {
        return List.of(
                new ApiMessage("user", "hello"),
                new ApiMessage("assistant", "hello, I am LunaCode")
        );
    }
}