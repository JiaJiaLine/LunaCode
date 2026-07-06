package com.lunacode.prompt;

import com.lunacode.conversation.ApiMessage;
import com.lunacode.hook.HookReminderStore;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.skill.SkillPromptContext;
import com.lunacode.tool.DeferredToolSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class MessageChannelBuilder {
    private final SystemReminderBuilder reminderBuilder;
    private final HookReminderStore hookReminderStore;
    private final Supplier<String> sessionIdSupplier;

    public MessageChannelBuilder() {
        this(new SystemReminderBuilder());
    }

    public MessageChannelBuilder(SystemReminderBuilder reminderBuilder) {
        this(reminderBuilder, null, () -> "");
    }

    public MessageChannelBuilder(SystemReminderBuilder reminderBuilder, HookReminderStore hookReminderStore, Supplier<String> sessionIdSupplier) {
        this.reminderBuilder = reminderBuilder == null ? new SystemReminderBuilder() : reminderBuilder;
        this.hookReminderStore = hookReminderStore;
        this.sessionIdSupplier = sessionIdSupplier == null ? () -> "" : sessionIdSupplier;
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
        return build(config, state, history, deferredTools, projectInstructions, memory, SkillPromptContext.empty());
    }

    public MessageChannel build(
            AgentRunConfig config,
            ModeInjectionState state,
            List<ApiMessage> history,
            List<DeferredToolSummary> deferredTools,
            Optional<ProjectInstructionContext> projectInstructions,
            Optional<MemoryContext> memory,
            SkillPromptContext skillContext
    ) {
        List<SystemReminder> reminders = new ArrayList<>(reminderBuilder.build(state, deferredTools));
        if (hookReminderStore != null && state != null) {
            reminders.addAll(hookReminderStore.drain(sessionIdSupplier.get(), state.turnIndex()));
        }
        return new MessageChannel(
                projectInstructions,
                memory,
                skillContext,
                config == null ? Optional.empty() : config.subAgentSystemPrompt(),
                reminders,
                history
        );
    }
}
