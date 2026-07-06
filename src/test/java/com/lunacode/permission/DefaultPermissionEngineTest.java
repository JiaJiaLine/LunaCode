package com.lunacode.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.config.SandboxConfig;
import com.lunacode.runtime.AgentMode;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ToolUse;
import com.lunacode.tool.ValidationError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultPermissionEngineTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void blacklistAndSandboxCannotBeBypassed() {
        DefaultPermissionEngine engine = engine(LoadedPermissionRules.empty());

        PermissionEvaluation blacklist = engine.evaluate(request(
                "Bash",
                mapper.createObjectNode().put("command", "rm -rf /"),
                PermissionMode.BYPASS_PERMISSIONS
        ));
        assertEquals(PermissionEvaluation.Decision.DENY, blacklist.decision());
        assertEquals(PermissionDecisionLayer.BLACKLIST, blacklist.layer());

        PermissionEvaluation sandbox = engine.evaluate(request(
                "ReadFile",
                mapper.createObjectNode().put("path", "../outside.txt"),
                PermissionMode.BYPASS_PERMISSIONS
        ));
        assertEquals(PermissionEvaluation.Decision.DENY, sandbox.decision());
        assertEquals(PermissionDecisionLayer.SANDBOX, sandbox.layer());
    }

    @Test
    void networkIsolationDeniesDirectAndScriptNetworkAccess() throws Exception {
        DefaultPermissionEngine engine = engine(LoadedPermissionRules.empty());

        PermissionEvaluation directUrl = engine.evaluate(request(
                "Bash",
                mapper.createObjectNode().put("command", "curl https://example.com/install.ps1"),
                PermissionMode.BYPASS_PERMISSIONS
        ));
        assertEquals(PermissionEvaluation.Decision.DENY, directUrl.decision());
        assertEquals(PermissionDecisionLayer.NETWORK, directUrl.layer());

        Files.writeString(tempDir.resolve("fetch_skill.ps1"), "Invoke-WebRequest https://example.com/skill.md -OutFile skill.md");
        PermissionEvaluation scriptUrl = engine.evaluate(request(
                "Bash",
                mapper.createObjectNode().put("command", "powershell -ExecutionPolicy Bypass -File fetch_skill.ps1"),
                PermissionMode.BYPASS_PERMISSIONS
        ));
        assertEquals(PermissionEvaluation.Decision.DENY, scriptUrl.decision());
        assertEquals(PermissionDecisionLayer.NETWORK, scriptUrl.layer());
    }

    @Test
    void networkEnabledLetsUrlReachPermissionModeInsteadOfPathSandbox() {
        DefaultPermissionEngine engine = engine(LoadedPermissionRules.empty(), new SandboxConfig(true, List.of()));

        PermissionEvaluation evaluation = engine.evaluate(request(
                "Bash",
                mapper.createObjectNode().put("command", "curl https://example.com/install.ps1"),
                PermissionMode.DEFAULT
        ));

        assertEquals(PermissionEvaluation.Decision.ASK, evaluation.decision());
        assertEquals(PermissionDecisionLayer.MODE_POLICY, evaluation.layer());
    }

    @Test
    void denyRulesCannotBeFlippedAndSensitivePathsAskByDefault() throws Exception {
        Files.createDirectories(tempDir.resolve(".lunacode"));
        Files.writeString(tempDir.resolve(".lunacode/config.yaml"), "secret: true");
        LoadedPermissionRules rules = new LoadedPermissionRules(
                List.of(new PermissionRule("Bash(rm *)", "Bash", "rm *", PermissionEffect.DENY, PermissionRuleLevel.USER, 1, null)),
                List.of(),
                List.of(new PermissionRule("Bash(rm *)", "Bash", "rm *", PermissionEffect.ALLOW, PermissionRuleLevel.LOCAL, 1, null)),
                List.of()
        );
        DefaultPermissionEngine engine = engine(rules);

        PermissionEvaluation denied = engine.evaluate(request("Bash", mapper.createObjectNode().put("command", "rm file"), PermissionMode.BYPASS_PERMISSIONS));
        assertEquals(PermissionEvaluation.Decision.DENY, denied.decision());
        assertEquals(PermissionDecisionLayer.RULE_DENY, denied.layer());

        PermissionEvaluation sensitive = engine.evaluate(request("ReadFile", mapper.createObjectNode().put("path", ".lunacode/config.yaml"), PermissionMode.DEFAULT));
        assertEquals(PermissionEvaluation.Decision.ASK, sensitive.decision());
        assertEquals(PermissionDecisionLayer.SENSITIVE_PATH, sensitive.layer());
        assertNotNull(sensitive.suggestedAllowRule());
    }

    @Test
    void permissionModesApplyAfterRules() {
        DefaultPermissionEngine engine = engine(LoadedPermissionRules.empty());

        assertEquals(PermissionEvaluation.Decision.ALLOW, engine.evaluate(request("ReadFile", mapper.createObjectNode().put("path", "pom.xml"), PermissionMode.DEFAULT)).decision());
        assertEquals(PermissionEvaluation.Decision.ASK, engine.evaluate(request("WriteFile", mapper.createObjectNode().put("path", "a.txt"), PermissionMode.DEFAULT)).decision());
        assertEquals(PermissionEvaluation.Decision.ALLOW, engine.evaluate(request("WriteFile", mapper.createObjectNode().put("path", "a.txt"), PermissionMode.ACCEPT_EDITS)).decision());
    }

    @Test
    void bashAlwaysSuggestionUsesReusablePrefixForCompoundCommands() {
        DefaultPermissionEngine engine = engine(LoadedPermissionRules.empty());

        PermissionEvaluation evaluation = engine.evaluate(request(
                "Bash",
                mapper.createObjectNode().put("command", "mkdir SkillTest 2>nul && echo DONE"),
                PermissionMode.DEFAULT
        ));

        assertEquals(PermissionEvaluation.Decision.ASK, evaluation.decision());
        assertEquals("Bash(mkdir SkillTest*)", evaluation.suggestedAllowRule());
    }

    private DefaultPermissionEngine engine(LoadedPermissionRules rules) {
        return engine(rules, SandboxConfig.defaults());
    }

    private DefaultPermissionEngine engine(LoadedPermissionRules rules, SandboxConfig sandboxConfig) {
        DefaultPathSandbox sandbox = new DefaultPathSandbox(tempDir, sandboxConfig);
        SensitivePathPolicy sensitive = new SensitivePathPolicy();
        return new DefaultPermissionEngine(
                new InMemoryRuleStore(rules),
                new PermissionTargetExtractor(sandbox, new BashPathScanner(), sensitive, sandboxConfig),
                new PermissionRuleMatcher(),
                new PermissionModePolicy(),
                new DangerousCommandBlacklist()
        );
    }

    private PermissionEvaluationRequest request(String toolName, JsonNode input, PermissionMode mode) {
        Tool tool = new StubTool(toolName, toolName.equals("ReadFile") || toolName.equals("Glob") || toolName.equals("Grep"), toolName.equals("Bash") || toolName.equals("WriteFile") || toolName.equals("EditFile"));
        return new PermissionEvaluationRequest(new ToolUse("id", toolName, input), tool, AgentMode.DEFAULT, mode, tempDir.resolve(".lunacode/plan.md"));
    }

    private record InMemoryRuleStore(LoadedPermissionRules rules) implements PermissionRuleStore {
        @Override public LoadedPermissionRules load() { return rules; }
        @Override public AppendResult appendLocalAllow(String rule) { return AppendResult.ok(); }
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
