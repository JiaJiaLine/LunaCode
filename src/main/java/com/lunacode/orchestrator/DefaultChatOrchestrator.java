package com.lunacode.orchestrator;

import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.provider.ChatProvider;
import com.lunacode.stream.StreamEvent;

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
    private final Runnable onChange;
    private final ExecutorService executor;
    private final AtomicBoolean responding = new AtomicBoolean(false);
    private final AtomicReference<StatusSnapshot> status;

    public DefaultChatOrchestrator(
            ConversationManager conversationManager,
            ChatProvider provider,
            ProviderConfig config,
            Runnable onChange
    ) {
        this(conversationManager, provider, config, onChange, Executors.newSingleThreadExecutor(r -> {
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
        this.conversationManager = Objects.requireNonNull(conversationManager, "conversationManager");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.config = Objects.requireNonNull(config, "config");
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

        executor.submit(() -> consumeProviderStream(apiMessages, assistantId));
    }

    @Override
    public StatusSnapshot status() {
        return status.get();
    }

    private void consumeProviderStream(List<ApiMessage> apiMessages, String assistantId) {
        TokenUsage latestUsage = TokenUsage.unknown();
        boolean failed = false;
        try (Stream<StreamEvent> events = provider.streamChat(apiMessages, config)) {
            for (StreamEvent event : (Iterable<StreamEvent>) events::iterator) {
                latestUsage = handleEvent(assistantId, event, latestUsage);
                onChange.run();
                if (event instanceof StreamEvent.Error) {
                    failed = true;
                    break;
                }
            }
            if (!failed && conversationManager.snapshot().stream().anyMatch(message -> message.id().equals(assistantId) && message.status().name().equals("STREAMING"))) {
                conversationManager.completeMessage(assistantId, latestUsage);
            }
            if (!failed) {
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

    private TokenUsage handleEvent(String assistantId, StreamEvent event, TokenUsage latestUsage) {
        if (event instanceof StreamEvent.MessageStart messageStart) {
            TokenUsage merged = latestUsage.merge(messageStart.usage());
            status.set(new StatusSnapshot(config.protocol(), config.model(), merged.inputTokens(), merged.outputTokens(), "responding", null));
            return merged;
        }
        if (event instanceof StreamEvent.ContentDelta contentDelta) {
            conversationManager.appendContent(assistantId, contentDelta.text());
            return latestUsage;
        }
        if (event instanceof StreamEvent.MessageDelta messageDelta) {
            TokenUsage merged = latestUsage.merge(messageDelta.usage());
            status.set(new StatusSnapshot(config.protocol(), config.model(), merged.inputTokens(), merged.outputTokens(), "responding", null));
            return merged;
        }
        if (event instanceof StreamEvent.MessageStop messageStop) {
            TokenUsage merged = latestUsage.merge(messageStop.usage());
            conversationManager.completeMessage(assistantId, merged);
            status.set(new StatusSnapshot(config.protocol(), config.model(), merged.inputTokens(), merged.outputTokens(), "idle", null));
            return merged;
        }
        if (event instanceof StreamEvent.Error error) {
            conversationManager.failMessage(assistantId, error.summary());
            status.set(new StatusSnapshot(config.protocol(), config.model(), latestUsage.inputTokens(), latestUsage.outputTokens(), "error", error.summary()));
            responding.set(false);
            return latestUsage;
        }
        return latestUsage;
    }
}