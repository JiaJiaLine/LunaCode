package com.lunacode.command;

import java.util.List;

public sealed interface SlashCommandCompletion {
    record NoMatch() implements SlashCommandCompletion {}

    record Single(String replacement) implements SlashCommandCompletion {}

    record Multiple(List<SlashCommandName> candidates) implements SlashCommandCompletion {
        public Multiple {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }
}
