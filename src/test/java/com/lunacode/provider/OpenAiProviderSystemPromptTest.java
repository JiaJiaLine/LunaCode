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

class OpenAiProviderSystemPromptTest {
    @Test
    void prependsSystemMessage() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ProviderConfig config = new ProviderConfig("openai", "gpt-test", URI.create("https://api.openai.com"), "secret", ThinkingConfig.disabled());

        String body = new OpenAiProvider(HttpClient.newHttpClient()).buildRequestBody(List.of(new ApiMessage("user", "hi")), config, mapper.createArrayNode(), "system prompt");
        JsonNode root = mapper.readTree(body);

        assertEquals("system", root.path("messages").get(0).path("role").asText());
        assertEquals("system prompt", root.path("messages").get(0).path("content").asText());
        assertEquals("user", root.path("messages").get(1).path("role").asText());
    }
}