package com.lunacode.instructions;

import java.util.Objects;

public record InstructionSection(
        InstructionSource source,
        String content
) {
    public InstructionSection {
        source = Objects.requireNonNull(source, "source");
        content = content == null ? "" : content;
    }
}
