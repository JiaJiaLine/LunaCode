package com.lunacode.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SlashCommandCompleterTest {
    @Test
    void completesSingleVisibleMatch() {
        SlashCommandCompleter completer = new SlashCommandCompleter(registry());

        SlashCommandCompletion.Single completion = (SlashCommandCompletion.Single) completer.complete("/pe", 3);

        assertEquals("/permission", completion.replacement());
    }

    @Test
    void returnsMultipleVisibleMatchesInStableOrder() {
        SlashCommandCompleter completer = new SlashCommandCompleter(registry());

        SlashCommandCompletion.Multiple completion = (SlashCommandCompletion.Multiple) completer.complete("/p", 2);

        assertEquals(List.of("/plan", "/pl", "/permission", "/perm"), completion.candidates().stream().map(SlashCommandName::value).toList());
    }

    @Test
    void doesNotCompleteArgumentsOrNonCommandInput() {
        SlashCommandCompleter completer = new SlashCommandCompleter(registry());

        assertInstanceOf(SlashCommandCompletion.NoMatch.class, completer.complete("hello", 5));
        assertInstanceOf(SlashCommandCompletion.NoMatch.class, completer.complete("/help re", 8));
    }

    @Test
    void hiddenCommandsAreNotCandidates() {
        SlashCommandCompleter completer = new SlashCommandCompleter(registry());

        assertInstanceOf(SlashCommandCompletion.NoMatch.class, completer.complete("/s", 2));
    }

    private SlashCommandRegistry registry() {
        SlashCommandRegistry registry = new SlashCommandRegistry();
        registry.register(command("/plan", List.of("/pl"), false));
        registry.register(command("/permission", List.of("/perm"), false));
        registry.register(command("/secret", List.of("/s"), true));
        return registry;
    }

    private SlashCommandDefinition command(String name, List<String> aliases, boolean hidden) {
        return new SlashCommandDefinition(name, aliases, "描述", name, SlashCommandType.LOCAL, "", hidden, context -> {});
    }
}
