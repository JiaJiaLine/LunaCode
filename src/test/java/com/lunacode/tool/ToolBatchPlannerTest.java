package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolBatchPlannerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void groupsReadOnlyConcurrentToolsAndSerializesSideEffects() {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new StubTool("ReadFile", true, false, true));
        registry.register(new StubTool("Grep", true, false, true));
        registry.register(new StubTool("WriteFile", false, true, false));
        registry.register(new StubTool("AskUserQuestion", true, false, false));

        List<ToolBatch> batches = new ToolBatchPlanner().plan(List.of(
                new ToolUse("1", "ReadFile", mapper.createObjectNode()),
                new ToolUse("2", "Grep", mapper.createObjectNode()),
                new ToolUse("3", "WriteFile", mapper.createObjectNode()),
                new ToolUse("4", "AskUserQuestion", mapper.createObjectNode()),
                new ToolUse("5", "Missing", mapper.createObjectNode())
        ), registry);

        assertTrue(batches.get(0).parallel());
        assertEquals(2, batches.get(0).toolUses().size());
        assertFalse(batches.get(1).parallel());
        assertFalse(batches.get(2).parallel());
        assertFalse(batches.get(3).parallel());
    }

    private record StubTool(String name, boolean readOnly, boolean destructive, boolean concurrent) implements Tool {
        @Override public String description() { return "stub"; }
        @Override public JsonNode inputSchema() { return new ObjectMapper().createObjectNode().put("type", "object"); }
        @Override public ToolResult execute(ToolExecutionContext context, JsonNode input) { return ToolResult.success("ok", Map.of()); }
        @Override public boolean isReadOnly() { return readOnly; }
        @Override public boolean isDestructive() { return destructive; }
        @Override public boolean isConcurrencySafe(JsonNode input) { return concurrent; }
        @Override public String category() { return "test"; }
        @Override public ValidationError validateInput(JsonNode input) { return null; }
    }
}