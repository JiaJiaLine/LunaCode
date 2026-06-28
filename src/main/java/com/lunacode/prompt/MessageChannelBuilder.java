package com.lunacode.prompt;

import com.lunacode.runtime.AgentRunConfig;

import com.lunacode.conversation.ApiMessage;

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
        return new MessageChannel(
                Optional.empty(),
                Optional.empty(),
                reminderBuilder.build(state),
                history
        );
    }
}
