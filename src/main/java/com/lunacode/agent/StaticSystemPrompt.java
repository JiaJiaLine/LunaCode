package com.lunacode.agent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record StaticSystemPrompt(List<PromptSection> sections) {
    public StaticSystemPrompt {
        Objects.requireNonNull(sections, "sections");
        Map<PromptSectionKind, PromptSection> byKind = new EnumMap<>(PromptSectionKind.class);
        for (PromptSection section : sections) {
            PromptSection previous = byKind.put(section.kind(), section);
            if (previous != null) {
                throw new IllegalArgumentException("提示模块重复: " + section.kind());
            }
        }
        for (PromptSectionKind kind : PromptSectionKind.values()) {
            if (!byKind.containsKey(kind)) {
                throw new IllegalArgumentException("缺少提示模块: " + kind);
            }
        }
        sections = List.copyOf(sections);
    }

    public String render() {
        Map<PromptSectionKind, PromptSection> byKind = new EnumMap<>(PromptSectionKind.class);
        for (PromptSection section : sections) {
            byKind.put(section.kind(), section);
        }
        StringBuilder result = new StringBuilder();
        for (PromptSectionKind kind : PromptSectionKind.values()) {
            if (!result.isEmpty()) {
                result.append("\n\n");
            }
            PromptSection section = byKind.get(kind);
            result.append("# ").append(section.title()).append('\n');
            result.append(section.content().strip());
        }
        return result.toString();
    }
}
