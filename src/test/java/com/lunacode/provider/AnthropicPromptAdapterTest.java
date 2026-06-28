package com.lunacode.provider;

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

class AnthropicPromptAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsPromptBundleToSystemToolsAndMessagesWithCacheMarkers() throws Exception {
        PromptBundle bundle = bundle();
        ProviderConfig config = new ProviderConfig("anthropic", "claude-test", URI.create("https://api.anthropic.com"), "secret", ThinkingConfig.disabled());

        var root = mapper.readTree(new AnthropicPromptAdapter().buildRequestBody(bundle, config));

        assertTrue(root.path("system").isArray());
        assertTrue(root.path("system").get(0).path("text").asText().contains("角色设定"));
        assertTrue(root.path("system").get(0).has("cache_control"));
        assertTrue(root.path("system").get(1).path("text").asText().contains("环境上下文"));
        assertEquals("ReadFile", root.path("tools").get(0).path("name").asText());
        assertTrue(root.path("tools").get(0).has("cache_control"));
        assertTrue(root.path("messages").get(0).path("content").asText().contains("System Reminder"));
        assertEquals("user", root.path("messages").get(1).path("role").asText());
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
