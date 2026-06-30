package com.lunacode.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.runtime.AgentMode;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.tool.DeferredToolSummary;
import com.lunacode.tool.ToolDeclarationSet;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeferredToolPromptTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rendersMcpHintWithoutAddingDeferredSchemaToVisibleTools() {
        ToolDeclarationSet declarations = new ToolDeclarationSet(
                mapper.createArrayNode(),
                List.of(new DeferredToolSummary("mcp_demo_echo", "echo", "mcp"))
        );
        AgentRunConfig config = new AgentRunConfig(Path.of("."), AgentMode.DEFAULT, Path.of("plan.md"), 4, 3, Clock.systemUTC());

        PromptBundle bundle = new PromptContextBuilder().build(config, 1, List.of(new ApiMessage("user", "hi")), declarations);

        assertEquals(0, bundle.toolDeclarations().size());
        assertEquals(1, bundle.messages().reminders().size());
        assertEquals(SystemReminderKind.MCP_HINT, bundle.messages().reminders().get(0).kind());
        assertTrue(bundle.messages().reminders().get(0).content().contains("ToolSearch"));
    }
}
