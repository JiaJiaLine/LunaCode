package com.lunacode.prompt;

import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.Objects;

public record PromptBundle(
        SystemChannel system,
        ArrayNode toolDeclarations,
        MessageChannel messages,
        PromptCachePolicy cachePolicy
) {
    public PromptBundle {
        system = Objects.requireNonNull(system, "system");
        toolDeclarations = Objects.requireNonNull(toolDeclarations, "toolDeclarations");
        messages = Objects.requireNonNull(messages, "messages");
        cachePolicy = cachePolicy == null ? PromptCachePolicy.enabled() : cachePolicy;
    }
}
