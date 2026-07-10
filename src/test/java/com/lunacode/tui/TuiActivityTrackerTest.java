package com.lunacode.tui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.agent.event.AgentEvent;
import com.lunacode.context.CompactTrigger;
import com.lunacode.orchestrator.BackgroundActivitySnapshot;
import com.lunacode.orchestrator.StatusSnapshot;
import com.lunacode.permission.PermissionMode;
import com.lunacode.runtime.AgentMode;
import com.lunacode.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiActivityTrackerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant T0 = Instant.parse("2026-07-10T00:00:00Z");

    @Test
    void modelActivityStartsOnceAndStopsAnimatingAfterFirstText() {
        TuiActivityTracker tracker = new TuiActivityTracker();
        StatusSnapshot responding = new StatusSnapshot("anthropic", "claude", null, null, "responding", null);

        tracker.synchronize(responding, T0);
        TuiActivity first = tracker.primaryActivity().orElseThrow();
        tracker.synchronize(responding, T0.plusSeconds(3));

        assertEquals(T0, tracker.primaryActivity().orElseThrow().startedAt());
        assertEquals(Duration.ofSeconds(3), first.elapsedAt(T0.plusSeconds(3)));

        tracker.onAgentEvent(new AgentEvent.StreamText("hello"), T0.plusSeconds(3));

        assertTrue(tracker.primaryActivity().isEmpty());
        assertFalse(tracker.hasAnimatedActivity());
    }

    @Test
    void modelActivityStopsForBlockingAndTerminalStates() {
        TuiActivityTracker tracker = new TuiActivityTracker();
        tracker.synchronize(new StatusSnapshot("anthropic", "claude", null, null, "responding", null), T0);

        tracker.synchronize(new StatusSnapshot("anthropic", "claude", null, null, "waiting_permission", "confirm"), T0.plusSeconds(1));
        assertTrue(tracker.primaryActivity().isEmpty());

        tracker.synchronize(new StatusSnapshot("anthropic", "claude", null, null, "idle", null), T0.plusSeconds(2));
        assertTrue(tracker.primaryActivity().isEmpty());
    }

    @Test
    void toolLifecycleUsesRequestIdAndKeepsCompactResult() {
        TuiActivityTracker tracker = new TuiActivityTracker();
        ObjectNode input = MAPPER.createObjectNode().put("path", "src/main/java/App.java");

        tracker.onAgentEvent(new AgentEvent.ToolUseStarted("req-1", "ReadFile", input), T0);

        TuiActivity running = tracker.primaryActivity().orElseThrow();
        assertEquals("tool:req-1", running.id());
        assertEquals("ReadFile", running.title());
        assertEquals("src/main/java/App.java", running.detail());

        tracker.onAgentEvent(new AgentEvent.ToolResultReady(
                "req-1",
                "ReadFile",
                ToolResult.success("full file content", Map.of()),
                Duration.ofMillis(125)
        ), T0.plusMillis(125));

        assertTrue(tracker.activeTools().isEmpty());
        TuiActivity completed = tracker.drainCompletedRecords().get(0);
        assertEquals(ActivityPhase.SUCCESS, completed.phase());
        assertEquals(Duration.ofMillis(125), completed.finalDuration());
        assertEquals("", completed.errorSummary());
        assertFalse(completed.detail().contains("full file content"));
    }

    @Test
    void lastToolCompletionStartsNextModelWaitUntilFirstText() {
        TuiActivityTracker tracker = new TuiActivityTracker();
        tracker.onAgentEvent(new AgentEvent.ToolUseStarted(
                "req-next", "ReadFile", MAPPER.createObjectNode().put("path", "pom.xml")
        ), T0);
        tracker.synchronize(new StatusSnapshot(
                "anthropic", "claude", null, null,
                "tool_running", null, "ReadFile", "pom.xml"
        ), T0);

        tracker.onAgentEvent(new AgentEvent.ToolResultReady(
                "req-next", "ReadFile", ToolResult.success("ok", Map.of()), Duration.ofSeconds(1)
        ), T0.plusSeconds(1));
        tracker.synchronize(new StatusSnapshot(
                "anthropic", "claude", null, null,
                "tool_done", null, "ReadFile", "ok"
        ), T0.plusSeconds(1));

        TuiActivity waiting = tracker.primaryActivity().orElseThrow();
        assertEquals(ActivityKind.MODEL, waiting.kind());
        assertEquals("Luna 正在继续处理", waiting.title());

        tracker.onAgentEvent(new AgentEvent.StreamText("继续"), T0.plusSeconds(2));
        assertTrue(tracker.primaryActivity().isEmpty());
    }

    @Test
    void turnCompleteStatusCannotRestartSpinnerAfterTerminalError() {
        TuiActivityTracker tracker = new TuiActivityTracker();
        tracker.synchronize(new StatusSnapshot(
                "anthropic", "claude", null, null, "responding", null
        ), T0);
        tracker.onAgentEvent(new AgentEvent.ErrorOccurred("provider failed", null), T0.plusSeconds(1));
        tracker.synchronize(new StatusSnapshot(
                "anthropic", "claude", null, null, "error", "provider failed"
        ), T0.plusSeconds(1));

        tracker.synchronize(new StatusSnapshot(
                "anthropic", "claude", null, null, "responding", "turn=1"
        ), T0.plusSeconds(2));

        assertTrue(tracker.primaryActivity().isEmpty());
        assertFalse(tracker.hasAnimatedActivity());
    }

    @Test
    void toolFailureTruncatesErrorSummary() {
        TuiActivityTracker tracker = new TuiActivityTracker();
        tracker.onAgentEvent(new AgentEvent.ToolUseStarted("req-2", "Bash", MAPPER.createObjectNode().put("command", "mvn test")), T0);

        tracker.onAgentEvent(new AgentEvent.ToolResultReady(
                "req-2",
                "Bash",
                ToolResult.error("x".repeat(500), Map.of()),
                Duration.ofSeconds(2)
        ), T0.plusSeconds(2));

        TuiActivity completed = tracker.drainCompletedRecords().get(0);
        assertEquals(ActivityPhase.ERROR, completed.phase());
        assertTrue(completed.errorSummary().length() <= 160);
        assertTrue(completed.errorSummary().endsWith("..."));
    }

    @Test
    void toolTargetsUseCompactToolSpecificFields() {
        assertEquals("mvn test", toolDetail(
                "Bash", MAPPER.createObjectNode().put("command", "mvn test")
        ));
        assertEquals("**/*.java", toolDetail(
                "Glob", MAPPER.createObjectNode().put("pattern", "**/*.java")
        ));
        assertEquals("TODO · src", toolDetail(
                "Grep", MAPPER.createObjectNode().put("pattern", "TODO").put("path", "src")
        ));
        assertEquals("分析测试", toolDetail(
                "Agent", MAPPER.createObjectNode().put("task", "分析测试")
        ));
        assertEquals("请求", toolDetail("CustomTool", MAPPER.createObjectNode().put("secret", "hidden")));
    }

    @Test
    void toolParallelCallsCompleteIndependentlyOutOfOrder() {
        TuiActivityTracker tracker = new TuiActivityTracker();
        tracker.onAgentEvent(new AgentEvent.ToolUseStarted("a", "ReadFile", MAPPER.createObjectNode().put("path", "a.txt")), T0);
        tracker.onAgentEvent(new AgentEvent.ToolUseStarted("b", "Grep", MAPPER.createObjectNode().put("pattern", "TODO")), T0.plusMillis(10));

        assertEquals(2, tracker.activeTools().size());
        assertTrue(tracker.primaryActivity().orElseThrow().title().contains("2"));

        tracker.onAgentEvent(new AgentEvent.ToolResultReady("b", "Grep", ToolResult.success("b", Map.of()), Duration.ofMillis(40)), T0.plusMillis(50));
        tracker.onAgentEvent(new AgentEvent.ToolResultReady("a", "ReadFile", ToolResult.success("a", Map.of()), Duration.ofMillis(70)), T0.plusMillis(70));

        List<TuiActivity> completed = tracker.drainCompletedRecords();
        assertEquals(List.of("tool:b", "tool:a"), completed.stream().map(TuiActivity::id).toList());
        assertEquals(0, tracker.drainCompletedRecords().size());
    }

    @Test
    void toolPermissionTemporarilySuppressesAnimationAndCanResume() {
        TuiActivityTracker tracker = new TuiActivityTracker();
        tracker.onAgentEvent(new AgentEvent.ToolUseStarted("req", "WriteFile", MAPPER.createObjectNode().put("path", "a.txt")), T0);
        tracker.onAgentEvent(new AgentEvent.PermissionRequested("req", "WriteFile", "允许写入吗"), T0.plusMillis(1));

        assertTrue(tracker.primaryActivity().isEmpty());

        tracker.onAgentEvent(new AgentEvent.PermissionAllowed("req", "WriteFile", "allowed"), T0.plusMillis(2));
        assertEquals("tool:req", tracker.primaryActivity().orElseThrow().id());
    }

    @Test
    void deniedToolKeepsTargetUntilFinalErrorRecord() {
        TuiActivityTracker tracker = new TuiActivityTracker();
        tracker.onAgentEvent(new AgentEvent.ToolUseStarted(
                "req", "WriteFile", MAPPER.createObjectNode().put("path", "src/App.java")
        ), T0);

        tracker.onAgentEvent(new AgentEvent.PermissionDenied(
                "req", "WriteFile", "用户拒绝"
        ), T0.plusMillis(10));
        assertTrue(tracker.primaryActivity().isEmpty());

        tracker.onAgentEvent(new AgentEvent.ToolResultReady(
                "req",
                "WriteFile",
                ToolResult.error("用户拒绝", Map.of()),
                Duration.ofMillis(20)
        ), T0.plusMillis(20));

        TuiActivity completed = tracker.drainCompletedRecords().get(0);
        assertEquals("src/App.java", completed.detail());
        assertEquals("用户拒绝", completed.errorSummary());
    }

    @Test
    void compactionLifecycleProducesOneFinalRecord() {
        TuiActivityTracker tracker = new TuiActivityTracker();
        tracker.onAgentEvent(new AgentEvent.CompactionStarted(CompactTrigger.MANUAL, 100_000), T0);

        assertEquals(ActivityKind.COMPACTION, tracker.primaryActivity().orElseThrow().kind());

        tracker.onAgentEvent(new AgentEvent.CompactionCompleted(
                CompactTrigger.MANUAL,
                100_000,
                40_000,
                2,
                18,
                3
        ), T0.plusSeconds(4));

        assertTrue(tracker.primaryActivity().isEmpty());
        TuiActivity completed = tracker.drainCompletedRecords().get(0);
        assertEquals(ActivityKind.COMPACTION, completed.kind());
        assertEquals(ActivityPhase.SUCCESS, completed.phase());
        assertEquals(Duration.ofSeconds(4), completed.finalDuration());
    }

    @Test
    void backgroundActivityRunsOnlyWhenNoForegroundActivityHasPriority() {
        TuiActivityTracker tracker = new TuiActivityTracker();
        StatusSnapshot idleWithBackground = statusWithBackground(List.of(
                new BackgroundActivitySnapshot("bg-1", "分析测试", T0),
                new BackgroundActivitySnapshot("bg-2", "检查权限", T0.plusSeconds(1))
        ));

        tracker.synchronize(idleWithBackground, T0.plusSeconds(2));
        TuiActivity background = tracker.primaryActivity().orElseThrow();
        assertEquals(ActivityKind.BACKGROUND, background.kind());
        assertTrue(background.title().contains("2"));

        tracker.onAgentEvent(new AgentEvent.ToolUseStarted("req", "ReadFile", MAPPER.createObjectNode().put("path", "pom.xml")), T0.plusSeconds(3));
        assertEquals(ActivityKind.TOOL, tracker.primaryActivity().orElseThrow().kind());

        tracker.synchronize(statusWithBackground(List.of()), T0.plusSeconds(4));
        tracker.onAgentEvent(new AgentEvent.ToolResultReady("req", "ReadFile", ToolResult.success("ok", Map.of()), Duration.ofSeconds(1)), T0.plusSeconds(4));
        assertTrue(tracker.primaryActivity().isEmpty());
    }

    @Test
    void terminalWarningSuppressesBackgroundSpinner() {
        TuiActivityTracker tracker = new TuiActivityTracker();
        StatusSnapshot warning = new StatusSnapshot(
                "anthropic",
                "claude",
                null,
                null,
                "warning",
                "上下文压缩失败",
                null,
                null,
                AgentMode.DEFAULT,
                PermissionMode.DEFAULT,
                "session",
                null,
                null,
                List.of(new BackgroundActivitySnapshot("bg-1", "后台分析", T0))
        );

        tracker.synchronize(warning, T0.plusSeconds(1));

        assertTrue(tracker.primaryActivity().isEmpty());
        assertFalse(tracker.hasAnimatedActivity());
    }

    @Test
    void clearRemovesAllTransientAndCompletedState() {
        TuiActivityTracker tracker = new TuiActivityTracker();
        tracker.onAgentEvent(new AgentEvent.ToolUseStarted("req", "ReadFile", MAPPER.createObjectNode().put("path", "a")), T0);
        tracker.onAgentEvent(new AgentEvent.ToolResultReady("req", "ReadFile", ToolResult.success("ok", Map.of()), Duration.ZERO), T0);

        tracker.clear();

        assertTrue(tracker.primaryActivity().isEmpty());
        assertTrue(tracker.activeTools().isEmpty());
        assertTrue(tracker.drainCompletedRecords().isEmpty());
    }

    private StatusSnapshot statusWithBackground(List<BackgroundActivitySnapshot> activities) {
        return new StatusSnapshot(
                "anthropic",
                "claude",
                null,
                null,
                "idle",
                null,
                null,
                null,
                AgentMode.DEFAULT,
                PermissionMode.DEFAULT,
                "session",
                null,
                null,
                activities
        );
    }

    private String toolDetail(String name, ObjectNode input) {
        TuiActivityTracker tracker = new TuiActivityTracker();
        tracker.onAgentEvent(new AgentEvent.ToolUseStarted("request", name, input), T0);
        return tracker.primaryActivity().orElseThrow().detail();
    }
}
