package com.lunacode.worktree;

public interface WorktreeCommandHandler {
    CommandResult handle(String rawInput, boolean busy);

    record CommandResult(String state, String message) {
        public CommandResult {
            state = state == null || state.isBlank() ? "idle" : state;
            message = message == null ? "" : message;
        }
    }
}
