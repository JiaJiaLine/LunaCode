package com.lunacode.prompt;

import com.lunacode.conversation.ApiMessage;

import java.util.List;
import java.util.Optional;

public record MessageChannel(
        Optional<ProjectInstructionContext> projectInstructions,
        Optional<MemoryContext> memory,
        List<SystemReminder> reminders,
        List<ApiMessage> history
) {
    public MessageChannel {
        projectInstructions = projectInstructions == null ? Optional.empty() : projectInstructions;
        memory = memory == null ? Optional.empty() : memory;
        reminders = reminders == null ? List.of() : List.copyOf(reminders);
        history = history == null ? List.of() : List.copyOf(history);
    }
}
