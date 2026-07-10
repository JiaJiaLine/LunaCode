package com.lunacode.tui;

import com.fasterxml.jackson.databind.JsonNode;
import com.lunacode.agent.event.AgentEvent;
import com.lunacode.orchestrator.BackgroundActivitySnapshot;
import com.lunacode.orchestrator.StatusSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TuiActivityTracker {
    private static final int SUMMARY_LIMIT = 160;

    private final Map<String, TuiActivity> activeTools = new LinkedHashMap<>();
    private final Map<String, TuiActivity> deniedToolsAwaitingResult = new LinkedHashMap<>();
    private final Map<String, TuiActivity> backgroundActivities = new LinkedHashMap<>();
    private final Deque<TuiActivity> completedRecords = new ArrayDeque<>();
    private TuiActivity modelActivity;
    private TuiActivity compactionActivity;
    private boolean modelStreaming;
    private boolean blockedByInteraction;
    private boolean errorAwaitingLoopComplete;
    private String lastState = "";

    public synchronized void onAgentEvent(AgentEvent event, Instant now) {
        if (event == null) {
            return;
        }
        Instant safeNow = safeNow(now);
        if (event instanceof AgentEvent.StreamText) {
            errorAwaitingLoopComplete = false;
            modelStreaming = true;
            if (modelActivity != null) {
                modelActivity = new TuiActivity(
                        modelActivity.id(),
                        modelActivity.kind(),
                        modelActivity.phase(),
                        "Luna 正在回复",
                        modelActivity.detail(),
                        modelActivity.startedAt(),
                        null,
                        ""
                );
            }
            return;
        }
        if (event instanceof AgentEvent.ToolUseStarted started) {
            errorAwaitingLoopComplete = false;
            modelActivity = null;
            modelStreaming = false;
            if (!"AskUserQuestion".equals(started.toolName())) {
                String id = stableId("tool", started.requestId(), started.toolName());
                activeTools.put(id, new TuiActivity(
                        id,
                        ActivityKind.TOOL,
                        ActivityPhase.RUNNING,
                        safeToolName(started.toolName()),
                        toolTarget(started.toolName(), started.input()),
                        safeNow,
                        null,
                        ""
                ));
            }
            return;
        }
        if (event instanceof AgentEvent.PermissionRequested) {
            blockedByInteraction = true;
            return;
        }
        if (event instanceof AgentEvent.PermissionAllowed) {
            blockedByInteraction = false;
            return;
        }
        if (event instanceof AgentEvent.PermissionDenied denied) {
            blockedByInteraction = false;
            String id = stableId("tool", denied.requestId(), denied.toolName());
            TuiActivity deniedTool = activeTools.remove(id);
            if (deniedTool != null) {
                deniedToolsAwaitingResult.put(id, deniedTool);
            }
            return;
        }
        if (event instanceof AgentEvent.ToolResultReady ready) {
            blockedByInteraction = false;
            finishTool(ready, safeNow);
            return;
        }
        if (event instanceof AgentEvent.CompactionStarted started) {
            errorAwaitingLoopComplete = false;
            modelActivity = null;
            modelStreaming = false;
            compactionActivity = new TuiActivity(
                    "compaction",
                    ActivityKind.COMPACTION,
                    ActivityPhase.RUNNING,
                    "正在压缩上下文",
                    "约 " + started.estimatedTokensBefore() + " tokens",
                    safeNow,
                    null,
                    ""
            );
            return;
        }
        if (event instanceof AgentEvent.CompactionCompleted completed) {
            finishCompaction(
                    ActivityPhase.SUCCESS,
                    "已压缩 " + completed.summarizedMessages() + " 条消息",
                    "",
                    safeNow
            );
            return;
        }
        if (event instanceof AgentEvent.CompactionFailed failed) {
            finishCompaction(ActivityPhase.ERROR, "上下文压缩失败", compact(failed.reason()), safeNow);
            return;
        }
        if (event instanceof AgentEvent.LoopComplete) {
            errorAwaitingLoopComplete = false;
            modelActivity = null;
            modelStreaming = false;
            blockedByInteraction = false;
            return;
        }
        if (event instanceof AgentEvent.ErrorOccurred) {
            errorAwaitingLoopComplete = true;
            modelActivity = null;
            compactionActivity = null;
            activeTools.clear();
            deniedToolsAwaitingResult.clear();
            modelStreaming = false;
            blockedByInteraction = true;
        }
    }

    public synchronized void synchronize(StatusSnapshot status, Instant now) {
        if (status == null) {
            return;
        }
        Instant safeNow = safeNow(now);
        String state = normalizeState(status.state());
        synchronizeBackground(status.backgroundActivities(), safeNow);

        if (errorAwaitingLoopComplete) {
            blockedByInteraction = true;
            modelActivity = null;
        }

        boolean responding = "responding".equals(state);
        boolean enteredResponding = responding && !"responding".equals(lastState);
        if (!errorAwaitingLoopComplete && !modelStreaming && (enteredResponding
                || (responding && modelActivity == null && activeTools.isEmpty() && compactionActivity == null))) {
            modelActivity = new TuiActivity(
                    "model:" + safeNow.toEpochMilli(),
                    ActivityKind.MODEL,
                    ActivityPhase.RUNNING,
                    "Luna 正在思考",
                    compact(status.model()),
                    safeNow,
                    null,
                    ""
            );
            modelStreaming = false;
        }
        if ("tool_running".equals(state) && activeTools.isEmpty()
                && status.toolName() != null && !status.toolName().isBlank()) {
            String id = "tool:status";
            activeTools.put(id, new TuiActivity(
                    id,
                    ActivityKind.TOOL,
                    ActivityPhase.RUNNING,
                    safeToolName(status.toolName()),
                    compact(status.toolSummary()),
                    safeNow,
                    null,
                    ""
            ));
        }
        if ("compacting".equals(state) && compactionActivity == null) {
            compactionActivity = new TuiActivity(
                    "compaction",
                    ActivityKind.COMPACTION,
                    ActivityPhase.RUNNING,
                    "正在压缩上下文",
                    compact(status.errorSummary()),
                    safeNow,
                    null,
                    ""
            );
        }

        if (isBlockingState(state)) {
            blockedByInteraction = true;
            modelActivity = null;
            modelStreaming = false;
        } else if (!errorAwaitingLoopComplete
                && !"waiting_permission".equals(state)
                && !"waiting_user".equals(state)) {
            blockedByInteraction = false;
        }

        if ("cancelled".equals(state) || "error".equals(state) || "warning".equals(state)) {
            blockedByInteraction = true;
        }

        if (modelActivity != null && modelActivity.detail().isBlank()
                && status.model() != null && !status.model().isBlank()) {
            modelActivity = new TuiActivity(
                    modelActivity.id(),
                    modelActivity.kind(),
                    modelActivity.phase(),
                    modelActivity.title(),
                    compact(status.model()),
                    modelActivity.startedAt(),
                    modelActivity.finalDuration(),
                    modelActivity.errorSummary()
            );
        }

        if (isTerminalState(state)) {
            modelActivity = null;
            compactionActivity = null;
            activeTools.clear();
            deniedToolsAwaitingResult.clear();
            modelStreaming = false;
        } else if (!responding && !"tool_running".equals(state) && !"compacting".equals(state)
                && !"waiting_permission".equals(state)
                && !"tool_done".equals(state)
                && !"tool_error".equals(state)) {
            modelActivity = null;
            modelStreaming = false;
        }
        lastState = state;
    }

    public synchronized Optional<TuiActivity> primaryActivity() {
        if (blockedByInteraction) {
            return Optional.empty();
        }
        if (!activeTools.isEmpty()) {
            if (activeTools.size() == 1) {
                return Optional.of(activeTools.values().iterator().next());
            }
            TuiActivity first = activeTools.values().stream()
                    .min(Comparator.comparing(TuiActivity::startedAt))
                    .orElseThrow();
            return Optional.of(new TuiActivity(
                    "tools:aggregate",
                    ActivityKind.TOOL,
                    ActivityPhase.RUNNING,
                    "正在执行 " + activeTools.size() + " 个工具",
                    first.title() + (first.detail().isBlank() ? "" : " · " + first.detail()),
                    first.startedAt(),
                    null,
                    ""
            ));
        }
        if (compactionActivity != null && compactionActivity.phase() == ActivityPhase.RUNNING) {
            return Optional.of(compactionActivity);
        }
        if (modelActivity != null && !modelStreaming) {
            return Optional.of(modelActivity);
        }
        return backgroundPrimary();
    }

    public synchronized List<TuiActivity> activeTools() {
        return List.copyOf(activeTools.values());
    }

    public synchronized List<TuiActivity> drainCompletedRecords() {
        List<TuiActivity> drained = new ArrayList<>(completedRecords);
        completedRecords.clear();
        return List.copyOf(drained);
    }

    public synchronized boolean hasAnimatedActivity() {
        return primaryActivity().isPresent();
    }

    public synchronized void clear() {
        activeTools.clear();
        deniedToolsAwaitingResult.clear();
        backgroundActivities.clear();
        completedRecords.clear();
        modelActivity = null;
        compactionActivity = null;
        modelStreaming = false;
        blockedByInteraction = false;
        errorAwaitingLoopComplete = false;
        lastState = "";
    }

    private void finishTool(AgentEvent.ToolResultReady ready, Instant now) {
        String id = stableId("tool", ready.requestId(), ready.toolName());
        TuiActivity started = activeTools.remove(id);
        if (started == null) {
            started = deniedToolsAwaitingResult.remove(id);
        }
        if (started == null && activeTools.size() == 1) {
            String fallbackId = activeTools.keySet().iterator().next();
            if ("tool:status".equals(fallbackId)) {
                started = activeTools.remove(fallbackId);
            }
        }
        Duration duration = nonNegative(ready.duration());
        Instant startedAt = started == null ? now.minus(duration) : started.startedAt();
        boolean error = ready.result() != null && ready.result().isError();
        String content = ready.result() == null ? "" : ready.result().content();
        completedRecords.add(new TuiActivity(
                id,
                ActivityKind.TOOL,
                error ? ActivityPhase.ERROR : ActivityPhase.SUCCESS,
                started == null ? safeToolName(ready.toolName()) : started.title(),
                started == null ? "" : started.detail(),
                startedAt,
                duration,
                error ? compact(content) : ""
        ));
        if (activeTools.isEmpty()
                && deniedToolsAwaitingResult.isEmpty()
                && !isTerminalState(lastState)) {
            modelActivity = new TuiActivity(
                    "model:after-tool:" + now.toEpochMilli(),
                    ActivityKind.MODEL,
                    ActivityPhase.RUNNING,
                    "Luna 正在继续处理",
                    "",
                    now,
                    null,
                    ""
            );
            modelStreaming = false;
        }
    }

    private void finishCompaction(ActivityPhase phase, String detail, String error, Instant now) {
        TuiActivity started = compactionActivity;
        Instant startedAt = started == null ? now : started.startedAt();
        Duration duration = now.isBefore(startedAt) ? Duration.ZERO : Duration.between(startedAt, now);
        TuiActivity completed = new TuiActivity(
                "compaction:" + now.toEpochMilli(),
                ActivityKind.COMPACTION,
                phase,
                phase == ActivityPhase.SUCCESS ? "上下文压缩完成" : "上下文压缩失败",
                compact(detail),
                startedAt,
                duration,
                compact(error)
        );
        if (phase == ActivityPhase.SUCCESS) {
            completedRecords.add(completed);
        }
        compactionActivity = null;
    }

    private void synchronizeBackground(List<BackgroundActivitySnapshot> snapshots, Instant now) {
        Map<String, TuiActivity> next = new LinkedHashMap<>();
        if (snapshots != null) {
            snapshots.stream()
                    .filter(snapshot -> snapshot != null && snapshot.id() != null && !snapshot.id().isBlank())
                    .sorted(Comparator.comparing(snapshot -> snapshot.startedAt() == null ? now : snapshot.startedAt()))
                    .forEach(snapshot -> {
                        Instant startedAt = snapshot.startedAt() == null ? now : snapshot.startedAt();
                        next.put(snapshot.id(), new TuiActivity(
                                "background:" + snapshot.id(),
                                ActivityKind.BACKGROUND,
                                ActivityPhase.RUNNING,
                                "后台任务运行中",
                                compact(snapshot.summary()),
                                startedAt,
                                null,
                                ""
                        ));
                    });
        }
        backgroundActivities.clear();
        backgroundActivities.putAll(next);
    }

    private Optional<TuiActivity> backgroundPrimary() {
        if (backgroundActivities.isEmpty()) {
            return Optional.empty();
        }
        TuiActivity first = backgroundActivities.values().stream()
                .min(Comparator.comparing(TuiActivity::startedAt))
                .orElseThrow();
        if (backgroundActivities.size() == 1) {
            return Optional.of(first);
        }
        return Optional.of(new TuiActivity(
                "background:aggregate",
                ActivityKind.BACKGROUND,
                ActivityPhase.RUNNING,
                "后台任务 " + backgroundActivities.size() + " 个",
                first.detail(),
                first.startedAt(),
                null,
                ""
        ));
    }

    private String toolTarget(String toolName, JsonNode input) {
        String name = safeToolName(toolName);
        return switch (name) {
            case "ReadFile", "WriteFile", "EditFile" -> compact(text(input, "path", "file_path"));
            case "Bash" -> compact(text(input, "command"));
            case "Glob" -> compact(text(input, "pattern"));
            case "Grep" -> grepTarget(input);
            case "Agent" -> compact(text(input, "task", "prompt", "name"));
            default -> "请求";
        };
    }

    private String grepTarget(JsonNode input) {
        String pattern = compact(text(input, "pattern"));
        String path = compact(text(input, "path"));
        if (pattern.isBlank()) {
            return path;
        }
        return path.isBlank() ? pattern : pattern + " · " + path;
    }

    private String text(JsonNode input, String... names) {
        if (input == null || names == null) {
            return "";
        }
        for (String name : names) {
            if (input.hasNonNull(name) && !input.path(name).asText().isBlank()) {
                return input.path(name).asText();
            }
        }
        return "";
    }

    private String safeToolName(String value) {
        return value == null || value.isBlank() ? "UnknownTool" : value.strip();
    }

    private String stableId(String prefix, String requestId, String fallback) {
        String id = requestId == null || requestId.isBlank() ? safeToolName(fallback) : requestId.strip();
        return prefix + ":" + id;
    }

    private String compact(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= SUMMARY_LIMIT) {
            return normalized;
        }
        int end = normalized.offsetByCodePoints(0, SUMMARY_LIMIT - 3);
        return normalized.substring(0, end) + "...";
    }

    private String normalizeState(String value) {
        return value == null ? "" : value.strip().toLowerCase();
    }

    private boolean isBlockingState(String state) {
        return "waiting_user".equals(state) || "waiting_permission".equals(state);
    }

    private boolean isTerminalState(String state) {
        return "idle".equals(state)
                || "cancelled".equals(state)
                || "error".equals(state)
                || "warning".equals(state);
    }

    private Instant safeNow(Instant now) {
        return now == null ? Instant.now() : now;
    }

    private Duration nonNegative(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return Duration.ZERO;
        }
        return duration;
    }
}
