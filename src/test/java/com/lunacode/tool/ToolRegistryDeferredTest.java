package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.runtime.AgentMode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryDeferredTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void hidesDeferredToolUntilDiscovered() {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new DeferredTool());

        assertTrue(registry.get("mcp_demo_tool").isEmpty());
        assertEquals(0, registry.toAPIFormat(AgentMode.DEFAULT).size());
        assertEquals(1, registry.deferredToolSummaries().size());

        assertTrue(registry.discoverDeferredTool("mcp_demo_tool").isPresent());

        assertTrue(registry.get("mcp_demo_tool").isPresent());
        assertEquals(1, registry.toAPIFormat(AgentMode.DEFAULT).size());
        assertTrue(registry.deferredToolSummaries().isEmpty());
    }

    private final class DeferredTool implements Tool {
        @Override public String name() { return "mcp_demo_tool"; }
        @Override public String description() { return "demo"; }
        @Override public JsonNode inputSchema() { return mapper.createObjectNode().put("type", "object"); }
        @Override public ToolResult execute(ToolExecutionContext context, JsonNode input) { return ToolResult.success("ok", Map.of()); }
        @Override public boolean isReadOnly() { return false; }
        @Override public boolean isDestructive() { return true; }
        @Override public boolean isConcurrencySafe(JsonNode input) { return false; }
        @Override public String category() { return "mcp"; }
        @Override public ValidationError validateInput(JsonNode input) { return null; }
        @Override public boolean shouldDefer() { return true; }
    }
}
