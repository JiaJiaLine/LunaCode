package com.lunacode.prompt;

import com.lunacode.runtime.AgentRunConfig;

import com.lunacode.conversation.ApiMessage;
import com.lunacode.tool.DeferredToolSummary;

import java.util.List;
import java.util.Optional;

public final class MessageChannelBuilder {
    private final SystemReminderBuilder reminderBuilder;

    public MessageChannelBuilder() {
        this(new SystemReminderBuilder());
    }

    public MessageChannelBuilder(SystemReminderBuilder reminderBuilder) {
        this.reminderBuilder = reminderBuilder;
    }

    public MessageChannel build(AgentRunConfig config, ModeInjectionState state, List<ApiMessage> history) {
        return build(config, state, history, List.of());
    }

    public MessageChannel build(AgentRunConfig config, ModeInjectionState state, List<ApiMessage> history, List<DeferredToolSummary> deferredTools) {
        return build(config, state, history, deferredTools, Optional.empty(), Optional.empty());
    }

    public MessageChannel build(
            AgentRunConfig config,
            ModeInjectionState state,
            List<ApiMessage> history,
            List<DeferredToolSummary> deferredTools,
            Optional<ProjectInstructionContext> projectInstructions,
            Optional<MemoryContext> memory
    ) {
        return new MessageChannel(
                projectInstructions,
                memory,
                reminderBuilder.build(state, deferredTools),
                history
        );
    }
}