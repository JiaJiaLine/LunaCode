package com.lunacode.context;

import com.lunacode.config.ContextConfig;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.MessageRole;

import java.util.ArrayList;
import java.util.List;

public final class PromptTooLongRetryPolicy {
    public List<ConversationMessageSnapshot> dropOldestForRetry(
            List<ConversationMessageSnapshot> messages,
            int promptTooLongFailures,
            ContextConfig config
    ) {
        List<List<ConversationMessageSnapshot>> groups = groupByApiTurn(messages);
        if (groups.size() <= 1) {
            return List.of();
        }
        int groupsToDrop = promptTooLongFailures <= config.promptTooLongGroupRetries()
                ? 1
                : Math.max(1, (int) Math.ceil(groups.size() * config.promptTooLongDropFraction()));
        List<ConversationMessageSnapshot> retained = new ArrayList<>();
        for (int i = Math.min(groupsToDrop, groups.size()); i < groups.size(); i++) {
            retained.addAll(groups.get(i));
        }
        return retained;
    }

    public List<List<ConversationMessageSnapshot>> groupByApiTurn(List<ConversationMessageSnapshot> messages) {
        List<List<ConversationMessageSnapshot>> groups = new ArrayList<>();
        List<ConversationMessageSnapshot> current = new ArrayList<>();
        for (ConversationMessageSnapshot message : messages == null ? List.<ConversationMessageSnapshot>of() : messages) {
            if (message.role() == MessageRole.USER && !current.isEmpty()) {
                groups.add(List.copyOf(current));
                current.clear();
            }
            current.add(message);
        }
        if (!current.isEmpty()) {
            groups.add(List.copyOf(current));
        }
        return groups;
    }
}
