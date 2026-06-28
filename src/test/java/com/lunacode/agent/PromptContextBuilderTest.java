package com.lunacode.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.conversation.ApiMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptContextBuilderTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void splitsStaticSystemEnvironmentToolsAndMessages() {
        AgentRunConfig config = new AgentRunConfig(Path.of("."), AgentMode.PLAN, Path.of(".lunacode/plan.md"), 4, 3, Clock.fixed(Instant.parse("2026-06-28T00:00:00Z"), ZoneOffset.UTC));
        var tools = mapper.createArrayNode();
        tools.addObject().put("name", "ReadFile").put("description", "read").set("input_schema", mapper.createObjectNode().put("type", "object"));
        List<ApiMessage> history = List.of(new ApiMessage("user", "hi"));

        PromptBundle bundle = new PromptContextBuilder().build(config, 1, history, tools);

        assertNotNull(bundle.system().staticPrompt());
        assertEquals(config.workDir(), bundle.system().environmentContext().workDir());
        assertSame(tools, bundle.toolDeclarations());
        assertEquals(history, bundle.messages().history());
        assertEquals(1, bundle.messages().reminders().size());
        assertTrue(bundle.cachePolicy().cacheStaticSystemPrompt());
        assertTrue(bundle.cachePolicy().cacheToolDeclarations());
        assertFalse(bundle.system().staticPrompt().render().contains(config.workDir().toString()));
    }
}
