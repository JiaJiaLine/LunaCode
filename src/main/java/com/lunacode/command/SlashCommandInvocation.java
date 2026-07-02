package com.lunacode.command;

public record SlashCommandInvocation(
        String rawInput,
        String rawName,
        String normalizedName,
        String args
) {
    public SlashCommandInvocation {
        rawInput = rawInput == null ? "" : rawInput;
        rawName = rawName == null ? "" : rawName;
        normalizedName = normalizedName == null ? "" : normalizedName;
        args = args == null ? "" : args;
    }
}
