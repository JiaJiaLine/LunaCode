package com.lunacode.tool;

import java.util.List;

public record ToolBatch(
        List<ToolUse> toolUses,
        boolean parallel
) {
    public ToolBatch {
        toolUses = toolUses == null ? List.of() : List.copyOf(toolUses);
    }
}
