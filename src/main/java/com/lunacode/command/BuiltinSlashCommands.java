package com.lunacode.command;

import com.lunacode.permission.PermissionMode;

import java.util.List;

public final class BuiltinSlashCommands {
    private BuiltinSlashCommands() {
    }

    public static void registerAll(SlashCommandRegistry registry) {
        registry.register(command("/help", List.of("/h", "/?"), "显示命令帮助", "/help [command]", SlashCommandType.LOCAL, "[command]", false, BuiltinSlashCommands::help));
        registry.register(command("/compact", List.of("/cp"), "手动压缩当前上下文", "/compact", SlashCommandType.LOCAL, "", false, context -> context.runtime().compactContext()));
        registry.register(command("/clear", List.of("/cl"), "清空终端可见输出", "/clear", SlashCommandType.UI_STATE, "", false, context -> context.runtime().clearVisibleScreen()));
        registry.register(command("/plan", List.of("/pl"), "进入计划模式", "/plan", SlashCommandType.UI_STATE, "", false, context -> context.runtime().enterPlanMode()));
        registry.register(command("/do", List.of("/d"), "回到执行模式", "/do", SlashCommandType.UI_STATE, "", false, context -> context.runtime().enterDefaultMode()));
        registry.register(command("/session", List.of("/sess"), "管理会话", "/session [current|list|resume <id>|new]", SlashCommandType.LOCAL, "[current|list|resume <id>|new]", false, context -> context.runtime().runSessionCommand(canonicalRaw("/session", context.args()))));
        registry.register(command("/memory", List.of("/mem"), "管理记忆", "/memory [list|delete <id>|on|off]", SlashCommandType.LOCAL, "[list|delete <id>|on|off]", false, context -> context.runtime().runMemoryCommand(canonicalRaw("/memory", context.args()))));
        registry.register(command("/permission", List.of("/perm", "/permissions"), "查看或切换权限模式", "/permission [default|acceptEdits|plan|bypassPermissions]", SlashCommandType.UI_STATE, "[default|acceptEdits|plan|bypassPermissions]", false, BuiltinSlashCommands::permission));
        registry.register(command("/status", List.of("/st"), "显示当前运行状态", "/status", SlashCommandType.LOCAL, "", false, BuiltinSlashCommands::status));
        registry.register(command("/review", List.of("/r"), "审查当前 git diff", "/review [额外关注]", SlashCommandType.PROMPT, "[额外关注]", false, BuiltinSlashCommands::review));
        registry.register(command("/cancel", List.of("/x"), "取消当前运行或等待", "/cancel", SlashCommandType.UI_STATE, "", false, context -> context.runtime().cancelCurrentRun()));
    }

    private static SlashCommandDefinition command(
            String name,
            List<String> aliases,
            String description,
            String usage,
            SlashCommandType type,
            String argumentHint,
            boolean hidden,
            SlashCommandHandler handler
    ) {
        return new SlashCommandDefinition(name, aliases, description, usage, type, argumentHint, hidden, handler);
    }

    private static void help(SlashCommandContext context) {
        String args = context.args();
        if (args.isBlank()) {
            StringBuilder out = new StringBuilder("可用命令:");
            for (SlashCommandDefinition definition : context.registry().visibleCommands()) {
                out.append('\n')
                        .append("- ")
                        .append(definition.name())
                        .append(formatAliases(definition.aliases()))
                        .append(" - ")
                        .append(definition.description());
                if (!definition.usage().isBlank()) {
                    out.append("\n  用法: ").append(definition.usage());
                }
            }
            context.runtime().showInfo(out.toString());
            return;
        }

        context.registry().find(args).ifPresentOrElse(definition -> {
            StringBuilder out = new StringBuilder();
            out.append(definition.name()).append(formatAliases(definition.aliases())).append('\n');
            out.append(definition.description()).append('\n');
            out.append("类型: ").append(definition.type()).append('\n');
            out.append("用法: ").append(definition.usage());
            if (!definition.argumentHint().isBlank()) {
                out.append('\n').append("参数: ").append(definition.argumentHint());
            }
            context.runtime().showInfo(out.toString());
        }, () -> context.runtime().showError("未知命令: " + args + "。输入 /help 查看可用命令。"));
    }


    private static String canonicalRaw(String commandName, String args) {
        if (args == null || args.isBlank()) {
            return commandName;
        }
        return commandName + " " + args.strip();
    }    private static String formatAliases(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return "";
        }
        return " (" + String.join(", ", aliases) + ")";
    }

    private static void permission(SlashCommandContext context) {
        String args = context.args();
        if (args.isBlank()) {
            context.runtime().showInfo("当前权限模式: " + context.runtime().runtimeStatus().permissionMode().configValue());
            return;
        }
        String[] parts = args.split("\\s+");
        if (parts.length != 1) {
            context.runtime().showError("用法: /permission [default|acceptEdits|plan|bypassPermissions]");
            return;
        }
        PermissionMode mode;
        try {
            mode = PermissionMode.fromConfig(parts[0]);
        } catch (IllegalArgumentException e) {
            context.runtime().showError("未知权限模式: " + parts[0] + "。用法: /permission [default|acceptEdits|plan|bypassPermissions]");
            return;
        }
        if (mode == PermissionMode.BYPASS_PERMISSIONS) {
            context.runtime().requestDangerousPermissionMode(mode);
            return;
        }
        context.runtime().switchPermissionMode(mode);
    }

    private static void status(SlashCommandContext context) {
        CommandRuntimeStatus status = context.runtime().runtimeStatus();
        String memory = status.memoryAutoUpdateEnabled() == null
                ? status.memoryLatestState()
                : (status.memoryAutoUpdateEnabled() ? "on" : "off")
                + (status.memoryLatestState().isBlank() ? "" : ":" + status.memoryLatestState());
        String out = """
                Agent 模式: [%s]
                权限模式: %s
                Provider: %s
                Model: %s
                输入 token: %s
                输出 token: %s
                会话: %s
                记忆: %s
                运行状态: %s
                """.formatted(
                status.agentMode(),
                status.permissionMode().configValue(),
                blankAsDash(status.provider()),
                blankAsDash(status.model()),
                valueOrDash(status.inputTokens()),
                valueOrDash(status.outputTokens()),
                blankAsDash(status.sessionShortId()),
                blankAsDash(memory),
                blankAsDash(status.state())
        ).strip();
        context.runtime().showInfo(out);
    }

    private static String blankAsDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String valueOrDash(Integer value) {
        return value == null ? "-" : value.toString();
    }

    private static void review(SlashCommandContext context) {
        String prompt = """
                请审查当前 git diff 中的代码变更。重点关注：
                1. 逻辑错误
                2. 安全问题
                3. 性能问题
                4. 代码风格
                """.strip();
        if (!context.args().isBlank()) {
            prompt += "\n\n额外关注：" + context.args();
        }
        context.runtime().sendUserMessage(prompt);
    }
}
