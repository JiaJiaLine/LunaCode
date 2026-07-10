package com.lunacode.agent.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.lunacode.agent.event.AgentEvent;
import com.lunacode.agent.event.AgentEventSink;
import com.lunacode.hook.HookContext;
import com.lunacode.hook.HookEventName;
import com.lunacode.hook.HookExecutionScope;
import com.lunacode.hook.HookRejection;
import com.lunacode.hook.HookRuntime;
import com.lunacode.hook.NoOpHookRuntime;
import com.lunacode.interaction.PermissionConfirmationAnswer;
import com.lunacode.interaction.PermissionConfirmationBroker;
import com.lunacode.interaction.PermissionConfirmationRequest;
import com.lunacode.permission.DangerousCommandBlacklist;
import com.lunacode.permission.PermissionDecisionLayer;
import com.lunacode.permission.PermissionEvaluation;
import com.lunacode.permission.PermissionRuleStore;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.runtime.CancellationToken;
import com.lunacode.subagent.AgentExecutionContextHolder;
import com.lunacode.subagent.SubAgentParentContext;
import com.lunacode.team.TeamRuntimeRole;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolBatch;
import com.lunacode.tool.ToolBatchPlanner;
import com.lunacode.tool.ToolExecutionRecord;
import com.lunacode.tool.ToolExecutionScope;
import com.lunacode.tool.ToolExecutionScopeHolder;
import com.lunacode.tool.ToolExecutor;
import com.lunacode.tool.ToolPermissionGateway;
import com.lunacode.tool.ToolRegistry;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ToolUse;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class AgentToolRunner {
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ToolBatchPlanner batchPlanner;
    private final ToolPermissionGateway permissionGateway;
    private final PermissionConfirmationBroker confirmationBroker;
    private final PermissionRuleStore ruleStore;
    private final HookRuntime hookRuntime;
    private final Supplier<String> sessionIdSupplier;
    private final DangerousCommandBlacklist hardBlacklist = new DangerousCommandBlacklist();

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
        this(toolRegistry, toolExecutor, batchPlanner, permissionGateway, confirmationBroker, ruleStore, NoOpHookRuntime.instance(), () -> "");
    }

    public AgentToolRunner(
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ToolBatchPlanner batchPlanner,
            ToolPermissionGateway permissionGateway,
            PermissionConfirmationBroker confirmationBroker,
            PermissionRuleStore ruleStore,
            HookRuntime hookRuntime,
            Supplier<String> sessionIdSupplier
    ) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.toolExecutor = toolExecutor;
        this.batchPlanner = batchPlanner == null ? new ToolBatchPlanner() : batchPlanner;
        this.permissionGateway = permissionGateway;
        this.confirmationBroker = confirmationBroker;
        this.ruleStore = ruleStore;
        this.hookRuntime = hookRuntime == null ? NoOpHookRuntime.instance() : hookRuntime;
        this.sessionIdSupplier = sessionIdSupplier == null ? () -> "" : sessionIdSupplier;
    }

    public List<ToolExecutionRecord> executeToolBatches(
            List<ToolUse> toolUses,
            AgentRunConfig config,
            CancellationToken token,
            AgentEventSink sink
    ) {
        return executeToolBatches(toolUses, config, token, sink, 0);
    }

    public List<ToolExecutionRecord> executeToolBatches(
            List<ToolUse> toolUses,
            AgentRunConfig config,
            CancellationToken token,
            AgentEventSink sink,
            int turnIndex
    ) {
        return executeToolBatches(toolUses, config, token, sink, turnIndex, null);
    }

    public List<ToolExecutionRecord> executeToolBatches(
            List<ToolUse> toolUses,
            AgentRunConfig config,
            CancellationToken token,
            AgentEventSink sink,
            int turnIndex,
            SubAgentParentContext parentContext
    ) {
        List<ToolExecutionRecord> records = new ArrayList<>();
        HookExecutionScope scope = scope(config, turnIndex);
        for (ToolBatch batch : batchPlanner.plan(toolUses, toolRegistry)) {
            if (token.isCancellationRequested()) {
                return records;
            }
            if (batch.parallel()) {
                List<CompletableFuture<IndexedRecord>> futures = new ArrayList<>();
                for (int i = 0; i < batch.toolUses().size(); i++) {
                    int index = i;
                    ToolUse toolUse = batch.toolUses().get(i);
                    futures.add(CompletableFuture.supplyAsync(() -> ToolExecutionScopeHolder.withScope(new ToolExecutionScope(configWorkDir(config)), () -> new IndexedRecord(index, executeOne(toolUse, config, sink, scope, parentContext)))));
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
                    records.add(ToolExecutionScopeHolder.withScope(new ToolExecutionScope(configWorkDir(config)), () -> executeOne(toolUse, config, sink, scope, parentContext)));
                }
            }
        }
        return List.copyOf(records);
    }

    private ToolExecutionRecord executeOne(ToolUse toolUse, AgentRunConfig config, AgentEventSink sink, HookExecutionScope scope, SubAgentParentContext parentContext) {
        if (parentContext != null) {
            return AgentExecutionContextHolder.withContext(parentContext, () -> executeOneInternal(toolUse, config, sink, scope));
        }
        return executeOneInternal(toolUse, config, sink, scope);
    }

    private ToolExecutionRecord executeOneInternal(ToolUse toolUse, AgentRunConfig config, AgentEventSink sink, HookExecutionScope scope) {
        long started = System.nanoTime();
        ToolResult result;
        if (config.toolAccessPolicy() != null && !config.toolAccessPolicy().allows(toolUse.name())) {
            result = ToolResult.error("工具不存在或当前 Skill 不允许使用: " + toolUse.name(), Map.of("errorType", "tool_not_found"));
            Duration duration = Duration.ofNanos(System.nanoTime() - started);
            ToolExecutionRecord record = new ToolExecutionRecord(toolUse, result, duration);
            emitHook(HookEventName.ERROR, hookContext(HookEventName.ERROR, toolUse, result, result.content()), scope);
            emit(sink, new AgentEvent.ToolResultReady(toolUse.id(), toolUse.name(), result, duration));
            return record;
        }
        Tool tool = toolRegistry.get(toolUse.name()).orElse(null);
        if (tool == null) {
            result = ToolResult.error("工具不存在或已禁用: " + toolUse.name(), Map.of("errorType", "tool_not_found"));
        } else {
            if (planApprovalBlocked(config, toolUse, tool)) {
                result = ToolResult.error("Team member must wait for Lead plan approval before using modifying tools", Map.of("errorType", "plan_approval_required"));
            } else {
            Optional<HookRejection> rejection = hookRuntime.runPreToolHooks(hookContext(HookEventName.PRE_TOOL_USE, toolUse, null, ""), scope);
            if (rejection.isPresent()) {
                result = hookRejected(toolUse, rejection.get());
            } else {
                Optional<String> hardBlacklistReason = hardBlacklistReason(toolUse);
                if (hardBlacklistReason.isPresent()) {
                    PermissionEvaluation evaluation = PermissionEvaluation.deny(
                            PermissionDecisionLayer.BLACKLIST,
                            hardBlacklistReason.get(),
                            List.of(),
                            List.of()
                    );
                    result = permissionError("工具调用被拒绝: " + toolUse.name() + "。" + evaluation.reason(), evaluation);
                } else {
                    PermissionEvaluation evaluation = permissionGateway == null
                            ? PermissionEvaluation.allow(PermissionDecisionLayer.MODE_POLICY, "未配置权限网关，默认允许", List.of(), List.of())
                            : permissionGateway.evaluate(toolUse, tool, config);
                    result = switch (evaluation.decision()) {
                        case ALLOW -> executeTool(toolUse);
                        case ASK -> handleAsk(toolUse, config, sink, evaluation, scope);
                        case DENY -> permissionError("工具调用被拒绝: " + toolUse.name() + "。" + evaluation.reason(), evaluation);
                    };
                }
            }
            }
        }
        Duration duration = Duration.ofNanos(System.nanoTime() - started);
        ToolExecutionRecord record = new ToolExecutionRecord(toolUse, result, duration);
        emitPostToolHooks(toolUse, result, scope);
        emit(sink, new AgentEvent.ToolResultReady(toolUse.id(), toolUse.name(), result, duration));
        return record;
    }


    private boolean planApprovalBlocked(AgentRunConfig config, ToolUse toolUse, Tool tool) {
        if (config == null || config.teamRuntimeContext().role() != TeamRuntimeRole.MEMBER) {
            return false;
        }
        if (!config.teamRuntimeContext().planModeRequired() || config.teamRuntimeContext().planApproved()) {
            return false;
        }
        String name = toolUse == null ? "" : toolUse.name();
        if (Set.of("TaskCreate", "TaskGet", "TaskList", "TaskUpdate", "SendMessage", "ReadFile", "Glob", "Grep").contains(name)) {
            return false;
        }
        return tool != null && !tool.isReadOnly();
    }
    private ToolResult hookRejected(ToolUse toolUse, HookRejection rejection) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("errorType", "hook_rejected");
        metadata.put("hookId", rejection.hookId());
        metadata.put("toolName", toolUse.name());
        return ToolResult.error(rejection.reason(), metadata);
    }

    private void emitPostToolHooks(ToolUse toolUse, ToolResult result, HookExecutionScope scope) {
        emitHook(HookEventName.POST_TOOL_USE, hookContext(HookEventName.POST_TOOL_USE, toolUse, result, result == null ? "" : result.content()), scope);
        if (result != null && result.isError()) {
            emitHook(HookEventName.ERROR, hookContext(HookEventName.ERROR, toolUse, result, result.content()), scope);
        }
        if (isFileChange(toolUse, result)) {
            emitHook(HookEventName.FILE_CHANGE, hookContext(HookEventName.FILE_CHANGE, toolUse, result, result.content()), scope);
        }
    }

    private boolean isFileChange(ToolUse toolUse, ToolResult result) {
        if (toolUse == null || result == null || result.isError()) {
            return false;
        }
        return "WriteFile".equals(toolUse.name()) || "EditFile".equals(toolUse.name());
    }

    private ToolResult handleAsk(ToolUse toolUse, AgentRunConfig config, AgentEventSink sink, PermissionEvaluation evaluation, HookExecutionScope scope) {
        PermissionConfirmationAnswer answer = confirm(toolUse, config, sink, evaluation, scope);
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

    private Optional<String> hardBlacklistReason(ToolUse toolUse) {
        if (toolUse == null || !"Bash".equals(toolUse.name()) || toolUse.input() == null) {
            return Optional.empty();
        }
        String command = toolUse.input().path("command").asText("");
        return hardBlacklist.firstMatch(command);
    }

    private ToolResult executeTool(ToolUse toolUse) {
        return toolExecutor == null
                ? ToolResult.error("工具执行器未配置", Map.of("errorType", "executor_not_configured"))
                : toolExecutor.execute(toolUse);
    }

    private PermissionConfirmationAnswer confirm(ToolUse toolUse, AgentRunConfig config, AgentEventSink sink, PermissionEvaluation evaluation, HookExecutionScope scope) {
        if (confirmationBroker == null) {
            return PermissionConfirmationAnswer.DENY;
        }
        String prompt = permissionPrompt(toolUse, evaluation);
        emitHook(HookEventName.PERMISSION_REQUEST, hookContext(HookEventName.PERMISSION_REQUEST, toolUse, null, prompt), scope);
        emit(sink, new AgentEvent.PermissionRequested(toolUse.id(), toolUse.name(), prompt));
        try {
            PermissionConfirmationAnswer answer = confirmationBroker.confirm(new PermissionConfirmationRequest(
                    toolUse.id(),
                    toolUse.name(),
                    prompt,
                    config.permissionMode(),
                    targetSummary(toolUse),
                    evaluation.reason(),
                    evaluation.suggestedAllowRule()
            ));
            if (answer == PermissionConfirmationAnswer.DENY) {
                emit(sink, new AgentEvent.PermissionDenied(
                        toolUse.id(),
                        toolUse.name(),
                        "用户拒绝了本次工具调用"
                ));
            } else {
                emit(sink, new AgentEvent.PermissionAllowed(
                        toolUse.id(),
                        toolUse.name(),
                        answer == PermissionConfirmationAnswer.ALLOW_ALWAYS ? "始终允许" : "允许本次"
                ));
            }
            return answer;
        } catch (RuntimeException e) {
            emit(sink, new AgentEvent.PermissionDenied(
                    toolUse.id(),
                    toolUse.name(),
                    "权限确认失败"
            ));
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

    private HookContext hookContext(HookEventName event, ToolUse toolUse, ToolResult result, String message) {
        String toolName = toolUse == null ? "" : toolUse.name();
        JsonNode input = toolUse == null ? null : toolUse.input();
        String filePath = firstText(input, "path", "file_path");
        if ((filePath == null || filePath.isBlank()) && result != null && result.metadata().containsKey("path")) {
            filePath = String.valueOf(result.metadata().get("path"));
        }
        String error = result != null && result.isError() ? result.content() : "";
        return new HookContext(event.yamlName(), toolName, args(input), filePath, message, error);
    }

    private Map<String, String> args(JsonNode input) {
        if (input == null || !input.isObject()) {
            return Map.of();
        }
        Map<String, String> args = new LinkedHashMap<>();
        input.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            args.put(entry.getKey(), value == null || value.isNull() ? "" : value.isValueNode() ? value.asText() : value.toString());
        });
        return args;
    }

    private String firstText(JsonNode input, String... names) {
        if (input == null) {
            return "";
        }
        for (String name : names) {
            if (input.hasNonNull(name) && !input.path(name).asText().isBlank()) {
                return input.path(name).asText();
            }
        }
        return "";
    }

    private HookExecutionScope scope(AgentRunConfig config, int turnIndex) {
        Path workDir = config == null ? Path.of(".") : config.workDir();
        String sessionId;
        try {
            sessionId = sessionIdSupplier.get();
        } catch (RuntimeException e) {
            sessionId = "";
        }
        return new HookExecutionScope(sessionId, turnIndex, workDir);
    }

    private Path configWorkDir(AgentRunConfig config) {
        return config == null ? Path.of(".") : config.workDir();
    }
    private void emitHook(HookEventName event, HookContext context, HookExecutionScope scope) {
        hookRuntime.emit(event, context, scope);
    }

    private void emit(AgentEventSink sink, AgentEvent event) {
        if (sink != null) {
            sink.emit(event);
        }
    }

    private record IndexedRecord(int index, ToolExecutionRecord record) {}
}
