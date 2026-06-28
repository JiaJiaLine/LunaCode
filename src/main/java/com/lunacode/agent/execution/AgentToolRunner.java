package com.lunacode.agent.execution;

import com.lunacode.agent.event.AgentEvent;
import com.lunacode.agent.event.AgentEventSink;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.runtime.CancellationToken;
import com.lunacode.tool.PermissionDecision;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolBatch;
import com.lunacode.tool.ToolBatchPlanner;
import com.lunacode.tool.ToolExecutionRecord;
import com.lunacode.tool.ToolExecutor;
import com.lunacode.tool.ToolPermissionGateway;
import com.lunacode.tool.ToolRegistry;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ToolUse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class AgentToolRunner {
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ToolBatchPlanner batchPlanner;
    private final ToolPermissionGateway permissionGateway;

    public AgentToolRunner(
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ToolBatchPlanner batchPlanner,
            ToolPermissionGateway permissionGateway
    ) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.toolExecutor = toolExecutor;
        this.batchPlanner = batchPlanner == null ? new ToolBatchPlanner() : batchPlanner;
        this.permissionGateway = permissionGateway;
    }

    public List<ToolExecutionRecord> executeToolBatches(
            List<ToolUse> toolUses,
            AgentRunConfig config,
            CancellationToken token,
            AgentEventSink sink
    ) {
        List<ToolExecutionRecord> records = new ArrayList<>();
        for (ToolBatch batch : batchPlanner.plan(toolUses, toolRegistry)) {
            if (token.isCancellationRequested()) {
                return records;
            }
            if (batch.parallel()) {
                List<CompletableFuture<IndexedRecord>> futures = new ArrayList<>();
                for (int i = 0; i < batch.toolUses().size(); i++) {
                    int index = i;
                    ToolUse toolUse = batch.toolUses().get(i);
                    futures.add(CompletableFuture.supplyAsync(() -> new IndexedRecord(index, executeOne(toolUse, config, sink))));
                }
                futures.stream()
                        .map(CompletableFuture::join)
                        .sorted(Comparator.comparingInt(IndexedRecord::index))
                        .map(IndexedRecord::record)
                        .forEach(records::add);
            } else {
                for (ToolUse toolUse : batch.toolUses()) {
                    if (token.isCancellationRequested()) {
                        return records;
                    }
                    records.add(executeOne(toolUse, config, sink));
                }
            }
        }
        return List.copyOf(records);
    }

    private ToolExecutionRecord executeOne(ToolUse toolUse, AgentRunConfig config, AgentEventSink sink) {
        long started = System.nanoTime();
        ToolResult result;
        Tool tool = toolRegistry.get(toolUse.name()).orElse(null);
        if (tool == null) {
            result = ToolResult.error("工具不存在或已禁用: " + toolUse.name(), Map.of("errorType", "tool_not_found"));
        } else {
            PermissionDecision decision = permissionGateway == null
                    ? PermissionDecision.ALLOW
                    : permissionGateway.decide(toolUse, tool, config.mode(), config.planFile());
            if (decision == PermissionDecision.ALLOW) {
                result = toolExecutor == null
                        ? ToolResult.error("工具执行器未配置", Map.of("errorType", "executor_not_configured"))
                        : toolExecutor.execute(toolUse);
            } else if (decision == PermissionDecision.ASK) {
                result = ToolResult.error("该工具调用需要用户确认，当前版本不会静默执行: " + toolUse.name(), metadata("permission_required"));
            } else {
                result = ToolResult.error("工具调用被拒绝: " + toolUse.name(), metadata("permission_denied"));
            }
        }
        Duration duration = Duration.ofNanos(System.nanoTime() - started);
        ToolExecutionRecord record = new ToolExecutionRecord(toolUse, result, duration);
        emit(sink, new AgentEvent.ToolResultReady(toolUse.id(), toolUse.name(), result, duration));
        return record;
    }

    private Map<String, Object> metadata(String errorType) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("errorType", errorType);
        return metadata;
    }

    private void emit(AgentEventSink sink, AgentEvent event) {
        if (sink != null) {
            sink.emit(event);
        }
    }

    private record IndexedRecord(int index, ToolExecutionRecord record) {}
}
