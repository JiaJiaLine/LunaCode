package com.lunacode.command;

import java.util.List;

public record SlashCommandDefinition(
        String name,
        List<String> aliases,
        String description,
        String usage,
        SlashCommandType type,
        String argumentHint,
        boolean hidden,
        SlashCommandHandler handler
) {
    public SlashCommandDefinition {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        description = description == null ? "" : description;
        usage = usage == null ? "" : usage;
        argumentHint = argumentHint == null ? "" : argumentHint;
        if (type == null) {
            type = SlashCommandType.LOCAL;
        }
        if (handler == null) {
            throw new SlashCommandRegistrationException("命令 " + name + " 缺少处理函数");
        }
    }
}
