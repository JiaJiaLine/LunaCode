package com.lunacode.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlashCommandRegistryTest {
    @Test
    void findsCommandByMainNameAndAliasCaseInsensitively() {
        SlashCommandRegistry registry = new SlashCommandRegistry();
        SlashCommandDefinition help = command("/help", List.of("/h", "/?"), false);
        registry.register(help);

        assertSame(help, registry.require("/HELP"));
        assertSame(help, registry.require("/h"));
        assertSame(help, registry.require("help"));
    }

    @Test
    void rejectsMainNameAndAliasConflicts() {
        SlashCommandRegistry registry = new SlashCommandRegistry();
        registry.register(command("/help", List.of("/h"), false));

        SlashCommandRegistrationException error = assertThrows(
                SlashCommandRegistrationException.class,
                () -> registry.register(command("/status", List.of("/H"), false))
        );
        assertTrue(error.getMessage().contains("/H"));
        assertTrue(error.getMessage().contains("/help"));
        assertTrue(error.getMessage().contains("/status"));
    }

    @Test
    void visibleCommandsAndNamesSkipHiddenDefinitionsAndKeepOrder() {
        SlashCommandRegistry registry = new SlashCommandRegistry();
        registry.register(command("/help", List.of("/h"), false));
        registry.register(command("/secret", List.of("/s"), true));
        registry.register(command("/status", List.of("/st"), false));

        assertEquals(List.of("/help", "/status"), registry.visibleCommands().stream().map(SlashCommandDefinition::name).toList());
        assertEquals(
                List.of("/help", "/h", "/status", "/st"),
                registry.visibleNames().stream().map(SlashCommandName::value).toList()
        );
    }

    @Test
    void rejectsInvalidNames() {
        SlashCommandRegistry registry = new SlashCommandRegistry();

        assertThrows(SlashCommandRegistrationException.class, () -> registry.register(command("help", List.of(), false)));
        assertThrows(SlashCommandRegistrationException.class, () -> registry.register(command("/bad name", List.of(), false)));
    }

    private SlashCommandDefinition command(String name, List<String> aliases, boolean hidden) {
        return new SlashCommandDefinition(name, aliases, "描述", name, SlashCommandType.LOCAL, "", hidden, context -> {});
    }
}
