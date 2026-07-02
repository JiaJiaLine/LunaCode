package com.lunacode.command;

import java.util.Locale;

public final class SlashCommandParser {
    public SlashCommandParseResult parse(String input) {
        if (input == null || input.isBlank()) {
            return new SlashCommandParseResult.NotCommand();
        }
        String stripped = input.strip();
        if (!stripped.startsWith("/")) {
            return new SlashCommandParseResult.NotCommand();
        }

        int split = firstWhitespace(stripped);
        String rawName = split < 0 ? stripped : stripped.substring(0, split);
        String args = split < 0 ? "" : stripped.substring(split).strip();
        String normalizedName = rawName.toLowerCase(Locale.ROOT);
        return new SlashCommandParseResult.Command(
                new SlashCommandInvocation(input, rawName, normalizedName, args)
        );
    }

    private int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
