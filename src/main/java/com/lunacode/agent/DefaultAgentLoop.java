package com.lunacode.agent;

import com.lunacode.agent.event.AgentEvent;
import com.lunacode.agent.event.AgentEventSink;
import com.lunacode.agent.execution.AgentToolRunner;
import com.lunacode.agent.turn.AgentTurnInput;
import com.lunacode.agent.turn.AgentTurnResult;
import com.lunacode.agent.turn.AgentTurnRunner;
import com.lunacode.config.ProviderConfig;
import com.lunacode.context.CompactTrigger;
import com.lunacode.context.ContextManager;
import com.lunacode.context.ContextPreparationRequest;
import com.lunacode.context.ContextPreparationResult;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.prompt.PromptBundle;
import com.lunacode.prompt.PromptContextBuilder;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.runtime.CancellationToken;
import com.lunacode.tool.ToolExecutionRecord;
import com.lunacode.tool.ToolRegistry;

import java.util.List;
import java.util.Objects;

public final class DefaultAgentLoop implements AgentLoop {
    private final ConversationManager conversationManager;
    private final ProviderConfig providerConfig;
    private final ToolRegistry toolRegistry;
    private final AgentToolRunner toolRunner;
    private final AgentTurnRunner turnRunner;
    private final LoopDecisionMaker decisionMaker;
    private final PromptContextBuilder promptContextBuilder;
    private final ContextManager contextManager;

    public DefaultAgentLoop(
            ConversationManager conversationManager,
            ProviderConfig providerConfig,
            ToolRegistry toolRegistry,
            AgentToolRunner toolRunner,
            AgentTurnRunner turnRunner,
            LoopDecisionMaker decisionMaker,
            PromptContextBuilder promptContextBuilder
    ) {
        this(conversationManager, providerConfig, toolRegistry, toolRunner, turnRunner, decisionMaker, promptContextBuilder, ContextManager.noop());
    }

    public DefaultAgentLoop(
            ConversationManager conversationManager,
            ProviderConfig providerConfig,
            ToolRegistry toolRegistry,
            AgentToolRunner toolRunner,
            AgentTurnRunner turnRunner,
            LoopDecisionMaker decisionMaker,
            PromptContextBuilder promptContextBuilder,
            ContextManager contextManager
    ) {
        this.conversationManager = Objects.requireNonNull(conversationManager, "conversationManager");
        this.providerConfig = Objects.requireNonNull(providerConfig, "providerConfig");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.toolRunner = Objects.requireNonNull(toolRunner, "toolRunner");
        this.turnRunner = Objects.requireNonNull(turnRunner, "turnRunner");
        this.decisionMaker = Objects.requireNonNull(decisionMaker, "decisionMaker");
        this.promptContextBuilder = Objects.requireNonNull(promptContextBuilder, "promptContextBuilder");
        this.contextManager = contextManager == null ? ContextManager.noop() : contextManager;
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
            ContextPreparationResult contextResult = contextManager.prepareBeforeTurn(new ContextPreparationRequest(
                    providerConfig,
                    config,
                    turnIndex,
                    conversationManager,
                    toolRegistry,
                    turnRunner.provider(),
                    promptContextBuilder,
                    sink,
                    CompactTrigger.AUTO_CHECK
            ));
            if (!contextResult.proceed()) {
                emit(sink, new AgentEvent.ErrorOccurred(contextResult.userVisibleMessage(), null));
                emit(sink, new AgentEvent.LoopComplete(turns));
                return;
            }

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
            contextManager.recordProviderUsage(cumulativeUsage);

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
                List<ToolExecutionRecord> records = toolRunner.executeToolBatches(continueWithTools.toolUses(), config, token, sink);
                if (records.isEmpty() && token.isCancellationRequested()) {
                    continue;
                }
                contextManager.recordToolExecutions(records);
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
}