package com.lunacode.command;

public final class SlashCommandDispatcher {
    private static final String CANCEL_COMMAND = "/cancel";

    private final SlashCommandRegistry registry;
    private final SlashCommandParser parser;

    public SlashCommandDispatcher(SlashCommandRegistry registry, SlashCommandParser parser) {
        this.registry = registry;
        this.parser = parser;
    }

    public DispatchResult dispatch(String input, CommandRuntime runtime) {
        SlashCommandParseResult result = parser.parse(input);
        if (result instanceof SlashCommandParseResult.NotCommand) {
            return DispatchResult.NOT_COMMAND;
        }
        SlashCommandInvocation invocation = ((SlashCommandParseResult.Command) result).invocation();
        SlashCommandDefinition definition = registry.find(invocation.normalizedName()).orElse(null);
        if (definition == null) {
            runtime.showError("未知命令: " + invocation.rawName() + "。输入 /help 查看可用命令。");
            return DispatchResult.HANDLED;
        }
        if (!CANCEL_COMMAND.equals(definition.name()) && isCommandBlocked(runtime)) {
            runtime.showWarning("当前忙，请稍后再试。可使用 /cancel 取消当前操作。");
            return DispatchResult.HANDLED;
        }
        definition.handler().handle(new SlashCommandContext(invocation, registry, runtime));
        return DispatchResult.HANDLED;
    }

    private boolean isCommandBlocked(CommandRuntime runtime) {
        return runtime.isBusy()
                || runtime.hasPendingUserAnswer()
                || runtime.hasPendingPermissionAnswer()
                || runtime.hasPendingDangerousModeConfirmation();
    }
}
