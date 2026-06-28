package com.lunacode.agent;

public enum PromptSectionKind {
    ROLE("角色设定"),
    BEHAVIOR("行为准则"),
    TOOL_USAGE("工具使用指南"),
    CODE_QUALITY("代码质量规范"),
    SAFETY("安全边界"),
    TASK_MODES("任务执行模式"),
    OUTPUT_STYLE("输出风格");

    private final String title;

    PromptSectionKind(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
