package com.lunacode.prompt;

import com.lunacode.tool.DeferredToolSummary;

import java.util.List;

public final class DeferredToolReminderPolicy {
    public SystemReminder createReminder(ModeInjectionState state, List<DeferredToolSummary> deferredTools) {
        if (deferredTools == null || deferredTools.isEmpty()) {
            return null;
        }
        StringBuilder content = new StringBuilder();
        content.append("以下 MCP 延迟工具已在本地注册，但完整 schema 尚未进入普通工具列表。");
        content.append("如果需要使用其中某个工具，先调用 ToolSearch，并传入公开工具名；ToolSearch 只查询本地元数据，不会调用远端 MCP Server。");
        content.append('\n');
        for (DeferredToolSummary tool : deferredTools) {
            content.append("- ").append(tool.name());
            if (tool.description() != null && !tool.description().isBlank()) {
                content.append(": ").append(tool.description().replaceAll("\\s+", " ").strip());
            }
            content.append('\n');
        }
        return new SystemReminder(SystemReminderKind.MCP_HINT, content.toString().strip(), state.turnIndex());
    }
}
