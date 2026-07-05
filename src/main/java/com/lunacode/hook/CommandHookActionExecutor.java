package com.lunacode.hook;

import com.lunacode.tool.ToolExecutionContext;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CommandHookActionExecutor implements HookActionExecutor {
    private final ShellCommandRunner runner;
    private final ToolExecutionContext toolExecutionContext;

    public CommandHookActionExecutor(ShellCommandRunner runner, ToolExecutionContext toolExecutionContext) {
        this.runner = runner == null ? new ShellCommandRunner() : runner;
        this.toolExecutionContext = toolExecutionContext;
    }

    @Override
    public HookActionResult execute(HookDefinition hook, HookContext context, HookExecutionScope scope) {
        if (!(hook.action() instanceof HookAction.Command command)) {
            return HookActionResult.failure("Hook action 不是 command", Map.of("errorType", "invalid_action"));
        }
        Duration timeout = hook.timeout().orElse(null);
        return runner.run(command.command(), timeout, environment(context), toolExecutionContext);
    }

    private Map<String, String> environment(HookContext context) {
        HookContext safe = context == null ? HookContext.empty(null) : context;
        Map<String, String> env = new LinkedHashMap<>();
        env.put("EVENT_NAME", safe.eventName());
        env.put("TOOL_NAME", safe.toolName());
        env.put("FILE_PATH", safe.filePath());
        env.put("MESSAGE", safe.message());
        env.put("ERROR", safe.error());
        safe.toolArgs().forEach((key, value) -> env.put("ARGS_" + envKey(key), value == null ? "" : value));
        return env;
    }

    private String envKey(String key) {
        return (key == null ? "" : key)
                .replaceAll("[^A-Za-z0-9]", "_")
                .toUpperCase(Locale.ROOT);
    }
}
