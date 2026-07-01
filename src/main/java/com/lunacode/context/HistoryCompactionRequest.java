package com.lunacode.context;

import com.lunacode.config.ContextConfig;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.provider.ChatProvider;
import com.lunacode.runtime.AgentRunConfig;

import java.util.List;

public record HistoryCompactionRequest(
        List<ConversationMessageSnapshot> messages,
        ProviderConfig providerConfig,
        AgentRunConfig runConfig,
        ChatProvider provider,
        ContextConfig contextConfig,
        SessionContextStore store,
        RecentFileAccessTracker recentFileAccessTracker,
        UsedSkillRegistry usedSkillRegistry,
        SummaryModelClient summaryModelClient,
        CompactTrigger trigger
) {
    public HistoryCompactionRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
