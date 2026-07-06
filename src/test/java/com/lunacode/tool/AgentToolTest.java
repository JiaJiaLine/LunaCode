package com.lunacode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.hook.HookExecutionScope;
import com.lunacode.runtime.AgentMode;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.subagent.AgentExecutionContextHolder;
import com.lunacode.subagent.AgentToolRequest;
import com.lunacode.subagent.SubAgentLaunchRequest;
import com.lunacode.subagent.SubAgentParentContext;
import com.lunacode.subagent.SubAgentRunHandle;
import com.lunacode.subagent.SubAgentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void omittingSubagentTypeLaunchesForkRequest() throws Exception {
        AtomicReference<AgentToolRequest> seen = new AtomicReference<>();
        AgentTool tool = new AgentTool(new FakeSubAgentService(seen));
        SubAgentParentContext parent = parentContext(false, false);

        ToolResult result = AgentExecutionContextHolder.withContext(parent, () -> tool.execute(null, MAPPER.createObjectNode()
                .put("task", "summarize-current-risk")));

        assertFalse(result.isError());
        assertEquals("summarize-current-risk", seen.get().task());
        assertTrue(seen.get().subagentType().isEmpty());
        assertFalse(seen.get().runInBackground());
    }

    @Test
    void parsesDefinedBackgroundRequest() throws Exception {
        AtomicReference<AgentToolRequest> seen = new AtomicReference<>();
        AgentTool tool = new AgentTool(new FakeSubAgentService(seen));

        ToolResult result = AgentExecutionContextHolder.withContext(parentContext(false, false), () -> tool.execute(null, MAPPER.createObjectNode()
                .put("task", "security-review")
                .put("subagent_type", "security-reviewer")
                .put("run_in_background", true)));

        assertFalse(result.isError());
        assertEquals("security-reviewer", seen.get().subagentType().orElseThrow());
        assertTrue(seen.get().runInBackground());
    }

    @Test
    void validatesTask() {
        AgentTool tool = new AgentTool(new FakeSubAgentService(new AtomicReference<>()));
        assertEquals("missing_task", tool.validateInput(MAPPER.createObjectNode()).code());
    }

    private SubAgentParentContext parentContext(boolean background, boolean fork) {
        AgentRunConfig config = new AgentRunConfig(tempDir, AgentMode.DEFAULT, tempDir.resolve("plan.md"), 8, 3, Clock.systemUTC())
                .asSubAgent(background, fork);
        return new SubAgentParentContext(new DefaultConversationManager(), config, null, background, fork, "session", tempDir);
    }

    private static final class FakeSubAgentService implements SubAgentService {
        private final AtomicReference<AgentToolRequest> seen;

        private FakeSubAgentService(AtomicReference<AgentToolRequest> seen) {
            this.seen = seen;
        }

        @Override
        public ToolResult launchFromTool(AgentToolRequest request, SubAgentParentContext parentContext) {
            seen.set(request);
            return ToolResult.success("ok", Map.of());
        }

        @Override
        public String launchFromHook(String subagentType, String task, HookExecutionScope scope) {
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