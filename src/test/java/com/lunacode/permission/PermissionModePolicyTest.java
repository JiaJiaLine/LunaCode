package com.lunacode.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.runtime.AgentMode;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ValidationError;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PermissionModePolicyTest {
    private final PermissionModePolicy policy = new PermissionModePolicy();

    @Test
    void defaultAndPlanAllowReadsAndAskWritesAndBash() {
        assertEquals(PermissionEvaluation.Decision.ALLOW, policy.decide(PermissionMode.DEFAULT, AgentMode.DEFAULT, tool("ReadFile", true, false)));
        assertEquals(PermissionEvaluation.Decision.ASK, policy.decide(PermissionMode.DEFAULT, AgentMode.DEFAULT, tool("WriteFile", false, true)));
        assertEquals(PermissionEvaluation.Decision.ASK, policy.decide(PermissionMode.PLAN, AgentMode.PLAN, tool("Bash", false, true)));
    }

    @Test
    void parsesCommonPermissionModeAliases() {
        assertEquals(PermissionMode.ACCEPT_EDITS, PermissionMode.fromConfig("accept-edits"));
        assertEquals(PermissionMode.ACCEPT_EDITS, PermissionMode.fromConfig("accept_edits"));
        assertEquals(PermissionMode.BYPASS_PERMISSIONS, PermissionMode.fromConfig("bypass"));
        assertEquals(PermissionMode.BYPASS_PERMISSIONS, PermissionMode.fromConfig("bypass-permissions"));
    }
    @Test
    void acceptEditsAllowsFileEditsButStillAsksBash() {
        assertEquals(PermissionEvaluation.Decision.ALLOW, policy.decide(PermissionMode.ACCEPT_EDITS, AgentMode.DEFAULT, tool("WriteFile", false, true)));
        assertEquals(PermissionEvaluation.Decision.ALLOW, policy.decide(PermissionMode.ACCEPT_EDITS, AgentMode.DEFAULT, tool("EditFile", false, true)));
        assertEquals(PermissionEvaluation.Decision.ASK, policy.decide(PermissionMode.ACCEPT_EDITS, AgentMode.DEFAULT, tool("Bash", false, true)));
    }

    @Test
    void bypassAllowsOrdinaryToolsAndAskUserQuestionOnlyInPlanAgentMode() {
        assertEquals(PermissionEvaluation.Decision.ALLOW, policy.decide(PermissionMode.BYPASS_PERMISSIONS, AgentMode.DEFAULT, tool("Bash", false, true)));
        assertEquals(PermissionEvaluation.Decision.DENY, policy.decide(PermissionMode.BYPASS_PERMISSIONS, AgentMode.DEFAULT, tool("AskUserQuestion", true, false)));
        assertEquals(PermissionEvaluation.Decision.ALLOW, policy.decide(PermissionMode.DEFAULT, AgentMode.PLAN, tool("AskUserQuestion", true, false)));
    }

    private Tool tool(String name, boolean readOnly, boolean destructive) {
        return new StubTool(name, readOnly, destructive);
    }

    private record StubTool(String name, boolean readOnly, boolean destructive) implements Tool {
        @Override public String description() { return "stub"; }
        @Override public JsonNode inputSchema() { return new ObjectMapper().createObjectNode(); }
        @Override public ToolResult execute(ToolExecutionContext context, JsonNode input) { return ToolResult.success("ok", Map.of()); }
        @Override public boolean isReadOnly() { return readOnly; }
        @Override public boolean isDestructive() { return destructive; }
        @Override public boolean isConcurrencySafe(JsonNode input) { return true; }
        @Override public String category() { return "test"; }
        @Override public ValidationError validateInput(JsonNode input) { return null; }
    }
}
