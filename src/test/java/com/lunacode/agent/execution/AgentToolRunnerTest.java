package com.lunacode.agent.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.agent.event.AgentEvent;
import com.lunacode.interaction.PermissionConfirmationAnswer;
import com.lunacode.runtime.AgentMode;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.runtime.CancellationToken;
import com.lunacode.tool.DefaultToolRegistry;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolExecutionRecord;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ToolUse;
import com.lunacode.tool.ValidationError;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolRunnerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void askPermissionExecutesToolAfterApproval() {
        DefaultToolRegistry registry = registryWithDestructiveTool();
        AtomicInteger executions = new AtomicInteger();
        List<AgentEvent> events = new ArrayList<>();
        AgentToolRunner runner = new AgentToolRunner(
                registry,
                toolUse -> {
                    executions.incrementAndGet();
                    return ToolResult.success("done", Map.of());
                },
                null,
                (toolUse, tool, mode, planFile) -> com.lunacode.tool.PermissionDecision.ASK,
                request -> PermissionConfirmationAnswer.ALLOW_ONCE
        );

        List<ToolExecutionRecord> records = runner.executeToolBatches(
                List.of(new ToolUse("toolu_1", "WriteFile", mapper.createObjectNode().put("path", "a.txt"))),
                config(),
                new CancellationToken(),
                events::add
        );

        assertEquals(1, executions.get());
        assertEquals(1, records.size());
        assertFalse(records.get(0).result().isError());
        assertTrue(events.stream().anyMatch(event -> event instanceof AgentEvent.PermissionRequested));
        assertInstanceOf(AgentEvent.ToolResultReady.class, events.get(events.size() - 1));
    }

    @Test
    void askPermissionSkipsToolWhenUserDeclines() {
        DefaultToolRegistry registry = registryWithDestructiveTool();
        AtomicInteger executions = new AtomicInteger();
        AgentToolRunner runner = new AgentToolRunner(
                registry,
                toolUse -> {
                    executions.incrementAndGet();
                    return ToolResult.success("done", Map.of());
                },
                null,
                (toolUse, tool, mode, planFile) -> com.lunacode.tool.PermissionDecision.ASK,
                request -> PermissionConfirmationAnswer.DENY
        );

        List<ToolExecutionRecord> records = runner.executeToolBatches(
                List.of(new ToolUse("toolu_1", "WriteFile", mapper.createObjectNode().put("path", "a.txt"))),
                config(),
                new CancellationToken(),
                event -> {}
        );

        assertEquals(0, executions.get());
        assertEquals(1, records.size());
        assertTrue(records.get(0).result().isError());
        assertEquals("permission_denied", records.get(0).result().metadata().get("errorType"));
    }

    private DefaultToolRegistry registryWithDestructiveTool() {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new TestDestructiveTool());
        return registry;
    }

    private AgentRunConfig config() {
        return new AgentRunConfig(Path.of("."), AgentMode.DEFAULT, Path.of(".lunacode/plan.md"), 8, 3, Clock.systemUTC());
    }

    private final class TestDestructiveTool implements Tool {
        @Override
        public String name() {
            return "WriteFile";
        }

        @Override
        public String description() {
            return "测试写入工具";
        }

        @Override
        public JsonNode inputSchema() {
            return mapper.createObjectNode().put("type", "object");
        }

        @Override
        public ToolResult execute(ToolExecutionContext context, JsonNode input) {
            return ToolResult.success("unused", Map.of());
        }

        @Override
        public boolean isReadOnly() {
            return false;
        }

        @Override
        public boolean isDestructive() {
            return true;
        }

        @Override
        public boolean isConcurrencySafe(JsonNode input) {
            return false;
        }

        @Override
        public String category() {
            return "file";
        }

        @Override
        public ValidationError validateInput(JsonNode input) {
            return null;
        }
    }
}
