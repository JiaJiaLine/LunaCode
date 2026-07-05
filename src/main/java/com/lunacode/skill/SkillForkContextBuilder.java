package com.lunacode.skill;

import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationCompactionAccess;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.ConversationMessageMetadata;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.InternalMessage;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.conversation.TokenUsage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SkillForkContextBuilder {
    private static final int RECENT_LIMIT = 5;
    private static final int MAX_SUMMARY_CHARS_PER_MESSAGE = 240;

    public List<ConversationMessageSnapshot> build(SkillContextPolicy policy, ConversationManager parentConversation) {
        SkillContextPolicy resolvedPolicy = policy == null ? SkillContextPolicy.FULL : policy;
        if (parentConversation == null || resolvedPolicy == SkillContextPolicy.NONE) {
            return List.of();
        }
        return switch (resolvedPolicy) {
            case NONE -> List.of();
            case RECENT -> recentMessages(parentConversation);
            case FULL -> fullSummary(parentConversation);
        };
    }

    private List<ConversationMessageSnapshot> recentMessages(ConversationManager parentConversation) {
        List<ConversationMessageSnapshot> messages = eligibleSnapshots(parentConversation);
        int from = Math.max(0, messages.size() - RECENT_LIMIT);
        List<ConversationMessageSnapshot> result = new ArrayList<>();
        for (int i = from; i < messages.size(); i++) {
            result.add(copySeed(i - from, messages.get(i)));
        }
        return List.copyOf(result);
    }

    private List<ConversationMessageSnapshot> fullSummary(ConversationManager parentConversation) {
        List<ConversationMessageSnapshot> messages = eligibleSnapshots(parentConversation);
        if (messages.isEmpty()) {
            return List.of();
        }
        StringBuilder summary = new StringBuilder("主对话摘要，供 fork Skill 参考：");
        for (ConversationMessageSnapshot message : messages) {
            String text = compact(message.content(), MAX_SUMMARY_CHARS_PER_MESSAGE);
            if (text.isBlank()) {
                continue;
            }
            summary.append('\n')
                    .append("- ")
                    .append(message.role().name().toLowerCase(java.util.Locale.ROOT))
                    .append(": ")
                    .append(text);
        }
        if (summary.toString().equals("主对话摘要，供 fork Skill 参考：")) {
            return List.of();
        }
        return List.of(new ConversationMessageSnapshot(
                "fork-full-summary",
                MessageRole.USER,
                MessageStatus.COMPLETE,
                Instant.EPOCH,
                TokenUsage.unknown(),
                summary.toString(),
                List.of(new ContentBlock.Text(summary.toString())),
                ConversationMessageMetadata.empty(),
                null
        ));
    }

    private List<ConversationMessageSnapshot> eligibleSnapshots(ConversationManager parentConversation) {
        if (parentConversation instanceof ConversationCompactionAccess access) {
            return access.fullSnapshot().stream()
                    .filter(this::eligible)
                    .toList();
        }
        return parentConversation.snapshot().stream()
                .filter(this::eligible)
                .map(this::fromInternalMessage)
                .toList();
    }

    private boolean eligible(ConversationMessageSnapshot message) {
        if (message.status() != MessageStatus.COMPLETE) {
            return false;
        }
        return message.role() == MessageRole.USER || message.role() == MessageRole.ASSISTANT;
    }

    private boolean eligible(InternalMessage message) {
        if (message.status() != MessageStatus.COMPLETE) {
            return false;
        }
        return message.role() == MessageRole.USER || message.role() == MessageRole.ASSISTANT;
    }

    private ConversationMessageSnapshot fromInternalMessage(InternalMessage message) {
        List<ContentBlock> blocks = message.content() == null || message.content().isBlank()
                ? List.of()
                : List.of(new ContentBlock.Text(message.content()));
        return new ConversationMessageSnapshot(
                message.id(),
                message.role(),
                message.status(),
                message.timestamp(),
                message.usage(),
                message.content(),
                blocks,
                ConversationMessageMetadata.empty(),
                message.errorSummary()
        );
    }

    private ConversationMessageSnapshot copySeed(int index, ConversationMessageSnapshot message) {
        return new ConversationMessageSnapshot(
                "fork-recent-" + index,
                message.role(),
                MessageStatus.COMPLETE,
                message.timestamp(),
                message.usage(),
                message.content(),
                message.blocks(),
                message.metadata(),
                null
        );
    }

    private String compact(String value, int maxChars) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }
}
