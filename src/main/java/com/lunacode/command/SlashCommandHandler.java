package com.lunacode.command;

@FunctionalInterface
public interface SlashCommandHandler {
    void handle(SlashCommandContext context);
}
