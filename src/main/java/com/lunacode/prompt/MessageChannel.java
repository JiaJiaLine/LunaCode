package com.lunacode.prompt;

import com.lunacode.conversation.ApiMessage;
import com.lunacode.skill.SkillPromptContext;

import java.util.List;
import java.util.Optional;

public record MessageChannel(
        Optional<ProjectInstructionContext> projectInstructions,
        Optional<MemoryContext> memory,
        SkillPromptContext skillContext,
        List<SystemReminder> reminders,
        List<ApiMessage> history
) {
    public MessageChannel(
            Optional<ProjectInstructionContext> projectInstructions,
            Optional<MemoryContext> memory,
            List<SystemReminder> reminders,
            List<ApiMessage> history
    ) {
        this(projectInstructions, memory, SkillPromptContext.empty(), reminders, history);
    }

    public MessageChannel {
        projectInstructions = projectInstructions == null ? Optional.empty() : projectInstructions;
        memory = memory == null ? Optional.empty() : memory;
        skillContext = skillContext == null ? SkillPromptContext.empty() : skillContext;
        reminders = reminders == null ? List.of() : List.copyOf(reminders);
        history = history == null ? List.of() : List.copyOf(history);
    }
}