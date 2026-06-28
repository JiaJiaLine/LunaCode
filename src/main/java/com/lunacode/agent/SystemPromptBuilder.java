package com.lunacode.agent;

public final class SystemPromptBuilder {
    public String build(SystemPromptConfig config) {
        AgentMode mode = config.mode() == null ? AgentMode.DEFAULT : config.mode();
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 LunaCode，一个终端环境中的 AI 编程助手。\n");
        prompt.append("你擅长阅读代码、编写代码和调试问题。\n");
        prompt.append("你会先思考再行动，用简洁中文说明你正在做什么和为什么。\n\n");
        prompt.append("# Environment\n");
        prompt.append("当前工作目录：").append(config.workDir()).append('\n');
        prompt.append("操作系统：").append(config.osName()).append('\n');
        prompt.append("当前时间：").append(config.now()).append('\n');
        if (mode == AgentMode.PLAN) {
            prompt.append("\n# Plan Mode\n");
            prompt.append("Plan mode is active.\n");
            prompt.append("你不能执行任何修改操作，不能编辑非 plan 文件，不能提交代码，不能修改配置。\n");
            prompt.append("唯一可以写入的文件是指定 plan file：").append(config.planFile()).append('\n');
            prompt.append("你的工作流程：\n");
            prompt.append("1. 如果需求不清楚，先使用 AskUserQuestion 一次提出一个聚焦问题，逐步澄清需求。\n");
            prompt.append("2. 用 ReadFile、Grep、Glob、Bash（只读命令）探索代码。\n");
            prompt.append("3. 分析用户需求，设计实现方案。\n");
            prompt.append("4. 把计划写入 plan file。\n");
            prompt.append("5. 等待用户确认后再执行。\n");
        }
        return prompt.toString();
    }
}
