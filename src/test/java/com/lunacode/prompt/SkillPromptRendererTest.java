package com.lunacode.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.provider.AnthropicPromptAdapter;
import com.lunacode.provider.OpenAiPromptAdapter;
import com.lunacode.skill.LoadedSkillContext;
import com.lunacode.skill.SkillExecutionMode;
import com.lunacode.skill.SkillOrigin;
import com.lunacode.skill.SkillPromptContext;
import com.lunacode.skill.SkillSourceKind;
import com.lunacode.skill.SkillSummary;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPromptRendererTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void summariesDoNotIncludeFullSop() {
        SkillPromptContext context = new SkillPromptContext(List.of(summary("commit", "commit helper")), Optional.empty());

        String rendered = new SkillPromptRenderer().render(context);

        assertTrue(rendered.contains("/commit"));
        assertTrue(rendered.contains("LoadSkill"));
        assertFalse(rendered.contains("full SOP body"));
    }

    @Test
    void loadedDirectorySkillShowsRootWithoutResourceContents() {
        SkillPromptContext context = new SkillPromptContext(
                List.of(summary("commit", "commit helper")),
                Optional.of(new LoadedSkillContext("commit", "full SOP body", Optional.of(Path.of("skill-dir"))))
        );

        String rendered = new SkillPromptRenderer().render(context);

        assertTrue(rendered.contains("full SOP body"));
        assertTrue(rendered.contains("Resource root:"));
        assertTrue(rendered.contains("Do not preload directory resources"));
        assertFalse(rendered.contains("sample resource content"));
    }

    @Test
    void anthropicAddsSkillContextToSystemBlocks() throws Exception {
        String body = new AnthropicPromptAdapter().buildRequestBody(bundle(skillContext()), providerConfig("anthropic"));

        JsonNode root = MAPPER.readTree(body);

        assertTrue(root.path("system").toString().contains("Available Skills"));
        assertTrue(root.path("system").toString().contains("/commit"));
    }

    @Test
    void openAiAddsSkillContextToDeveloperMessages() throws Exception {
        String body = new OpenAiPromptAdapter().buildRequestBody(bundle(skillContext()), providerConfig("openai"));

        JsonNode root = MAPPER.readTree(body);

        assertTrue(root.path("messages").toString().contains("\"role\":\"developer\""));
        assertTrue(root.path("messages").toString().contains("Available Skills"));
    }

    private SkillPromptContext skillContext() {
        return new SkillPromptContext(List.of(summary("commit", "commit helper")), Optional.empty());
    }

    private SkillSummary summary(String name, String description) {
        return new SkillSummary(
                name,
                description,
                new SkillOrigin(SkillSourceKind.BUILTIN, "builtin-" + name, Optional.empty(), 100),
                SkillExecutionMode.INLINE
        );
    }

    private PromptBundle bundle(SkillPromptContext skillContext) {
        return new PromptBundle(
                new SystemChannel(staticPrompt(), new EnvironmentContext(Path.of("."), "test", Instant.EPOCH, GitStatusSnapshot.unknown("unknown"))),
                MAPPER.createArrayNode(),
                new MessageChannel(Optional.empty(), Optional.empty(), skillContext, List.of(), List.of(new ApiMessage("user", "hello"))),
                new PromptCachePolicy(false, false)
        );
    }

    private StaticSystemPrompt staticPrompt() {
        return new StaticSystemPrompt(Arrays.stream(PromptSectionKind.values())
                .map(kind -> new PromptSection(kind, kind.title(), "content " + kind.name()))
                .toList());
    }

    private ProviderConfig providerConfig(String protocol) {
        return new ProviderConfig(protocol, "test-model", URI.create("http://localhost"), "key", ThinkingConfig.disabled());
    }
}
