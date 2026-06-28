package com.lunacode.provider;

import com.lunacode.prompt.SystemReminderKind;

import com.lunacode.prompt.SystemReminder;

import com.lunacode.prompt.SystemChannel;

import com.lunacode.prompt.StaticSystemPromptBuilder;

import com.lunacode.prompt.PromptCachePolicy;

import com.lunacode.prompt.PromptBundle;

import com.lunacode.prompt.MessageChannel;

import com.lunacode.prompt.GitStatusSnapshot;

import com.lunacode.prompt.EnvironmentContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.agent.*;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.ApiMessage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiPromptAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsPromptBundleToEquivalentMessagesAndTools() throws Exception {
        var root = mapper.readTree(new OpenAiPromptAdapter().buildRequestBody(bundle(), new ProviderConfig("openai", "gpt-test", URI.create("https://api.openai.com"), "secret", ThinkingConfig.disabled())));

        assertEquals("system", root.path("messages").get(0).path("role").asText());
        assertTrue(root.path("messages").get(0).path("content").asText().contains("角色设定"));
        assertEquals("developer", root.path("messages").get(1).path("role").asText());
        assertTrue(root.path("messages").get(1).path("content").asText().contains("环境上下文"));
        assertEquals("system", root.path("messages").get(2).path("role").asText());
        assertEquals("user", root.path("messages").get(3).path("role").asText());
        assertEquals("function", root.path("tools").get(0).path("type").asText());
        assertEquals("ReadFile", root.path("tools").get(0).path("function").path("name").asText());
    }

    private PromptBundle bundle() {
        var tools = mapper.createArrayNode();
        tools.addObject().put("name", "ReadFile").put("description", "read").set("input_schema", mapper.createObjectNode().put("type", "object"));
        return new PromptBundle(
                new SystemChannel(new StaticSystemPromptBuilder().build(), new EnvironmentContext(Path.of("."), "TestOS", Instant.parse("2026-06-28T00:00:00Z"), GitStatusSnapshot.unknown("unknown"))),
                tools,
                new MessageChannel(java.util.Optional.empty(), java.util.Optional.empty(), List.of(new SystemReminder(SystemReminderKind.PLAN_MODE, "plan", 1)), List.of(new ApiMessage("user", "hi"))),
                PromptCachePolicy.enabled()
        );
    }
}
