package com.lunacode.hook;

import com.lunacode.subagent.AgentToolRequest;
import com.lunacode.subagent.SubAgentLaunchRequest;
import com.lunacode.subagent.SubAgentParentContext;
import com.lunacode.subagent.SubAgentRunHandle;
import com.lunacode.subagent.SubAgentService;
import com.lunacode.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealSubAgentHookActionExecutorTest {
    @TempDir
    Path tempDir;

    @Test
    void launchesSubAgentActionAsBackgroundTask() {
        AtomicReference<String> seenType = new AtomicReference<>();
        AtomicReference<String> seenTask = new AtomicReference<>();
        RealSubAgentHookActionExecutor executor = new RealSubAgentHookActionExecutor(new FakeSubAgentService(seenType, seenTask, false));

        HookActionResult result = executor.execute(hook(new HookAction.SubAgent("security-reviewer", "scan code")), HookContext.empty(HookEventName.POST_TOOL_USE), scope());

        assertTrue(result.success());
        assertEquals("security-reviewer", seenType.get());
        assertEquals("scan code", seenTask.get());
        assertEquals("bg-1", result.metadata().get("taskId"));
    }

    @Test
    void returnsFailureWhenServiceIsNotReady() {
        RealSubAgentHookActionExecutor executor = new RealSubAgentHookActionExecutor(() -> null);

        HookActionResult result = executor.execute(hook(new HookAction.SubAgent("security-reviewer", "scan code")), HookContext.empty(HookEventName.POST_TOOL_USE), scope());

        assertFalse(result.success());
        assertEquals("sub_agent_not_ready", result.metadata().get("errorType"));
    }

    @Test
    void wrapsLaunchFailures() {
        RealSubAgentHookActionExecutor executor = new RealSubAgentHookActionExecutor(new FakeSubAgentService(new AtomicReference<>(), new AtomicReference<>(), true));

        HookActionResult result = executor.execute(hook(new HookAction.SubAgent("missing", "scan code")), HookContext.empty(HookEventName.POST_TOOL_USE), scope());

        assertFalse(result.success());
        assertEquals("sub_agent_failed", result.metadata().get("errorType"));
        assertEquals("missing", result.metadata().get("subAgent"));
    }

    private HookDefinition hook(HookAction action) {
        return new HookDefinition(
                "hook-1",
                new HookSource(HookSourceLevel.PROJECT, tempDir.resolve("config.yaml")),
                0,
                HookEventName.POST_TOOL_USE,
                Optional.empty(),
                action,
                false,
                false,
                false,
                Optional.empty(),
                false
        );
    }

    private HookExecutionScope scope() {
        return new HookExecutionScope("session", 1, tempDir);
    }

    private static final class FakeSubAgentService implements SubAgentService {
        private final AtomicReference<String> seenType;
        private final AtomicReference<String> seenTask;
        private final boolean fail;

        private FakeSubAgentService(AtomicReference<String> seenType, AtomicReference<String> seenTask, boolean fail) {
            this.seenType = seenType;
            this.seenTask = seenTask;
            this.fail = fail;
        }

        @Override
        public ToolResult launchFromTool(AgentToolRequest request, SubAgentParentContext parentContext) {
            return ToolResult.success("ok", Map.of());
        }

        @Override
        public String launchFromHook(String subagentType, String task, HookExecutionScope scope) {
            if (fail) {
                throw new IllegalArgumentException("missing role");
            }
            seenType.set(subagentType);
            seenTask.set(task);
            return "bg-1";
        }

        @Override
        public SubAgentRunHandle startForeground(SubAgentLaunchRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String launchBackground(SubAgentLaunchRequest request) {
            return "bg-1";
        }
    }
}