package com.lunacode.subagent;

import com.lunacode.background.BackgroundTaskListener;
import com.lunacode.background.BackgroundTaskManager;
import com.lunacode.background.BackgroundTaskSnapshot;
import com.lunacode.background.ForegroundSubAgentTracker;
import com.lunacode.config.AgentConfig;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.runtime.AgentMode;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.tool.ToolResult;
import com.lunacode.worktree.WorktreeCleanupPolicy;
import com.lunacode.worktree.WorktreeCleanupResult;
import com.lunacode.worktree.WorktreeCreateRequest;
import com.lunacode.worktree.WorktreeCreateResult;
import com.lunacode.worktree.WorktreeKind;
import com.lunacode.worktree.WorktreeManager;
import com.lunacode.worktree.WorktreeRecord;
import com.lunacode.worktree.WorktreeRemoveRequest;
import com.lunacode.worktree.WorktreeRemoveResult;
import com.lunacode.worktree.WorktreeSession;
import com.lunacode.worktree.WorktreeSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSubAgentWorktreeIsolationTest {
    @TempDir Path tempDir;

    @Test
    void definedAgentWithWorktreeIsolationCreatesWorktreeAndInjectsInstructions() {
        WorktreeRecord record = new WorktreeRecord(
                "agent-a1234567",
                WorktreeKind.AGENT,
                tempDir.resolve(".lunacode/worktrees/agent-a1234567"),
                "worktree-agent-a1234567",
                "base",
                "head",
                Optional.of("main"),
                Instant.now(),
                Instant.now(),
                List.of()
        );
        RecordingWorktreeManager worktrees = new RecordingWorktreeManager(record);
        RecordingBackgroundTaskManager background = new RecordingBackgroundTaskManager();
        AgentDefinition definition = new AgentDefinition(
                "reviewer",
                "review code",
                List.of(),
                List.of(),
                "inherit",
                OptionalInt.empty(),
                Optional.empty(),
                AgentIsolation.WORKTREE,
                false,
                "system",
                tempDir.resolve("reviewer.md"),
                AgentDefinitionSourceKind.PROJECT
        );
        DefaultSubAgentService service = new DefaultSubAgentService(
                catalog(definition),
                request -> { throw new AssertionError("foreground runner should not be used"); },
                background,
                new NoopForegroundTracker(),
                providerConfig(),
                worktrees
        );

        ToolResult result = service.launchFromTool(new AgentToolRequest("review src", Optional.of("reviewer"), true), parentContext());

        assertFalse(result.isError());
        assertEquals(1, worktrees.createCalls.get());
        SubAgentLaunchRequest launch = background.request.get();
        assertTrue(launch.worktree().isPresent());
        assertEquals(record.path(), launch.worktree().orElseThrow().path());
        assertTrue(launch.task().contains(record.path().toString()));
        assertTrue(launch.task().contains(record.branchName()));
        assertTrue(launch.task().contains("review src"));
    }

    private AgentDefinitionCatalog catalog(AgentDefinition definition) {
        return new AgentDefinitionCatalog() {
            @Override
            public AgentDefinitionCatalogSnapshot snapshot() {
                return new AgentDefinitionCatalogSnapshot(List.of(definition), List.of());
            }

            @Override
            public Optional<AgentDefinition> find(String agentType) {
                return definition.agentType().equals(agentType) ? Optional.of(definition) : Optional.empty();
            }

            @Override
            public List<AgentDefinitionDiagnostic> diagnostics() {
                return List.of();
            }
        };
    }

    private ProviderConfig providerConfig() {
        AgentConfig agentConfig = new AgentConfig(8, 3, tempDir.resolve("plan.md"), 120_000L, Map.of());
        return new ProviderConfig("openai", "model", URI.create("https://api.example.com"), "key", ThinkingConfig.disabled(), agentConfig);
    }

    private SubAgentParentContext parentContext() {
        AgentRunConfig config = new AgentRunConfig(tempDir, AgentMode.DEFAULT, tempDir.resolve("plan.md"), 8, 3, Clock.systemUTC());
        return new SubAgentParentContext(new DefaultConversationManager(), config, null, false, false, "session", tempDir);
    }

    private static final class RecordingBackgroundTaskManager implements BackgroundTaskManager {
        private final AtomicReference<SubAgentLaunchRequest> request = new AtomicReference<>();

        @Override
        public String launch(SubAgentLaunchRequest request) {
            this.request.set(request);
            return "bg-1";
        }

        @Override
        public String adoptRunning(SubAgentRunHandle handle, String task) {
            return "bg-1";
        }

        @Override
        public Optional<BackgroundTaskSnapshot> get(String taskId) {
            return Optional.empty();
        }

        @Override
        public List<BackgroundTaskSnapshot> list() {
            return List.of();
        }

        @Override
        public void addListener(BackgroundTaskListener listener) {
        }
    }

    private static final class NoopForegroundTracker implements ForegroundSubAgentTracker {
        @Override
        public void setCurrent(SubAgentRunHandle handle, String task) {
        }

        @Override
        public Optional<SubAgentRunHandle> current() {
            return Optional.empty();
        }

        @Override
        public Optional<String> adoptCurrentToBackground() {
            return Optional.empty();
        }

        @Override
        public void clear(SubAgentRunHandle handle) {
        }
    }

    private static final class RecordingWorktreeManager implements WorktreeManager {
        private final WorktreeRecord record;
        private final AtomicInteger createCalls = new AtomicInteger();

        private RecordingWorktreeManager(WorktreeRecord record) {
            this.record = record;
        }

        @Override
        public WorktreeCreateResult create(WorktreeCreateRequest request) {
            createCalls.incrementAndGet();
            assertEquals(WorktreeKind.AGENT, request.kind());
            return new WorktreeCreateResult(record, false, true, "created", List.of());
        }

        @Override public Optional<WorktreeRecord> find(String name) { return Optional.of(record); }
        @Override public List<WorktreeSnapshot> list() { return List.of(); }
        @Override public WorktreeSession enter(String name, String sessionId) { throw new UnsupportedOperationException(); }
        @Override public void exit() {}
        @Override public Optional<WorktreeSession> currentSession() { return Optional.empty(); }
        @Override public Path effectiveWorkDir() { return record.path(); }
        @Override public WorktreeRemoveResult remove(WorktreeRemoveRequest request) { throw new UnsupportedOperationException(); }
        @Override public WorktreeCleanupResult cleanupExpired(WorktreeCleanupPolicy policy) { return new WorktreeCleanupResult(0, 0, 0, 0, List.of(), List.of(), List.of()); }
        @Override public String generateAgentName() { return record.name(); }
        @Override public List<String> startupWarnings() { return List.of(); }
    }
}
