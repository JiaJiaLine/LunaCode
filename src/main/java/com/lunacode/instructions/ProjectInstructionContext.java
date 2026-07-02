package com.lunacode.instructions;

import java.util.List;

public record ProjectInstructionContext(List<InstructionSection> sections) {
    public ProjectInstructionContext {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        for (InstructionSection section : sections) {
            if (section.content().isBlank()) {
                continue;
            }
            out.append("## ")
                    .append(section.source().scope())
                    .append(" ")
                    .append(section.source().path())
                    .append('\n')
                    .append(section.content().strip())
                    .append("\n\n");
        }
        return out.toString().strip();
    }
}
