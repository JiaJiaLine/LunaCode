package com.lunacode.agent.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.interaction.BlockingPermissionConfirmationBroker;
import com.lunacode.interaction.PermissionConfirmationAnswer;
import com.lunacode.interaction.PermissionConfirmationRequest;
import com.lunacode.permission.PermissionDecisionLayer;
import com.lunacode.permission.PermissionEvaluation;
import com.lunacode.permission.PermissionRuleStore;
import com.lunacode.runtime.AgentMode;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.runtime.CancellationToken;
import com.lunacode.tool.DefaultToolRegistry;
import com.lunacode.tool.PermissionDecision;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolExecutionRecord;
import com.lunacode.tool.ToolPermissionGateway;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ToolUse;
import com.lunacode.tool.ValidationError;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolRunnerPermissionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void alwaysAllowAppendsLocalRuleThenExecutes() {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new StubTool("WriteFile"));
        AtomicReference<String> appended = new AtomicReference<>();
        PermissionRuleStore store = new PermissionRuleStore() {
            @Override public com.lunacode.permission.LoadedPermissionRules load() { return com.lunacode.permission.LoadedPermissionRules.empty(); }
            @Override public AppendResult appendLocalAllow(String rule) {
                appended.set(rule);
                return AppendResult.ok();
            }
        };
        AgentToolRunner runner = new AgentToolRunner(
                registry,
                use -> ToolResult.success("done", Map.of()),
                null,
                new AskingGateway("WriteFile(a.txt)"),
                request -> PermissionConfirmationAnswer.ALLOW_ALWAYS,
                store
        );

        List<ToolExecutionRecord> records = runner.executeToolBatches(
                List.of(new ToolUse("toolu_1", "WriteFile", mapper.createObjectNode().put("path", "a.txt"))),
                config(),
                new CancellationToken(),
                event -> {}
        );

        assertEquals("WriteFile(a.txt)", appended.get());
        assertFalse(records.get(0).result().isError());
    }

    @Test
    void denialReturnsToolResultInsteadOfThrowing() {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new StubTool("WriteFile"));
        AgentToolRunner runner = new AgentToolRunner(
                registry,
                use -> ToolResult.success("done", Map.of()),
                null,
                new AskingGateway("WriteFile(a.txt)"),
                request -> PermissionConfirmationAnswer.DENY
        );

        List<ToolExecutionRecord> records = runner.executeToolBatches(
                List.of(new ToolUse("toolu_1", "WriteFile", mapper.createObjectNode().put("path", "a.txt"))),
                config(),
                new CancellationToken(),
                event -> {}
        );

        assertTrue(records.get(0).result().isError());
        assertEquals("permission_denied", records.get(0).result().metadata().get("errorType"));
    }

    @Test
    void blockingBrokerParsesAlwaysAndAllowOnce() throws Exception {
        BlockingPermissionConfirmationBroker broker = new BlockingPermissionConfirmationBroker();
        CompletableFuture<PermissionConfirmationAnswer> answer = CompletableFuture.supplyAsync(() -> broker.confirm(new PermissionConfirmationRequest("id", "Bash", "prompt")));
        while (!broker.hasPendingConfirmation()) {
            Thread.sleep(10);
        }
        broker.answer("始终允许");
        assertEquals(PermissionConfirmationAnswer.ALLOW_ALWAYS, answer.get(1, TimeUnit.SECONDS));
    }

    private AgentRunConfig config() {
        return new AgentRunConfig(Path.of("."), AgentMode.DEFAULT, Path.of(".lunacode/plan.md"), 8, 3, Clock.systemUTC());
    }

    private record AskingGateway(String rule) implements ToolPermissionGateway {
        @Override public PermissionDecision decide(ToolUse toolUse, Tool tool, AgentMode mode, Path planFile) { return PermissionDecision.ASK; }
        @Override public PermissionEvaluation evaluate(ToolUse toolUse, Tool tool, AgentRunConfig config) {
            return PermissionEvaluation.ask(PermissionDecisionLayer.MODE_POLICY, "测试确认", rule, List.of());
        }
    }

    private record StubTool(String name) implements Tool {
        @Override public String description() { return "stub"; }
        @Override public JsonNode inputSchema() { return new ObjectMapper().createObjectNode(); }
        @Override public ToolResult execute(ToolExecutionContext context, JsonNode input) { return ToolResult.success("ok", Map.of()); }
        @Override public boolean isReadOnly() { return false; }
        @Override public boolean isDestructive() { return true; }
        @Override public boolean isConcurrencySafe(JsonNode input) { return false; }
        @Override public String category() { return "test"; }
        @Override public ValidationError validateInput(JsonNode input) { return null; }
    }
}
