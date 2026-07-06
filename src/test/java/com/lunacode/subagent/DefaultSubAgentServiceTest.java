package com.lunacode.subagent;

import com.lunacode.background.BackgroundTaskListener;
import com.lunacode.background.BackgroundTaskManager;
import com.lunacode.background.BackgroundTaskSnapshot;
import com.lunacode.background.ForegroundSubAgentTracker;
import com.lunacode.background.ProgressTracker;
import com.lunacode.config.AgentConfig;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.runtime.AgentMode;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSubAgentServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void omittingSubagentTypeLaunchesForkInBackground() {
        RecordingBackgroundTaskManager background = new RecordingBackgroundTaskManager("bg-fork");
        DefaultSubAgentService service = service(catalog(), request -> completedHandle("done"), background, new RecordingForegroundTracker(), providerConfig(120_000L));

        ToolResult result = service.launchFromTool(new AgentToolRequest("summarize", Optional.empty(), false), parentContext(false, false));

        assertFalse(result.isError());
        assertEquals("async_launched", result.metadata().get("status"));
        assertEquals("bg-fork", result.metadata().get("taskId"));
        assertEquals("fork", result.metadata().get("agentType"));
        assertEquals(SubAgentKind.FORK, background.launchedRequest.get().kind());
        assertTrue(background.launchedRequest.get().requestedBackground());
    }

    @Test
    void forkParentCannotForkAgain() {
        DefaultSubAgentService service = service(catalog(), request -> completedHandle("done"), new RecordingBackgroundTaskManager("bg"), new RecordingForegroundTracker(), providerConfig(120_000L));

        ToolResult result = service.launchFromTool(new AgentToolRequest("summarize", Optional.empty(), false), parentContext(false, true));

        assertTrue(result.isError());
        assertEquals("fork_cannot_fork", result.metadata().get("errorType"));
    }

    @Test
    void backgroundParentCannotSpawnAnyAgent() {
        DefaultSubAgentService service = service(catalog(definition("reviewer")), request -> completedHandle("done"), new RecordingBackgroundTaskManager("bg"), new RecordingForegroundTracker(), providerConfig(120_000L));

        ToolResult result = service.launchFromTool(new AgentToolRequest("review", Optional.of("reviewer"), true), parentContext(true, false));

        assertTrue(result.isError());
        assertEquals("nested_agent_forbidden", result.metadata().get("errorType"));
    }

    @Test
    void missingDefinedAgentReturnsToolError() {
        DefaultSubAgentService service = service(catalog(), request -> completedHandle("done"), new RecordingBackgroundTaskManager("bg"), new RecordingForegroundTracker(), providerConfig(120_000L));

        ToolResult result = service.launchFromTool(new AgentToolRequest("review", Optional.of("missing"), false), parentContext(false, false));

        assertTrue(result.isError());
        assertEquals("subagent_not_found", result.metadata().get("errorType"));
    }

    @Test
    void definedBackgroundLaunchesThroughTaskManager() {
        AgentDefinition definition = definition("reviewer");
        RecordingBackgroundTaskManager background = new RecordingBackgroundTaskManager("bg-defined");
        DefaultSubAgentService service = service(catalog(definition), request -> completedHandle("done"), background, new RecordingForegroundTracker(), providerConfig(120_000L));

        ToolResult result = service.launchFromTool(new AgentToolRequest("review", Optional.of("reviewer"), true), parentContext(false, false));

        assertFalse(result.isError());
        assertEquals("bg-defined", result.metadata().get("taskId"));
        assertEquals("reviewer", result.metadata().get("agentType"));
        assertEquals(SubAgentKind.DEFINED, background.launchedRequest.get().kind());
        assertEquals("review", background.launchedRequest.get().task());
    }
    @Test
    void definitionBackgroundFlagForcesBackgroundLaunch() {
        AgentDefinition definition = definition("verifier", true);
        RecordingBackgroundTaskManager background = new RecordingBackgroundTaskManager("bg-verifier");
        DefaultSubAgentService service = service(catalog(definition), request -> completedHandle("done"), background, new RecordingForegroundTracker(), providerConfig(120_000L));

        ToolResult result = service.launchFromTool(new AgentToolRequest("verify", Optional.of("verifier"), false), parentContext(false, false));

        assertFalse(result.isError());
        assertEquals("async_launched", result.metadata().get("status"));
        assertEquals("bg-verifier", result.metadata().get("taskId"));
        assertTrue(background.launchedRequest.get().requestedBackground());
    }

    @Test
    void autoBackgroundAfterTimeoutAdoptsForegroundHandle() {
        AgentDefinition definition = definition("reviewer");
        CompletableFuture<SubAgentResult> neverCompletes = new CompletableFuture<>();
        DefaultSubAgentRunHandle handle = new DefaultSubAgentRunHandle("child-1", neverCompletes, null, new ProgressTracker());
        RecordingForegroundTracker foreground = new RecordingForegroundTracker();
        RecordingBackgroundTaskManager background = new RecordingBackgroundTaskManager("bg-timeout");
        DefaultSubAgentService service = service(catalog(definition), request -> handle, background, foreground, providerConfig(1L));

        ToolResult result = service.launchFromTool(new AgentToolRequest("review", Optional.of("reviewer"), false), parentContext(false, false));

        assertFalse(result.isError());
        assertEquals("async_launched", result.metadata().get("status"));
        assertEquals("bg-adopted", result.metadata().get("taskId"));
        assertEquals(1, foreground.setCalls.get());
        assertEquals(1, foreground.adoptCalls.get());
        assertTrue(handle.backgroundTaskId().isPresent());
    }

    private DefaultSubAgentService service(
            AgentDefinitionCatalog catalog,
            SubAgentRunnerFactory runnerFactory,
            BackgroundTaskManager background,
            ForegroundSubAgentTracker foreground,
            ProviderConfig providerConfig
    ) {
        return new DefaultSubAgentService(catalog, runnerFactory, background, foreground, providerConfig);
    }

    private ProviderConfig providerConfig(long autoBackgroundMs) {
        AgentConfig agentConfig = new AgentConfig(8, 3, tempDir.resolve("plan.md"), autoBackgroundMs, Map.of());
        return new ProviderConfig("openai", "model", URI.create("https://api.example.com"), "key", ThinkingConfig.disabled(), agentConfig);
    }

    private SubAgentParentContext parentContext(boolean background, boolean fork) {
        AgentRunConfig config = new AgentRunConfig(tempDir, AgentMode.DEFAULT, tempDir.resolve("plan.md"), 8, 3, Clock.systemUTC())
                .asSubAgent(background, fork);
        return new SubAgentParentContext(new DefaultConversationManager(), config, null, background, fork, "session", tempDir);
    }

    private DefaultSubAgentRunHandle completedHandle(String result) {
        return new DefaultSubAgentRunHandle(
                CompletableFuture.completedFuture(new SubAgentResult(result, result, null, 0, false, Optional.empty())),
                null,
                new ProgressTracker()
        );
    }

    private AgentDefinition definition(String name) {
        return definition(name, false);
    }

    private AgentDefinition definition(String name, boolean background) {
        return new AgentDefinition(
                name,
                "review code",
                List.of(),
                List.of(),
                "inherit",
                OptionalInt.empty(),
                Optional.empty(),
                background,
                "system prompt",
                tempDir.resolve(name + ".md"),
                AgentDefinitionSourceKind.PROJECT
        );
    }

    private AgentDefinitionCatalog catalog(AgentDefinition... definitions) {
        Map<String, AgentDefinition> byName = java.util.Arrays.stream(definitions)
                .collect(java.util.stream.Collectors.toMap(AgentDefinition::agentType, definition -> definition));
        return new AgentDefinitionCatalog() {
            @Override
            public AgentDefinitionCatalogSnapshot snapshot() {
                return new AgentDefinitionCatalogSnapshot(List.copyOf(byName.values()), List.of());
            }

            @Override
            public Optional<AgentDefinition> find(String agentType) {
                return Optional.ofNullable(byName.get(agentType));
            }

            @Override
            public List<AgentDefinitionDiagnostic> diagnostics() {
                return List.of();
            }
        };
    }

    private static final class RecordingBackgroundTaskManager implements BackgroundTaskManager {
        private final String taskId;
        private final AtomicReference<SubAgentLaunchRequest> launchedRequest = new AtomicReference<>();

        private RecordingBackgroundTaskManager(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public String launch(SubAgentLaunchRequest request) {
            launchedRequest.set(request);
            return taskId;
        }

        @Override
        public String adoptRunning(SubAgentRunHandle handle, String task) {
            handle.markAdoptedByBackground(taskId);
            return taskId;
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

    private static final class RecordingForegroundTracker implements ForegroundSubAgentTracker {
        private final AtomicReference<SubAgentRunHandle> current = new AtomicReference<>();
        private final AtomicInteger setCalls = new AtomicInteger();
        private final AtomicInteger adoptCalls = new AtomicInteger();

        @Override
        public void setCurrent(SubAgentRunHandle handle, String task) {
            setCalls.incrementAndGet();
            current.set(handle);
        }

        @Override
        public Optional<SubAgentRunHandle> current() {
            return Optional.ofNullable(current.get());
        }

        @Override
        public Optional<String> adoptCurrentToBackground() {
            adoptCalls.incrementAndGet();
            SubAgentRunHandle handle = current.getAndSet(null);
            if (handle == null) {
                return Optional.empty();
            }
            handle.markAdoptedByBackground("bg-adopted");
            return Optional.of("bg-adopted");
        }

        @Override
        public void clear(SubAgentRunHandle handle) {
            current.compareAndSet(handle, null);
        }
    }
}