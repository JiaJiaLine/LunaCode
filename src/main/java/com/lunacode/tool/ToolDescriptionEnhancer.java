package com.lunacode.tool;

import java.util.Set;

public final class ToolDescriptionEnhancer {
    private static final Set<String> READ_TOOLS = Set.of("ReadFile", "Glob", "Grep");
    private static final Set<String> WRITE_TOOLS = Set.of("WriteFile", "EditFile");

    public String enhance(Tool tool) {
        String base = tool.description() == null ? "" : tool.description().strip();
        String rule = ruleFor(tool);
        if (rule.isBlank() || base.contains(rule)) {
            return base;
        }
        return base.isBlank() ? rule : base + "\n\n" + rule;
    }

    private String ruleFor(Tool tool) {
        if (READ_TOOLS.contains(tool.name()) || (tool.isReadOnly() && !tool.isDestructive())) {
            return "使用规则：这是只读探索工具。优先用它理解项目、读取事实和缩小范围，再决定是否需要修改。";
        }
        if (WRITE_TOOLS.contains(tool.name())) {
            return "使用规则：编辑前必须先读取目标文件当前内容；修改必须基于已观察到的当前内容，避免盲写或覆盖用户改动。";
        }
        if ("Bash".equals(tool.name())) {
            return "使用规则：优先使用 ReadFile、Glob、Grep、WriteFile、EditFile 等专用工具；只有缺少专用工具或确有必要时才使用命令。";
        }
        return "";
    }
}
