package com.lunacode.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SlashCommandRegistry {
    private final List<SlashCommandDefinition> definitions = new ArrayList<>();
    private final Map<String, SlashCommandDefinition> byName = new LinkedHashMap<>();

    public void register(SlashCommandDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        validateName(definition.name(), "命令主名称");
        List<String> names = new ArrayList<>();
        names.add(definition.name());
        for (String alias : definition.aliases()) {
            validateName(alias, "命令别名");
            names.add(alias);
        }

        for (String name : names) {
            String normalized = normalize(name);
            SlashCommandDefinition existing = byName.get(normalized);
            if (existing != null) {
                throw new SlashCommandRegistrationException(
                        "斜杠命令名称冲突: " + name + " 同时属于 " + existing.name() + " 和 " + definition.name()
                );
            }
        }

        definitions.add(definition);
        for (String name : names) {
            byName.put(normalize(name), definition);
        }
    }

    public SlashCommandDefinition require(String nameOrAlias) {
        return find(nameOrAlias).orElseThrow(() ->
                new SlashCommandRegistrationException("未注册的斜杠命令: " + nameOrAlias)
        );
    }

    public Optional<SlashCommandDefinition> find(String nameOrAlias) {
        if (nameOrAlias == null || nameOrAlias.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(normalizeLookup(nameOrAlias)));
    }

    public List<SlashCommandDefinition> visibleCommands() {
        return definitions.stream()
                .filter(definition -> !definition.hidden())
                .toList();
    }

    public List<SlashCommandName> visibleNames() {
        List<SlashCommandName> result = new ArrayList<>();
        for (SlashCommandDefinition definition : definitions) {
            if (definition.hidden()) {
                continue;
            }
            result.add(new SlashCommandName(definition.name(), definition.name()));
            for (String alias : definition.aliases()) {
                result.add(new SlashCommandName(alias, definition.name()));
            }
        }
        return List.copyOf(result);
    }

    private void validateName(String name, String label) {
        if (name == null || name.isBlank()) {
            throw new SlashCommandRegistrationException(label + "不能为空");
        }
        if (!name.startsWith("/")) {
            throw new SlashCommandRegistrationException(label + "必须以 / 开头: " + name);
        }
        if (name.strip().contains(" ")) {
            throw new SlashCommandRegistrationException(label + "不能包含空格: " + name);
        }
    }

    private String normalizeLookup(String nameOrAlias) {
        String stripped = nameOrAlias.strip();
        if (!stripped.startsWith("/")) {
            stripped = "/" + stripped;
        }
        return normalize(stripped);
    }

    private String normalize(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
