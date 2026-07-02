package com.lunacode.command;

public record SlashCommandContext(
        SlashCommandInvocation invocation,
        SlashCommandRegistry registry,
        CommandRuntime runtime
) {
    public String args() {
        return invocation == null ? "" : invocation.args();
    }
}
