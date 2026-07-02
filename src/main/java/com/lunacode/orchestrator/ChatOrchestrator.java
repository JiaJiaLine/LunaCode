package com.lunacode.orchestrator;

import com.lunacode.command.CommandUiController;
import com.lunacode.command.SlashCommandCompletion;

public interface ChatOrchestrator {
    void submitUserMessage(String content);

    void cancelCurrentRun();

    default SlashCommandCompletion completeSlashCommand(String input, int cursorIndex) {
        return new SlashCommandCompletion.NoMatch();
    }

    default void setCommandUiController(CommandUiController controller) {
    }

    StatusSnapshot status();
}