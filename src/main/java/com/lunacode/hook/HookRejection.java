package com.lunacode.hook;

public record HookRejection(String hookId, String toolName, String reason) {
    public HookRejection {
        hookId = hookId == null ? "" : hookId;
        toolName = toolName == null ? "" : toolName;
        reason = reason == null || reason.isBlank() ? "Hook 拒绝了工具调用，但没有提供原因。" : reason;
    }
}
