package com.lunacode.agent;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lunacode.conversation.ApiMessage;

import java.util.List;

public final class PromptContextBuilder {
    private final StaticSystemPromptBuilder staticPromptBuilder;
    private final EnvironmentContextCollector environmentCollector;
    private final MessageChannelBuilder messageChannelBuilder;

    public PromptContextBuilder() {
        this(new StaticSystemPromptBuilder(), new EnvironmentContextCollector(), new MessageChannelBuilder());
    }

    public PromptContextBuilder(
            StaticSystemPromptBuilder staticPromptBuilder,
            EnvironmentContextCollector environmentCollector,
            MessageChannelBuilder messageChannelBuilder
    ) {
        this.staticPromptBuilder = staticPromptBuilder;
        this.environmentCollector = environmentCollector;
        this.messageChannelBuilder = messageChannelBuilder;
    }

    public PromptBundle build(AgentRunConfig config, int turnIndex, List<ApiMessage> history, ArrayNode tools) {
        StaticSystemPrompt staticPrompt = staticPromptBuilder.build();
        EnvironmentContext environment = environmentCollector.collect(config);
        ModeInjectionState modeState = new ModeInjectionState(config.mode(), turnIndex, config.planFile(), 3);
        MessageChannel messages = messageChannelBuilder.build(config, modeState, history);
        return new PromptBundle(
                new SystemChannel(staticPrompt, environment),
                tools,
                messages,
                PromptCachePolicy.enabled()
        );
    }
}
