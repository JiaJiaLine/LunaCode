package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolDescriptionEnhancerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ToolDescriptionEnhancer enhancer = new ToolDescriptionEnhancer();

    @Test
    void enhancesReadWriteAndBashToolsWithStableRules() {
        assertTrue(enhancer.enhance(new StubTool("ReadFile", true, false)).contains("只读探索工具"));
        assertTrue(enhancer.enhance(new StubTool("Glob", true, false)).contains("优先用它理解项目"));
        assertTrue(enhancer.enhance(new StubTool("Grep", true, false)).contains("读取事实"));
        assertTrue(enhancer.enhance(new StubTool("WriteFile", false, true)).contains("编辑前必须先读取目标文件当前内容"));
        assertTrue(enhancer.enhance(new StubTool("EditFile", false, true)).contains("避免盲写"));
        assertTrue(enhancer.enhance(new StubTool("Bash", false, true)).contains("优先使用 ReadFile"));
    }

    @Test
    void registryOutputsEnhancedDescriptionsWithoutChangingSchema() {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new StubTool("ReadFile", true, false));

        var tools = registry.toAPIFormat();

        assertEquals("ReadFile", tools.get(0).path("name").asText());
        assertTrue(tools.get(0).path("description").asText().contains("只读探索工具"));
        assertEquals("object", tools.get(0).path("input_schema").path("type").asText());
    }

    private record StubTool(String name, boolean readOnly, boolean destructive) implements Tool {
        @Override public String description() { return "base"; }
        @Override public JsonNode inputSchema() { return new ObjectMapper().createObjectNode().put("type", "object"); }
        @Override public ToolResult execute(ToolExecutionContext context, JsonNode input) { return ToolResult.success("ok", Map.of()); }
        @Override public boolean isReadOnly() { return readOnly; }
        @Override public boolean isDestructive() { return destructive; }
        @Override public boolean isConcurrencySafe(JsonNode input) { return readOnly; }
        @Override public String category() { return "test"; }
        @Override public ValidationError validateInput(JsonNode input) { return null; }
    }
}
