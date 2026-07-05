package com.lunacode.hook;

public record PendingHookReminder(String hookId, String content, int availableTurnIndex) {
    public PendingHookReminder {
        hookId = hookId == null ? "" : hookId;
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Hook reminder 内容不能为空");
        }
        availableTurnIndex = Math.max(1, availableTurnIndex);
    }
}
