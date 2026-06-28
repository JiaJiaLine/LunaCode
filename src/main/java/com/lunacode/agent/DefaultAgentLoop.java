package com.lunacode.agent;

import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.provider.ChatProvider;
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

public final class DefaultAgentLoop implements AgentLoop {
    private final ConversationManager conversationManager;
    private final ProviderConfig providerConfig;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ToolBatchPlanner batchPlanner;
    private final ToolPermissionGateway permissionGateway;
    private final AgentTurnRunner turnRunner;
    private final LoopDecisionMaker decisionMaker;
    private final PromptContextBuilder promptContextBuilder;

    public DefaultAgentLoop(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig providerConfig,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ToolBatchPlanner batchPlanner,
            ToolPermissionGateway permissionGateway
    ) {
        this.conversationManager = Objects.requireNonNull(conversationManager, "conversationManager");
        this.providerConfig = Objects.requireNonNull(providerConfig, "providerConfig");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.toolExecutor = toolExecutor;
        this.batchPlanner = batchPlanner == null ? new ToolBatchPlanner() : batchPlanner;
        this.permissionGateway = permissionGateway;
        this.turnRunner = new AgentTurnRunner(conversationManager, Objects.requireNonNull(provider, "provider"));
        this.decisionMaker = new LoopDecisionMaker();
        this.promptContextBuilder = new PromptContextBuilder();
    }

    @Override
    public void run(AgentRequest request, AgentEventSink sink, CancellationToken cancellationToken) {
        Objects.requireNonNull(request, "request");
        CancellationToken token = cancellationToken == null ? new CancellationToken() : cancellationToken;
        AgentRunConfig config = request.config();
        conversationManager.addMessage(MessageRole.USER, request.userMessage());
        TokenUsage cumulativeUsage = TokenUsage.unknown();
        int consecutiveUnknownTools = 0;
        int turns = 0;

        while (true) {
            if (token.isCancellationRequested()) {
                emit(sink, new AgentEvent.ErrorOccurred("用户已取消当前请求", null));
                emit(sink, new AgentEvent.LoopComplete(turns));
                return;
            }

            int turnIndex = turns + 1;
            PromptBundle promptBundle = promptContextBuilder.build(
                    config,
                    turnIndex,
                    conversationManager.toAPIFormat(),
                    toolRegistry.toAPIFormat(config.mode())
            );
            AgentTurnInput input = new AgentTurnInput(
                    turnIndex,
                    promptBundle.system().staticPrompt().render(),
                    promptBundle,
                    promptBundle.messages().history(),
                    providerConfig,
                    promptBundle.toolDeclarations(),
                    cumulativeUsage,
                    sink
            );
            AgentTurnResult turnResult = turnRunner.runTurn(input);
            turns = turnIndex;
            cumulativeUsage = cumulativeUsage.merge(turnResult.usage());

            LoopContext context = new LoopContext(config, token, turnIndex, consecutiveUnknownTools, cumulativeUsage);
            LoopDecision decision = decisionMaker.decide(context, turnResult);
            if (decision instanceof LoopDecision.Complete) {
                emit(sink, new AgentEvent.LoopComplete(turns));
                return;
            }
            if (decision instanceof LoopDecision.StopCancelled) {
                emit(sink, new AgentEvent.ErrorOccurred("用户已取消当前请求", null));
                emit(sink, new AgentEvent.LoopComplete(turns));
                return;
            }
            if (decision instanceof LoopDecision.StopError stopError) {
                emit(sink, new AgentEvent.ErrorOccurred(stopError.summary(), null));
                emit(sink, new AgentEvent.LoopComplete(turns));
                return;
            }
            if (decision instanceof LoopDecision.StopWithLimit stopWithLimit) {
                emit(sink, new AgentEvent.ErrorOccurred("已达到 Agent Loop 最大迭代次数: " + stopWithLimit.maxIterations(), null));
                emit(sink, new AgentEvent.LoopComplete(turns));
                return;
            }
            if (decision instanceof LoopDecision.StopUnknownTools stopUnknownTools) {
                emit(sink, new AgentEvent.ErrorOccurred("连续未知工具达到阈值，已停止: " + stopUnknownTools.count(), null));
                emit(sink, new AgentEvent.LoopComplete(turns));
                return;
            }

            if (decision instanceof LoopDecision.ContinueWithTools continueWithTools) {
                List<ToolExecutionRecord> records = executeToolBatches(continueWithTools.toolUses(), config, token, sink);
                if (records.isEmpty() && token.isCancellationRequested()) {
                    continue;
                }
                conversationManager.addUserToolResultMessage(records.stream()
                        .map(record -> new ContentBlock.ToolResultBlock(
                                record.toolUse().id(),
                                record.result().content(),
                                record.result().isError()))
                        .toList());
                consecutiveUnknownTools = updateUnknownToolCount(consecutiveUnknownTools, records);
            }
        }
    }

    private List<ToolExecutionRecord> executeToolBatches(List<ToolUse> toolUses, AgentRunConfig config, CancellationToken token, AgentEventSink sink) {
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

    private int updateUnknownToolCount(int currentCount, List<ToolExecutionRecord> records) {
        int count = currentCount;
        for (ToolExecutionRecord record : records) {
            if ("tool_not_found".equals(record.result().metadata().get("errorType"))) {
                count++;
            } else {
                count = 0;
            }
        }
        return count;
    }

    private void emit(AgentEventSink sink, AgentEvent event) {
        if (sink != null) {
            sink.emit(event);
        }
    }

    private record IndexedRecord(int index, ToolExecutionRecord record) {}
}
