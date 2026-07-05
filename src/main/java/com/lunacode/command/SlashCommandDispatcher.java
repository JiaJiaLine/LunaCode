package com.lunacode.command;

import com.lunacode.hook.HookContext;
import com.lunacode.hook.HookEventName;
import com.lunacode.hook.HookExecutionScope;
import com.lunacode.hook.HookRuntime;
import com.lunacode.hook.NoOpHookRuntime;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

public final class SlashCommandDispatcher {
    private static final String CANCEL_COMMAND = "/cancel";

    private final SlashCommandRegistry registry;
    private final SlashCommandParser parser;
    private final HookRuntime hookRuntime;
    private final Supplier<String> sessionIdSupplier;
    private final Supplier<Path> workspaceRootSupplier;

    public SlashCommandDispatcher(SlashCommandRegistry registry, SlashCommandParser parser) {
        this(registry, parser, NoOpHookRuntime.instance(), () -> "", () -> Path.of("."));
    }

    public SlashCommandDispatcher(
            SlashCommandRegistry registry,
            SlashCommandParser parser,
            HookRuntime hookRuntime,
            Supplier<String> sessionIdSupplier,
            Supplier<Path> workspaceRootSupplier
    ) {
        this.registry = registry;
        this.parser = parser;
        this.hookRuntime = hookRuntime == null ? NoOpHookRuntime.instance() : hookRuntime;
        this.sessionIdSupplier = sessionIdSupplier == null ? () -> "" : sessionIdSupplier;
        this.workspaceRootSupplier = workspaceRootSupplier == null ? () -> Path.of(".") : workspaceRootSupplier;
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
            emitCommand(input, "未知命令: " + invocation.rawName());
            return DispatchResult.HANDLED;
        }
        if (!CANCEL_COMMAND.equals(definition.name()) && isCommandBlocked(runtime)) {
            runtime.showWarning("当前忙，请稍后再试。可使用 /cancel 取消当前操作。");
            emitCommand(input, "命令被忙碌状态阻止");
            return DispatchResult.HANDLED;
        }
        try {
            definition.handler().handle(new SlashCommandContext(invocation, registry, runtime));
            emitCommand(input, "");
        } catch (RuntimeException e) {
            emitCommand(input, e.getMessage());
            throw e;
        }
        return DispatchResult.HANDLED;
    }

    private boolean isCommandBlocked(CommandRuntime runtime) {
        return runtime.isBusy()
                || runtime.hasPendingUserAnswer()
                || runtime.hasPendingPermissionAnswer()
                || runtime.hasPendingDangerousModeConfirmation();
    }

    private void emitCommand(String input, String error) {
        Path workspaceRoot;
        try {
            workspaceRoot = workspaceRootSupplier.get();
        } catch (RuntimeException e) {
            workspaceRoot = Path.of(".");
        }
        String sessionId;
        try {
            sessionId = sessionIdSupplier.get();
        } catch (RuntimeException e) {
            sessionId = "";
        }
        hookRuntime.emit(
                HookEventName.COMMAND_EXECUTE,
                new HookContext(HookEventName.COMMAND_EXECUTE.yamlName(), "", Map.of(), "", input, error == null ? "" : error),
                new HookExecutionScope(sessionId, 0, workspaceRoot)
        );
    }
}