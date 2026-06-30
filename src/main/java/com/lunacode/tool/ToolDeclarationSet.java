package com.lunacode.tool;

import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.List;
import java.util.Objects;

public record ToolDeclarationSet(
        ArrayNode visibleTools,
        List<DeferredToolSummary> deferredTools
) {
    public ToolDeclarationSet {
        visibleTools = Objects.requireNonNull(visibleTools, "visibleTools");
        deferredTools = deferredTools == null ? List.of() : List.copyOf(deferredTools);
    }
}
