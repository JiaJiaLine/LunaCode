package com.lunacode.command;

import java.util.List;
import java.util.Locale;

public final class SlashCommandCompleter {
    private final SlashCommandRegistry registry;

    public SlashCommandCompleter(SlashCommandRegistry registry) {
        this.registry = registry;
    }

    public SlashCommandCompletion complete(String input, int cursorIndex) {
        String value = input == null ? "" : input;
        int cursor = Math.max(0, Math.min(cursorIndex, value.length()));
        int tokenEnd = firstWhitespace(value);
        if (tokenEnd < 0) {
            tokenEnd = value.length();
        }
        if (cursor > tokenEnd) {
            return new SlashCommandCompletion.NoMatch();
        }
        String token = value.substring(0, cursor).toLowerCase(Locale.ROOT);
        if (!token.startsWith("/")) {
            return new SlashCommandCompletion.NoMatch();
        }
        List<SlashCommandName> matches = registry.visibleNames().stream()
                .filter(name -> name.value().startsWith(token))
                .toList();
        if (matches.isEmpty()) {
            return new SlashCommandCompletion.NoMatch();
        }
        List<String> owners = matches.stream()
                .map(SlashCommandName::ownerCommand)
                .distinct()
                .toList();
        if (owners.size() == 1) {
            return new SlashCommandCompletion.Single(owners.get(0));
        }
        return new SlashCommandCompletion.Multiple(matches);
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

