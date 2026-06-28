package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.runtime.AgentMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolPermissionGatewayTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path root = Path.of(".").toAbsolutePath().normalize();
    private final DefaultToolPermissionGateway gateway = new DefaultToolPermissionGateway(root);

    @Test
    void defaultMatrixAllowsReadsAndAsksForSideEffects() {
        assertEquals(PermissionDecision.ALLOW, gateway.decide(new ToolUse("1", "ReadFile", mapper.createObjectNode()), new StubTool("ReadFile", true, false), AgentMode.DEFAULT, root.resolve("plan.md")));
        assertEquals(PermissionDecision.ASK, gateway.decide(new ToolUse("2", "WriteFile", mapper.createObjectNode().put("path", "x.txt")), new StubTool("WriteFile", false, true), AgentMode.DEFAULT, root.resolve("plan.md")));
        assertEquals(PermissionDecision.ASK, gateway.decide(new ToolUse("3", "Bash", mapper.createObjectNode()), new StubTool("Bash", false, true), AgentMode.PLAN, root.resolve("plan.md")));
    }

    @Test
    void planModeAllowsOnlyPlanFileAndAskUserQuestion() {
        Path plan = root.resolve(".lunacode/plan.md");
        assertEquals(PermissionDecision.ALLOW, gateway.decide(new ToolUse("1", "WriteFile", mapper.createObjectNode().put("path", ".lunacode/plan.md")), new StubTool("WriteFile", false, true), AgentMode.PLAN, plan));
        assertEquals(PermissionDecision.ASK, gateway.decide(new ToolUse("2", "WriteFile", mapper.createObjectNode().put("path", "src/Main.java")), new StubTool("WriteFile", false, true), AgentMode.PLAN, plan));
        assertEquals(PermissionDecision.ALLOW, gateway.decide(new ToolUse("3", "AskUserQuestion", mapper.createObjectNode()), new StubTool("AskUserQuestion", true, false), AgentMode.PLAN, plan));
        assertEquals(PermissionDecision.DENY, gateway.decide(new ToolUse("4", "AskUserQuestion", mapper.createObjectNode()), new StubTool("AskUserQuestion", true, false), AgentMode.DEFAULT, plan));
    }

    private record StubTool(String name, boolean readOnly, boolean destructive) implements Tool {
        @Override public String description() { return "stub"; }
        @Override public JsonNode inputSchema() { return new ObjectMapper().createObjectNode().put("type", "object"); }
        @Override public ToolResult execute(ToolExecutionContext context, JsonNode input) { return ToolResult.success("ok", Map.of()); }
        @Override public boolean isReadOnly() { return readOnly; }
        @Override public boolean isDestructive() { return destructive; }
        @Override public boolean isConcurrencySafe(JsonNode input) { return readOnly; }
        @Override public String category() { return "test"; }
        @Override public ValidationError validateInput(JsonNode input) { return null; }
    }
}