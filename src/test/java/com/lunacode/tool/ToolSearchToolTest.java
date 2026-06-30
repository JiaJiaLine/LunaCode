package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolSearchToolTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void discoversDeferredToolWithoutExecutingIt() {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        CountingDeferredTool deferred = new CountingDeferredTool();
        registry.register(deferred);
        ToolSearchTool search = new ToolSearchTool(registry);

        ToolResult result = search.execute(null, mapper.createObjectNode().put("name", "mcp_demo_echo"));

        assertFalse(result.isError(), result.content());
        assertTrue(result.content().contains("mcp_demo_echo"));
        assertTrue(registry.isDeferredDiscovered("mcp_demo_echo"));
        assertEquals(0, deferred.executions);
    }

    @Test
    void missingToolReturnsOrdinaryToolError() {
        ToolSearchTool search = new ToolSearchTool(new DefaultToolRegistry());

        ToolResult result = search.execute(null, mapper.createObjectNode().put("name", "missing"));

        assertTrue(result.isError());
        assertEquals("deferred_tool_not_found", result.metadata().get("errorType"));
    }

    private final class CountingDeferredTool implements Tool {
        private int executions;
        @Override public String name() { return "mcp_demo_echo"; }
        @Override public String description() { return "echo"; }
        @Override public JsonNode inputSchema() { return mapper.createObjectNode().put("type", "object"); }
        @Override public ToolResult execute(ToolExecutionContext context, JsonNode input) {
            executions++;
            return ToolResult.success("ok", Map.of());
        }
        @Override public boolean isReadOnly() { return false; }
        @Override public boolean isDestructive() { return true; }
        @Override public boolean isConcurrencySafe(JsonNode input) { return false; }
        @Override public String category() { return "mcp"; }
        @Override public ValidationError validateInput(JsonNode input) { return null; }
        @Override public boolean shouldDefer() { return true; }
    }
}
