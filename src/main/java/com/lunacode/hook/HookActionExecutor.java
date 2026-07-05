package com.lunacode.hook;

public interface HookActionExecutor {
    HookActionResult execute(HookDefinition hook, HookContext context, HookExecutionScope scope);
}
