package com.lunacode.command;

public sealed interface SlashCommandParseResult {
    record NotCommand() implements SlashCommandParseResult {}

    record Command(SlashCommandInvocation invocation) implements SlashCommandParseResult {}
}
