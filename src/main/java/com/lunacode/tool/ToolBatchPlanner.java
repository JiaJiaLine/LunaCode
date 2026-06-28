package com.lunacode.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ToolBatchPlanner {
    public List<ToolBatch> plan(List<ToolUse> toolUses, ToolRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        List<ToolBatch> batches = new ArrayList<>();
        List<ToolUse> parallel = new ArrayList<>();
        for (ToolUse toolUse : toolUses == null ? List.<ToolUse>of() : toolUses) {
            Tool tool = registry.get(toolUse.name()).orElse(null);
            if (tool != null
                    && tool.isReadOnly()
                    && !tool.isDestructive()
                    && tool.isConcurrencySafe(toolUse.input())
                    && !"AskUserQuestion".equals(tool.name())) {
                parallel.add(toolUse);
                continue;
            }
            flushParallel(batches, parallel);
            batches.add(new ToolBatch(List.of(toolUse), false));
        }
        flushParallel(batches, parallel);
        return List.copyOf(batches);
    }

    private void flushParallel(List<ToolBatch> batches, List<ToolUse> parallel) {
        if (!parallel.isEmpty()) {
            batches.add(new ToolBatch(List.copyOf(parallel), true));
            parallel.clear();
        }
    }
}
