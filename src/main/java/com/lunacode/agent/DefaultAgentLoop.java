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
import com.lunacode.context.ExternalizedToolResultRef;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationCompactionAccess;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.hook.HookContext;
import com.lunacode.hook.HookEventName;
import com.lunacode.hook.HookExecutionScope;
import com.lunacode.hook.HookRuntime;
import com.lunacode.hook.NoOpHookRuntime;
import com.lunacode.prompt.PromptBundle;
import com.lunacode.prompt.PromptContextBuilder;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.runtime.CancellationToken;
import com.lunacode.skill.DefaultSkillInvocationPlanner;
import com.lunacode.subagent.SubAgentParentContext;
import com.lunacode.tool.ToolDeclarationSet;
import com.lunacode.tool.ToolExecutionRecord;
import com.lunacode.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class DefaultAgentLoop implements AgentLoop {
    private final ConversationManager conversationManager;
    private final ProviderConfig providerConfig;
    private final ToolRegistry toolRegistry;
    private final AgentToolRunner toolRunner;
    private final AgentTurnRunner turnRunner;
    private final LoopDecisionMaker decisionMaker;
    private final PromptContextBuilder promptContextBuilder;
    private final ContextManager contextManager;
    private final HookRuntime hookRuntime;
    private final Supplier<String> sessionIdSupplier;

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
        this(conversationManager, providerConfig, toolRegistry, toolRunner, turnRunner, decisionMaker, promptContextBuilder, contextManager, NoOpHookRuntime.instance(), () -> "");
    }

    public DefaultAgentLoop(
            ConversationManager conversationManager,
            ProviderConfig providerConfig,
            ToolRegistry toolRegistry,
            AgentToolRunner toolRunner,
            AgentTurnRunner turnRunner,
            LoopDecisionMaker decisionMaker,
            PromptContextBuilder promptContextBuilder,
            ContextManager contextManager,
            HookRuntime hookRuntime,
            Supplier<String> sessionIdSupplier
    ) {
        this.conversationManager = Objects.requireNonNull(conversationManager, "conversationManager");
        this.providerConfig = Objects.requireNonNull(providerConfig, "providerConfig");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.toolRunner = Objects.requireNonNull(toolRunner, "toolRunner");
        this.turnRunner = Objects.requireNonNull(turnRunner, "turnRunner");
        this.decisionMaker = Objects.requireNonNull(decisionMaker, "decisionMaker");
        this.promptContextBuilder = Objects.requireNonNull(promptContextBuilder, "promptContextBuilder");
        this.contextManager = contextManager == null ? ContextManager.noop() : contextManager;
        this.hookRuntime = hookRuntime == null ? NoOpHookRuntime.instance() : hookRuntime;
        this.sessionIdSupplier = sessionIdSupplier == null ? () -> "" : sessionIdSupplier;
    }

    @Override
    public void run(AgentRequest request, AgentEventSink sink, CancellationToken cancellationToken) {
        Objects.requireNonNull(request, "request");
        CancellationToken token = cancellationToken == null ? new CancellationToken() : cancellationToken;
        AgentRunConfig config = request.config();
        ProviderConfig effectiveProviderConfig = effectiveProviderConfig(config);
        conversationManager.addMessage(MessageRole.USER, request.userMessage());
        List<LoadedSkillToolResult> loadedSkillToolResults = new ArrayList<>();
        TokenUsage cumulativeUsage = TokenUsage.unknown();
        int consecutiveUnknownTools = 0;
        int turns = 0;

        while (true) {
            if (token.isCancellationRequested()) {
                cleanupLoadedSkillResults(loadedSkillToolResults);
                emitHook(HookEventName.ERROR, new HookContext(HookEventName.ERROR.yamlName(), "", java.util.Map.of(), "", "", "用户已取消当前请求"), scope(config, turns));
                emit(sink, new AgentEvent.ErrorOccurred("用户已取消当前请求", null));
                emit(sink, new AgentEvent.LoopComplete(turns));
                return;
            }

            int turnIndex = turns + 1;
            HookExecutionScope scope = scope(config, turnIndex);
            emitHook(HookEventName.TURN_START, messageContext(HookEventName.TURN_START, request.userMessage(), ""), scope);
            ContextPreparationResult contextResult = contextManager.prepareBeforeTurn(new ContextPreparationRequest(
                    effectiveProviderConfig,
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
                cleanupLoadedSkillResults(loadedSkillToolResults);
                emitHook(HookEventName.ERROR, messageContext(HookEventName.ERROR, contextResult.userVisibleMessage(), contextResult.userVisibleMessage()), scope);
                emit(sink, new AgentEvent.ErrorOccurred(contextResult.userVisibleMessage(), null));
                emit(sink, new AgentEvent.LoopComplete(turns));
                return;
            }

            ToolDeclarationSet declarations = toolRegistry.declarationsForModel(config.mode(), config.toolAccessPolicy());
            emitHook(HookEventName.PRE_SEND, messageContext(HookEventName.PRE_SEND, request.modelMessage(), ""), scope);
            PromptBundle promptBundle = promptContextBuilder.build(
                    config,
                    turnIndex,
                    historyForModel(request, conversationManager.toAPIFormat()),
                    declarations
            );
            AgentTurnInput input = new AgentTurnInput(
                    turnIndex,
                    promptBundle.system().staticPrompt().render(),
                    promptBundle,
                    promptBundle.messages().history(),
                    effectiveProviderConfig,
                    promptBundle.toolDeclarations(),
                    cumulativeUsage,
                    sink
            );
            AgentTurnResult turnResult = turnRunner.runTurn(input);
            turns = turnIndex;
            cumulativeUsage = cumulativeUsage.merge(turnResult.usage());
            contextManager.recordProviderUsage(cumulativeUsage);
            emitHook(HookEventName.POST_RECEIVE, messageContext(HookEventName.POST_RECEIVE, turnResult.fullText(), turnResult.errorSummary()), scope);
            if (turnResult.errorSummary() != null && !turnResult.errorSummary().isBlank()) {
                emitHook(HookEventName.ERROR, messageContext(HookEventName.ERROR, turnResult.fullText(), turnResult.errorSummary()), scope);
            }
            emitHook(HookEventName.TURN_END, messageContext(HookEventName.TURN_END, turnResult.fullText(), turnResult.errorSummary()), scope);

            LoopContext context = new LoopContext(config, token, turnIndex, consecutiveUnknownTools, cumulativeUsage);
            LoopDecision decision = decisionMaker.decide(context, turnResult);
            if (decision instanceof LoopDecision.Complete) {
                cleanupLoadedSkillResults(loadedSkillToolResults);
                emit(sink, new AgentEvent.LoopComplete(turns));
                return;
            }
            if (decision instanceof LoopDecision.StopCancelled) {
                cleanupLoadedSkillResults(loadedSkillToolResults);
                emitHook(HookEventName.ERROR, messageContext(HookEventName.ERROR, "", "用户已取消当前请求"), scope);
                emit(sink, new AgentEvent.ErrorOccurred("用户已取消当前请求", null));
                emit(sink, new AgentEvent.LoopComplete(turns));
                return;
            }
            if (decision instanceof LoopDecision.StopError stopError) {
                cleanupLoadedSkillResults(loadedSkillToolResults);
                emitHook(HookEventName.ERROR, messageContext(HookEventName.ERROR, "", stopError.summary()), scope);
                emit(sink, new AgentEvent.ErrorOccurred(stopError.summary(), null));
                emit(sink, new AgentEvent.LoopComplete(turns));
                return;
            }
            if (decision instanceof LoopDecision.StopWithLimit stopWithLimit) {
                String message = "已达到 Agent Loop 最大迭代次数: " + stopWithLimit.maxIterations();
                cleanupLoadedSkillResults(loadedSkillToolResults);
                emitHook(HookEventName.ERROR, messageContext(HookEventName.ERROR, "", message), scope);
                emit(sink, new AgentEvent.ErrorOccurred(message, null));
                emit(sink, new AgentEvent.LoopComplete(turns));
                return;
            }
            if (decision instanceof LoopDecision.StopUnknownTools stopUnknownTools) {
                String message = "连续未知工具达到阈值，已停止: " + stopUnknownTools.count();
                cleanupLoadedSkillResults(loadedSkillToolResults);
                emitHook(HookEventName.ERROR, messageContext(HookEventName.ERROR, "", message), scope);
                emit(sink, new AgentEvent.ErrorOccurred(message, null));
                emit(sink, new AgentEvent.LoopComplete(turns));
                return;
            }

            if (decision instanceof LoopDecision.ContinueWithTools continueWithTools) {
                List<ToolExecutionRecord> records = toolRunner.executeToolBatches(continueWithTools.toolUses(), config, token, sink, turnIndex, parentContext(config));
                if (records.isEmpty() && token.isCancellationRequested()) {
                    continue;
                }
                contextManager.recordToolExecutions(records);
                String toolResultMessageId = conversationManager.addUserToolResultMessage(records.stream()
                        .map(record -> new ContentBlock.ToolResultBlock(
                                record.toolUse().id(),
                                record.result().content(),
                                record.result().isError()))
                        .toList());
                rememberLoadedSkillResults(toolResultMessageId, records, loadedSkillToolResults);
                consecutiveUnknownTools = updateUnknownToolCount(consecutiveUnknownTools, records);
            }
        }
    }

    private SubAgentParentContext parentContext(AgentRunConfig config) {
        return new SubAgentParentContext(
                conversationManager,
                config,
                config == null ? null : config.toolAccessPolicy(),
                config != null && config.backgroundAgent(),
                config != null && config.forkAgent(),
                safeSessionId(),
                config == null ? java.nio.file.Path.of("") : config.workDir()
        );
    }

    private String safeSessionId() {
        try {
            String sessionId = sessionIdSupplier.get();
            return sessionId == null ? "" : sessionId;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private ProviderConfig effectiveProviderConfig(AgentRunConfig config) {
        if (config == null || config.modelOverride().isEmpty()) {
            return providerConfig;
        }
        return new ProviderConfig(
                providerConfig.protocol(),
                config.modelOverride().orElseThrow(),
                providerConfig.baseUrl(),
                providerConfig.apiKey(),
                providerConfig.thinking(),
                providerConfig.agent(),
                providerConfig.permissions(),
                providerConfig.sandbox(),
                providerConfig.mcp(),
                providerConfig.context(),
                providerConfig.memory()
        );
    }

    private List<ApiMessage> historyForModel(AgentRequest request, List<ApiMessage> history) {
        if (Objects.equals(request.userMessage(), request.modelMessage())) {
            return history;
        }
        List<ApiMessage> copy = new ArrayList<>(history);
        for (int i = copy.size() - 1; i >= 0; i--) {
            ApiMessage message = copy.get(i);
            if (!"user".equals(message.role())) {
                continue;
            }
            if (message.content().size() == 1 && message.content().get(0) instanceof ContentBlock.Text text
                    && Objects.equals(text.text(), request.userMessage())) {
                copy.set(i, new ApiMessage("user", List.of(new ContentBlock.Text(request.modelMessage()))));
                return List.copyOf(copy);
            }
        }
        return history;
    }

    private void rememberLoadedSkillResults(
            String toolResultMessageId,
            List<ToolExecutionRecord> records,
            List<LoadedSkillToolResult> loadedSkillToolResults
    ) {
        for (ToolExecutionRecord record : records) {
            if (!DefaultSkillInvocationPlanner.LOAD_SKILL_TOOL_NAME.equals(record.toolUse().name())) {
                continue;
            }
            if (record.result().isError()) {
                continue;
            }
            Object loaded = record.result().metadata().get("loadedSkill");
            if (!Boolean.TRUE.equals(loaded)) {
                continue;
            }
            String skillName = String.valueOf(record.result().metadata().getOrDefault("skillName", ""));
            loadedSkillToolResults.add(new LoadedSkillToolResult(
                    toolResultMessageId,
                    record.toolUse().id(),
                    skillName,
                    record.result().content().length()
            ));
        }
    }

    private void cleanupLoadedSkillResults(List<LoadedSkillToolResult> loadedSkillToolResults) {
        if (loadedSkillToolResults.isEmpty()) {
            return;
        }
        if (!(conversationManager instanceof ConversationCompactionAccess access)) {
            return;
        }
        for (LoadedSkillToolResult result : loadedSkillToolResults) {
            String replacementText = "[Skill /" + result.skillName() + " loaded for this run; full SOP removed after completion]";
            ContentBlock.ToolResultBlock replacement = new ContentBlock.ToolResultBlock(result.toolUseId(), replacementText, false);
            try {
                access.replaceToolResultContent(
                        result.messageId(),
                        result.toolUseId(),
                        replacement,
                        new ExternalizedToolResultRef(
                                result.messageId(),
                                result.toolUseId(),
                                DefaultSkillInvocationPlanner.LOAD_SKILL_TOOL_NAME,
                                null,
                                result.originalChars(),
                                replacementText.length(),
                                false
                        )
                );
            } catch (RuntimeException ignored) {
                // 清理失败不影响已经完成的用户请求。
            }
        }
        loadedSkillToolResults.clear();
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

    private HookExecutionScope scope(AgentRunConfig config, int turnIndex) {
        String sessionId;
        try {
            sessionId = sessionIdSupplier.get();
        } catch (RuntimeException e) {
            sessionId = "";
        }
        return new HookExecutionScope(sessionId, turnIndex, config.workDir());
    }

    private HookContext messageContext(HookEventName event, String message, String error) {
        return new HookContext(event.yamlName(), "", java.util.Map.of(), "", message, error);
    }

    private void emitHook(HookEventName event, HookContext context, HookExecutionScope scope) {
        hookRuntime.emit(event, context, scope);
    }

    private void emit(AgentEventSink sink, AgentEvent event) {
        if (sink != null) {
            sink.emit(event);
        }
    }

    private record LoadedSkillToolResult(String messageId, String toolUseId, String skillName, int originalChars) {
    }
}
