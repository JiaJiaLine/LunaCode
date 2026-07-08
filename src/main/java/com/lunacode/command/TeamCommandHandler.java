package com.lunacode.command;

public interface TeamCommandHandler {
    CommandResult handle(String rawInput, boolean busy);

    record CommandResult(String state, String message) {
        public CommandResult {
            state = state == null || state.isBlank() ? "idle" : state;
            message = message == null ? "" : message;
        }
    }
}
