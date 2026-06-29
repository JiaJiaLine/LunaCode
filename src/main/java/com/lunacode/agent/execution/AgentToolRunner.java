package com.lunacode.agent.execution;

import com.lunacode.agent.event.AgentEvent;
import com.lunacode.agent.event.AgentEventSink;
import com.lunacode.interaction.PermissionConfirmationAnswer;
import com.lunacode.interaction.PermissionConfirmationBroker;
import com.lunacode.interaction.PermissionConfirmationRequest;
import com.lunacode.permission.PermissionEvaluation;
import com.lunacode.permission.PermissionRuleStore;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.runtime.CancellationToken;
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
    private final PermissionConfirmationBroker confirmationBroker;
    private final PermissionRuleStore ruleStore;

    public AgentToolRunner(
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ToolBatchPlanner batchPlanner,
            ToolPermissionGateway permissionGateway
    ) {
        this(toolRegistry, toolExecutor, batchPlanner, permissionGateway, null, null);
    }

    public AgentToolRunner(
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ToolBatchPlanner batchPlanner,
            ToolPermissionGateway permissionGateway,
            PermissionConfirmationBroker confirmationBroker
    ) {
        this(toolRegistry, toolExecutor, batchPlanner, permissionGateway, confirmationBroker, null);
    }

    public AgentToolRunner(
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ToolBatchPlanner batchPlanner,
            ToolPermissionGateway permissionGateway,
            PermissionConfirmationBroker confirmationBroker,
            PermissionRuleStore ruleStore
    ) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.toolExecutor = toolExecutor;
        this.batchPlanner = batchPlanner == null ? new ToolBatchPlanner() : batchPlanner;
        this.permissionGateway = permissionGateway;
        this.confirmationBroker = confirmationBroker;
        this.ruleStore = ruleStore;
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
            PermissionEvaluation evaluation = permissionGateway == null
                    ? PermissionEvaluation.allow(com.lunacode.permission.PermissionDecisionLayer.MODE_POLICY, "未配置权限网关，默认允许", List.of(), List.of())
                    : permissionGateway.evaluate(toolUse, tool, config);
            result = switch (evaluation.decision()) {
                case ALLOW -> executeTool(toolUse);
                case ASK -> handleAsk(toolUse, config, sink, evaluation);
                case DENY -> permissionError("工具调用被拒绝: " + toolUse.name() + "。" + evaluation.reason(), evaluation);
            };
        }
        Duration duration = Duration.ofNanos(System.nanoTime() - started);
        ToolExecutionRecord record = new ToolExecutionRecord(toolUse, result, duration);
        emit(sink, new AgentEvent.ToolResultReady(toolUse.id(), toolUse.name(), result, duration));
        return record;
    }

    private ToolResult handleAsk(ToolUse toolUse, AgentRunConfig config, AgentEventSink sink, PermissionEvaluation evaluation) {
        PermissionConfirmationAnswer answer = confirm(toolUse, config, sink, evaluation);
        return switch (answer) {
            case ALLOW_ONCE -> executeTool(toolUse);
            case ALLOW_ALWAYS -> appendRuleThenExecute(toolUse, evaluation);
            case DENY -> permissionError("用户未确认工具调用，已跳过: " + toolUse.name(), evaluation);
        };
    }

    private ToolResult appendRuleThenExecute(ToolUse toolUse, PermissionEvaluation evaluation) {
        if (ruleStore == null || evaluation.suggestedAllowRule() == null) {
            return permissionError("无法写入始终允许规则，已跳过: " + toolUse.name(), evaluation);
        }
        PermissionRuleStore.AppendResult append = ruleStore.appendLocalAllow(evaluation.suggestedAllowRule());
        if (!append.success()) {
            return ToolResult.error("始终允许规则写入失败，工具未执行: " + append.error(), metadata("permission_rule_append_failed", evaluation));
        }
        return executeTool(toolUse);
    }

    private ToolResult executeTool(ToolUse toolUse) {
        return toolExecutor == null
                ? ToolResult.error("工具执行器未配置", Map.of("errorType", "executor_not_configured"))
                : toolExecutor.execute(toolUse);
    }

    private PermissionConfirmationAnswer confirm(ToolUse toolUse, AgentRunConfig config, AgentEventSink sink, PermissionEvaluation evaluation) {
        if (confirmationBroker == null) {
            return PermissionConfirmationAnswer.DENY;
        }
        String prompt = permissionPrompt(toolUse, evaluation);
        emit(sink, new AgentEvent.PermissionRequested(toolUse.id(), toolUse.name(), prompt));
        try {
            return confirmationBroker.confirm(new PermissionConfirmationRequest(
                    toolUse.id(),
                    toolUse.name(),
                    prompt,
                    config.permissionMode(),
                    targetSummary(toolUse),
                    evaluation.reason(),
                    evaluation.suggestedAllowRule()
            ));
        } catch (RuntimeException e) {
            return PermissionConfirmationAnswer.DENY;
        }
    }

    private String permissionPrompt(ToolUse toolUse, PermissionEvaluation evaluation) {
        String summary = targetSummary(toolUse);
        String allowAlways = evaluation.suggestedAllowRule() == null ? "" : "；输入 always/始终允许 将写入本地规则: " + evaluation.suggestedAllowRule();
        return "工具 " + toolUse.name() + " 需要你的确认。原因: " + evaluation.reason()
                + "。参数: " + summary
                + "。输入 yes/y/确认/允许 执行本次，其他输入拒绝" + allowAlways + "。";
    }

    private String targetSummary(ToolUse toolUse) {
        String input = toolUse.input() == null ? "{}" : toolUse.input().toString();
        return input.length() <= 500 ? input : input.substring(0, 500) + "...";
    }

    private ToolResult permissionError(String message, PermissionEvaluation evaluation) {
        return ToolResult.error(message, metadata("permission_denied", evaluation));
    }

    private Map<String, Object> metadata(String errorType, PermissionEvaluation evaluation) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("errorType", errorType);
        metadata.put("permissionDecision", evaluation.decision().name().toLowerCase());
        metadata.put("permissionLayer", evaluation.layer().name().toLowerCase());
        metadata.put("permissionReason", evaluation.reason());
        return metadata;
    }

    private void emit(AgentEventSink sink, AgentEvent event) {
        if (sink != null) {
            sink.emit(event);
        }
    }

    private record IndexedRecord(int index, ToolExecutionRecord record) {}
}
