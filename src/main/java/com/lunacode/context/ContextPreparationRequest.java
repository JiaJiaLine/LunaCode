package com.lunacode.context;

import com.lunacode.agent.event.AgentEventSink;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.prompt.PromptContextBuilder;
import com.lunacode.provider.ChatProvider;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.tool.ToolRegistry;

public record ContextPreparationRequest(
        ProviderConfig providerConfig,
        AgentRunConfig runConfig,
        int turnIndex,
        ConversationManager conversationManager,
        ToolRegistry toolRegistry,
        ChatProvider provider,
        PromptContextBuilder promptContextBuilder,
        AgentEventSink sink,
        CompactTrigger trigger
) {}
