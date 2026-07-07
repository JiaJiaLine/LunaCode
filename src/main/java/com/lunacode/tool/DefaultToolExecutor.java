package com.lunacode.tool;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public class DefaultToolExecutor implements ToolExecutor {
    private final ToolRegistry registry;
    private final ToolExecutionContext context;

    public DefaultToolExecutor(ToolRegistry registry, ToolExecutionContext context) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public ToolResult execute(ToolUse toolUse) {
        if (toolUse == null) {
            return ToolResult.error("工具调用为空", Map.of("errorType", "invalid_tool_use"));
        }
        Tool tool = registry.get(toolUse.name()).orElse(null);
        if (tool == null) {
            return ToolResult.error("工具不存在或已禁用: " + toolUse.name(), Map.of("errorType", "tool_not_found"));
        }
        ValidationError validation = tool.validateInput(toolUse.input());
        if (validation != null) {
            return ToolResult.error(validation.message(), Map.of("errorType", "invalid_arguments", "code", validation.code()));
        }
        try {
                        ToolExecutionContext effectiveContext = ToolExecutionScopeHolder.currentWorkDir()
                    .map(context::withWorkspaceRoot)
                    .orElse(context);
            return tool.execute(effectiveContext, toolUse.input());
        } catch (Exception e) {
            return ToolResult.error("工具执行失败: " + e.getMessage(), Map.of("errorType", "execution_error"));
        }
    }

    public ToolExecutionRecord executeRecord(ToolUse toolUse) {
        long started = System.nanoTime();
        ToolResult result = execute(toolUse);
        return new ToolExecutionRecord(toolUse, result, Duration.ofNanos(System.nanoTime() - started));
    }
}
