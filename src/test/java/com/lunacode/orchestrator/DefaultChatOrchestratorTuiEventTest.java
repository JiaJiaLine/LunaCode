package com.lunacode.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.agent.event.AgentEvent;
import com.lunacode.background.BackgroundTaskListener;
import com.lunacode.background.BackgroundTaskManager;
import com.lunacode.background.BackgroundTaskSnapshot;
import com.lunacode.background.BackgroundTaskStatus;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.permission.PermissionMode;
import com.lunacode.provider.ChatProvider;
import com.lunacode.runtime.AgentMode;
import com.lunacode.stream.StreamEvent;
import com.lunacode.subagent.SubAgentLaunchRequest;
import com.lunacode.subagent.SubAgentRunHandle;
import com.lunacode.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultChatOrchestratorTuiEventTest {
    @Test
    void observersRunAfterStatusUpdateAndBeforeOnChange() {
        List<String> calls = new ArrayList<>();
        AtomicReference<DefaultChatOrchestrator> reference = new AtomicReference<>();
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(
                new DefaultConversationManager(),
                emptyProvider(),
                config(),
                () -> calls.add("change:" + reference.get().status().state())
        );
        reference.set(orchestrator);
        orchestrator.observeAgentEvents(event -> calls.add("observer:" + orchestrator.status().state()));

        orchestrator.emit(new AgentEvent.ToolUseStarted(
                "tool-1",
                "ReadFile",
                new ObjectMapper().createObjectNode().put("path", "README.md")
        ));

        assertEquals(List.of("observer:tool_running", "change:tool_running"), calls);
    }

    @Test
    void forwardsOriginalToolAndCompactionEventsWithoutLosingDetails() {
        DefaultChatOrchestrator orchestrator = orchestrator();
        List<AgentEvent> observed = new ArrayList<>();
        orchestrator.observeAgentEvents(observed::add);
        AgentEvent.ToolUseStarted started = new AgentEvent.ToolUseStarted(
                "tool-42",
                "Bash",
                new ObjectMapper().createObjectNode().put("command", "mvn test")
        );
        AgentEvent.ToolResultReady completed = new AgentEvent.ToolResultReady(
                "tool-42",
                "Bash",
                ToolResult.success("ok", Map.of()),
                Duration.ofMillis(375)
        );
        AgentEvent.CompactionStarted compaction = new AgentEvent.CompactionStarted(
                com.lunacode.context.CompactTrigger.AUTO_CHECK,
                12_000
        );

        orchestrator.emit(started);
        orchestrator.emit(completed);
        orchestrator.emit(compaction);

        assertEquals(List.of(started, completed, compaction), observed);
        assertSame(completed, observed.get(1));
        assertEquals("tool-42", ((AgentEvent.ToolResultReady) observed.get(1)).requestId());
        assertEquals(Duration.ofMillis(375), ((AgentEvent.ToolResultReady) observed.get(1)).duration());
    }

    @Test
    void closingSubscriptionStopsOnlyThatObserver() throws Exception {
        DefaultChatOrchestrator orchestrator = orchestrator();
        List<AgentEvent> first = new ArrayList<>();
        List<AgentEvent> second = new ArrayList<>();
        AutoCloseable firstSubscription = orchestrator.observeAgentEvents(first::add);
        orchestrator.observeAgentEvents(second::add);

        AgentEvent.StreamText beforeClose = new AgentEvent.StreamText("before");
        orchestrator.emit(beforeClose);
        firstSubscription.close();
        firstSubscription.close();
        AgentEvent.StreamText afterClose = new AgentEvent.StreamText("after");
        orchestrator.emit(afterClose);

        assertEquals(List.of(beforeClose), first);
        assertEquals(List.of(beforeClose, afterClose), second);
    }

    @Test
    void faultyObserverDoesNotBreakStatusOtherObserversOrOnChange() {
        AtomicInteger changes = new AtomicInteger();
        DefaultChatOrchestrator orchestrator = new DefaultChatOrchestrator(
                new DefaultConversationManager(),
                emptyProvider(),
                config(),
                changes::incrementAndGet
        );
        List<AgentEvent> healthyEvents = new ArrayList<>();
        orchestrator.observeAgentEvents(event -> {
            throw new IllegalStateException("模拟 UI 观察者故障");
        });
        orchestrator.observeAgentEvents(healthyEvents::add);
        AgentEvent.PermissionRequested event = new AgentEvent.PermissionRequested(
                "tool-1",
                "WriteFile",
                "确认写入 README.md"
        );

        assertDoesNotThrow(() -> orchestrator.emit(event));

        assertEquals("waiting_permission", orchestrator.status().state());
        assertEquals(List.of(event), healthyEvents);
        assertEquals(1, changes.get());
    }

    @Test
    void chatOrchestratorDefaultObservationIsSafeNoOp() throws Exception {
        ChatOrchestrator orchestrator = new ChatOrchestrator() {
            @Override
            public void submitUserMessage(String content) {
            }

            @Override
            public void cancelCurrentRun() {
            }

            @Override
            public StatusSnapshot status() {
                return StatusSnapshot.idle("test", "model");
            }
        };

        AutoCloseable subscription = orchestrator.observeAgentEvents(event -> {
            throw new AssertionError("默认实现不应保存观察者");
        });

        assertDoesNotThrow(subscription::close);
        assertDoesNotThrow(subscription::close);
        assertDoesNotThrow(() -> orchestrator.observeAgentEvents(null).close());
    }

    @Test
    void statusListsOnlyRunningBackgroundActivitiesWithStableSummaries() {
        DefaultChatOrchestrator orchestrator = orchestrator();
        MutableBackgroundTaskManager manager = new MutableBackgroundTaskManager();
        Instant early = Instant.parse("2026-07-10T01:00:00Z");
        Instant late = Instant.parse("2026-07-10T02:00:00Z");
        manager.snapshots = List.of(
                task("bg-late", "修复测试", BackgroundTaskStatus.RUNNING, late, ""),
                task("bg-done", "已经完成", BackgroundTaskStatus.COMPLETED, early.minusSeconds(60), "完成"),
                task("bg-early", "读取项目", BackgroundTaskStatus.RUNNING, early, " 正在读取 README.md "),
                task("bg-fallback", "", BackgroundTaskStatus.RUNNING, late, "")
        );
        orchestrator.configureBackgroundTasks(manager, null, null);

        List<BackgroundActivitySnapshot> activities = orchestrator.status().backgroundActivities();

        assertEquals(List.of("bg-early", "bg-late", "bg-fallback"), activities.stream().map(BackgroundActivitySnapshot::id).toList());
        assertEquals("正在读取 README.md", activities.get(0).summary());
        assertEquals("修复测试", activities.get(1).summary());
        assertEquals("后台任务运行中", activities.get(2).summary());
        assertEquals(early, activities.get(0).startedAt());
        assertThrows(UnsupportedOperationException.class, () -> activities.add(new BackgroundActivitySnapshot("x", "x", early)));
    }

    @Test
    void backgroundActivitiesHandleMissingManagerEmptyTasksAndCompletion() {
        DefaultChatOrchestrator orchestrator = orchestrator();
        assertTrue(orchestrator.status().backgroundActivities().isEmpty());

        MutableBackgroundTaskManager manager = new MutableBackgroundTaskManager();
        orchestrator.configureBackgroundTasks(manager, null, null);
        assertTrue(orchestrator.status().backgroundActivities().isEmpty());

        Instant startedAt = Instant.parse("2026-07-10T03:00:00Z");
        manager.snapshots = List.of(task("bg-1", "后台检查", BackgroundTaskStatus.RUNNING, startedAt, ""));
        assertEquals(List.of("bg-1"), orchestrator.status().backgroundActivities().stream().map(BackgroundActivitySnapshot::id).toList());

        manager.snapshots = List.of(task("bg-1", "后台检查", BackgroundTaskStatus.COMPLETED, startedAt, "done"));
        assertTrue(orchestrator.status().backgroundActivities().isEmpty());
    }

    @Test
    void statusSnapshotNormalizesAndPreservesBackgroundActivitiesAcrossWithMethods() {
        BackgroundActivitySnapshot normalized = new BackgroundActivitySnapshot(null, null, null);
        assertEquals("", normalized.id());
        assertEquals("", normalized.summary());
        assertEquals(Instant.EPOCH, normalized.startedAt());

        BackgroundActivitySnapshot activity = new BackgroundActivitySnapshot("bg-1", "运行中", Instant.EPOCH);
        StatusSnapshot status = new StatusSnapshot(
                "openai",
                "model",
                1,
                2,
                "idle",
                null,
                null,
                null,
                AgentMode.DEFAULT,
                PermissionMode.DEFAULT,
                "session",
                true,
                "ready",
                List.of(activity)
        );

        assertEquals(List.of(activity), status.withAgentMode(AgentMode.PLAN).backgroundActivities());
        assertEquals(List.of(activity), status.withPermissionMode(PermissionMode.PLAN).backgroundActivities());
        assertEquals(List.of(activity), status.withSessionAndMemory("next", false, "disabled").backgroundActivities());
        StatusSnapshot legacyConstructor = new StatusSnapshot(
                "openai", "model", null, null, "idle", null, null, null,
                AgentMode.DEFAULT, PermissionMode.DEFAULT, null, null, null
        );
        assertTrue(legacyConstructor.backgroundActivities().isEmpty());
        assertTrue(new StatusSnapshot(
                "openai", "model", null, null, "idle", null, null, null,
                AgentMode.DEFAULT, PermissionMode.DEFAULT, null, null, null, null
        ).backgroundActivities().isEmpty());
        assertFalse(status.backgroundActivities().isEmpty());
    }

    private DefaultChatOrchestrator orchestrator() {
        return new DefaultChatOrchestrator(new DefaultConversationManager(), emptyProvider(), config(), () -> {
        });
    }

    private static ChatProvider emptyProvider() {
        return new ChatProvider() {
            @Override
            public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config) {
                return Stream.empty();
            }
        };
    }

    private static ProviderConfig config() {
        return new ProviderConfig(
                "openai",
                "gpt-test",
                URI.create("https://api.openai.com"),
                "secret",
                ThinkingConfig.disabled()
        );
    }

    private static BackgroundTaskSnapshot task(
            String id,
            String task,
            BackgroundTaskStatus status,
            Instant startedAt,
            String recentActivity
    ) {
        return new BackgroundTaskSnapshot(
                id,
                task,
                status,
                "",
                "",
                startedAt,
                status == BackgroundTaskStatus.RUNNING ? null : startedAt.plusSeconds(1),
                0,
                TokenUsage.unknown(),
                recentActivity,
                startedAt
        );
    }

    private static final class MutableBackgroundTaskManager implements BackgroundTaskManager {
        private volatile List<BackgroundTaskSnapshot> snapshots = List.of();

        @Override
        public String launch(SubAgentLaunchRequest request) {
            throw new UnsupportedOperationException("测试 manager 不启动任务");
        }

        @Override
        public String adoptRunning(SubAgentRunHandle handle, String task) {
            throw new UnsupportedOperationException("测试 manager 不接管任务");
        }

        @Override
        public Optional<BackgroundTaskSnapshot> get(String taskId) {
            return snapshots.stream().filter(snapshot -> snapshot.id().equals(taskId)).findFirst();
        }

        @Override
        public List<BackgroundTaskSnapshot> list() {
            return snapshots;
        }

        @Override
        public void addListener(BackgroundTaskListener listener) {
        }
    }
}
