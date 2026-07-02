package com.lunacode.session;

import com.lunacode.context.ExternalizedToolResultRef;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationCompactionAccess;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.InternalMessage;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.conversation.TokenUsage;

import java.util.List;
import java.util.Objects;

public final class SessionBackedConversationManager implements ConversationManager, ConversationCompactionAccess {
    private final ConversationManager delegate;
    private final ConversationCompactionAccess compactionAccess;
    private final SessionService sessionService;
    private boolean suppressAppend;

    public SessionBackedConversationManager(ConversationManager delegate, SessionService sessionService) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (!(delegate instanceof ConversationCompactionAccess access)) {
            throw new IllegalArgumentException("delegate 必须支持 ConversationCompactionAccess");
        }
        this.compactionAccess = access;
        this.sessionService = Objects.requireNonNull(sessionService, "sessionService");
    }

    @Override
    public String addMessage(MessageRole role, String content) {
        String id = delegate.addMessage(role, content);
        appendIfStable(id);
        return id;
    }

    @Override
    public String addAssistantMessage(List<ContentBlock> blocks) {
        String id = delegate.addAssistantMessage(blocks);
        appendIfStable(id);
        return id;
    }

    @Override
    public String addUserToolResultMessage(List<ContentBlock.ToolResultBlock> results) {
        String id = delegate.addUserToolResultMessage(results);
        appendIfStable(id);
        return id;
    }

    @Override
    public String addStreamingAssistantMessage() {
        return delegate.addStreamingAssistantMessage();
    }

    @Override
    public void appendContent(String messageId, String delta) {
        delegate.appendContent(messageId, delta);
    }

    @Override
    public void appendToolUse(String messageId, ContentBlock.ToolUseBlock toolUse) {
        delegate.appendToolUse(messageId, toolUse);
    }

    @Override
    public void completeMessage(String messageId, TokenUsage usage) {
        delegate.completeMessage(messageId, usage);
        appendIfStable(messageId);
    }

    @Override
    public void failMessage(String messageId, String errorSummary) {
        delegate.failMessage(messageId, errorSummary);
    }

    @Override
    public List<InternalMessage> snapshot() {
        return delegate.snapshot();
    }

    @Override
    public List<ApiMessage> toAPIFormat() {
        return delegate.toAPIFormat();
    }

    @Override
    public List<ConversationMessageSnapshot> fullSnapshot() {
        return compactionAccess.fullSnapshot();
    }

    @Override
    public void replaceToolResultContent(String messageId, String toolUseId, ContentBlock.ToolResultBlock replacement, ExternalizedToolResultRef ref) {
        compactionAccess.replaceToolResultContent(messageId, toolUseId, replacement, ref);
    }

    @Override
    public void rewriteForCompaction(List<ConversationMessageSnapshot> rewrittenMessages) {
        compactionAccess.rewriteForCompaction(rewrittenMessages);
    }

    public void restoreHistory(List<ConversationMessageSnapshot> messages) {
        suppressAppend = true;
        try {
            compactionAccess.rewriteForCompaction(messages == null ? List.of() : messages);
        } finally {
            suppressAppend = false;
        }
    }

    private void appendIfStable(String messageId) {
        if (suppressAppend) {
            return;
        }
        ConversationMessageSnapshot snapshot = fullSnapshot().stream()
                .filter(message -> message.id().equals(messageId))
                .findFirst()
                .orElse(null);
        if (snapshot == null || snapshot.status() != MessageStatus.COMPLETE) {
            return;
        }
        if (snapshot.role() == MessageRole.SYSTEM) {
            return;
        }
        sessionService.appendCurrent(snapshot);
    }
}
