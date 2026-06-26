package com.lunacode.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.provider.ChatProvider;
import com.lunacode.stream.StreamEvent;
import com.lunacode.tool.DefaultToolRegistry;
import com.lunacode.tool.ToolExecutor;
import com.lunacode.tool.ToolRegistry;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ToolUse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class DefaultChatOrchestrator implements ChatOrchestrator {
    private final ConversationManager conversationManager;
    private final ChatProvider provider;
    private final ProviderConfig config;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final Runnable onChange;
    private final ExecutorService executor;
    private final AtomicBoolean responding = new AtomicBoolean(false);
    private final AtomicReference<StatusSnapshot> status;
    private final ObjectMapper mapper = new ObjectMapper();

    public DefaultChatOrchestrator(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig config,
            Runnable onChange
    ) {
        this(conversationManager, provider, config, new DefaultToolRegistry(), null, onChange, Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "lunacode-provider");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public DefaultChatOrchestrator(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig config,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            Runnable onChange
    ) {
        this(conversationManager, provider, config, toolRegistry, toolExecutor, onChange, Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "lunacode-provider");
            thread.setDaemon(true);
            return thread;
        }));
    }

    DefaultChatOrchestrator(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig config,
            Runnable onChange,
            ExecutorService executor
    ) {
        this(conversationManager, provider, config, new DefaultToolRegistry(), null, onChange, executor);
    }

    DefaultChatOrchestrator(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig config,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            Runnable onChange,
            ExecutorService executor
    ) {
        this.conversationManager = Objects.requireNonNull(conversationManager, "conversationManager");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.config = Objects.requireNonNull(config, "config");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.toolExecutor = toolExecutor;
        this.onChange = onChange == null ? () -> {} : onChange;
        this.executor = Objects.requireNonNull(executor, "executor");
        this.status = new AtomicReference<>(StatusSnapshot.idle(config.protocol(), config.model()));
    }

    @Override
    public void submitUserMessage(String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (!responding.compareAndSet(false, true)) {
            status.set(new StatusSnapshot(config.protocol(), config.model(), null, null, "responding", "正在响应中，请稍后再提交"));
            onChange.run();
            return;
        }

        conversationManager.addMessage(MessageRole.USER, content.strip());
        List<ApiMessage> apiMessages = conversationManager.toAPIFormat();
        String assistantId = conversationManager.addStreamingAssistantMessage();
        status.set(new StatusSnapshot(config.protocol(), config.model(), null, null, "responding", null));
        onChange.run();

        ArrayNode enabledTools = toolRegistry.toAPIFormat();
        executor.submit(() -> runConversation(apiMessages, assistantId, enabledTools));
    }

    @Override
    public StatusSnapshot status() {
        return status.get();
    }

    private void runConversation(List<ApiMessage> apiMessages, String assistantId, ArrayNode enabledTools) {
        TokenUsage latestUsage = TokenUsage.unknown();
        try {
            StreamResult first = consumeProviderStream(apiMessages, assistantId, enabledTools, true, latestUsage);
            latestUsage = first.usage();
            if (first.failed()) {
                return;
            }
            if (first.toolUses().isEmpty()) {
                completeIfStreaming(assistantId, latestUsage);
                status.set(new StatusSnapshot(config.protocol(), config.model(), latestUsage.inputTokens(), latestUsage.outputTokens(), "idle", null));
                return;
            }

            completeIfStreaming(assistantId, latestUsage);
            List<ContentBlock.ToolResultBlock> toolResults = executeTools(first.toolUses());
            conversationManager.addUserToolResultMessage(toolResults);
            String finalAssistantId = conversationManager.addStreamingAssistantMessage();
            status.set(new StatusSnapshot(config.protocol(), config.model(), latestUsage.inputTokens(), latestUsage.outputTokens(), "responding", null));
            onChange.run();

            StreamResult second = consumeProviderStream(conversationManager.toAPIFormat(), finalAssistantId, mapper.createArrayNode(), false, latestUsage);
            latestUsage = second.usage();
            if (!second.failed() && !second.toolUses().isEmpty()) {
                conversationManager.appendContent(finalAssistantId, "\n本阶段不支持工具结果回灌后的连环工具调用，我已停止自动执行后续工具。");
            }
            if (!second.failed()) {
                completeIfStreaming(finalAssistantId, latestUsage);
                status.set(new StatusSnapshot(config.protocol(), config.model(), latestUsage.inputTokens(), latestUsage.outputTokens(), "idle", null));
            }
        } catch (Exception e) {
            conversationManager.failMessage(assistantId, "模型响应失败");
            status.set(new StatusSnapshot(config.protocol(), config.model(), latestUsage.inputTokens(), latestUsage.outputTokens(), "error", "模型响应失败"));
        } finally {
            responding.set(false);
            onChange.run();
        }
    }

    private StreamResult consumeProviderStream(List<ApiMessage> apiMessages, String assistantId, ArrayNode tools, boolean collectTools, TokenUsage initialUsage) {
        TokenUsage latestUsage = initialUsage;
        List<ToolUse> toolUses = new ArrayList<>();
        boolean failed = false;
        try (Stream<StreamEvent> events = provider.streamChat(apiMessages, config, tools)) {
            for (StreamEvent event : (Iterable<StreamEvent>) events::iterator) {
                if (event instanceof StreamEvent.MessageStart messageStart) {
                    latestUsage = latestUsage.merge(messageStart.usage());
                    status.set(new StatusSnapshot(config.protocol(), config.model(), latestUsage.inputTokens(), latestUsage.outputTokens(), "responding", null));
                } else if (event instanceof StreamEvent.ContentDelta contentDelta) {
                    conversationManager.appendContent(assistantId, contentDelta.text());
                } else if (event instanceof StreamEvent.ToolUse toolUseEvent) {
                    ToolUse toolUse = new ToolUse(toolUseEvent.id(), toolUseEvent.name(), toolUseEvent.input());
                    if (collectTools) {
                        toolUses.add(toolUse);
                        conversationManager.appendToolUse(assistantId, new ContentBlock.ToolUseBlock(toolUse.id(), toolUse.name(), toolUse.input()));
                    } else {
                        toolUses.add(toolUse);
                    }
                } else if (event instanceof StreamEvent.MessageDelta messageDelta) {
                    latestUsage = latestUsage.merge(messageDelta.usage());
                    status.set(new StatusSnapshot(config.protocol(), config.model(), latestUsage.inputTokens(), latestUsage.outputTokens(), "responding", null));
                } else if (event instanceof StreamEvent.MessageStop messageStop) {
                    latestUsage = latestUsage.merge(messageStop.usage());
                } else if (event instanceof StreamEvent.Error error) {
                    conversationManager.failMessage(assistantId, error.summary());
                    status.set(new StatusSnapshot(config.protocol(), config.model(), latestUsage.inputTokens(), latestUsage.outputTokens(), "error", error.summary()));
                    failed = true;
                    break;
                }
                onChange.run();
            }
        } catch (Exception e) {
            conversationManager.failMessage(assistantId, "模型响应失败");
            status.set(new StatusSnapshot(config.protocol(), config.model(), latestUsage.inputTokens(), latestUsage.outputTokens(), "error", "模型响应失败"));
            failed = true;
        }
        return new StreamResult(latestUsage, toolUses, failed);
    }

    private List<ContentBlock.ToolResultBlock> executeTools(List<ToolUse> toolUses) {
        List<ContentBlock.ToolResultBlock> results = new ArrayList<>();
        for (ToolUse toolUse : toolUses) {
            status.set(new StatusSnapshot(config.protocol(), config.model(), null, null, "tool_running", null, toolUse.name(), null));
            onChange.run();
            ToolResult result = toolExecutor == null
                    ? ToolResult.error("工具执行器未配置", java.util.Map.of("errorType", "executor_not_configured"))
                    : toolExecutor.execute(toolUse);
            String state = result.isError() ? "tool_error" : "tool_done";
            status.set(new StatusSnapshot(config.protocol(), config.model(), null, null, state, result.isError() ? result.content() : null, toolUse.name(), result.metadata().toString()));
            onChange.run();
            results.add(new ContentBlock.ToolResultBlock(toolUse.id(), result.content(), result.isError()));
        }
        return List.copyOf(results);
    }

    private void completeIfStreaming(String assistantId, TokenUsage usage) {
        boolean streaming = conversationManager.snapshot().stream()
                .anyMatch(message -> message.id().equals(assistantId) && message.status() == MessageStatus.STREAMING);
        if (streaming) {
            conversationManager.completeMessage(assistantId, usage);
        }
    }

    private record StreamResult(TokenUsage usage, List<ToolUse> toolUses, boolean failed) {}
}
