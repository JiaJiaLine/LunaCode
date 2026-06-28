package com.lunacode.prompt;

import java.util.Objects;

public record SystemReminder(SystemReminderKind kind, String content, int turnIndex) {
    public SystemReminder {
        kind = Objects.requireNonNull(kind, "kind");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("System Reminder 内容不能为空");
        }
        if (turnIndex <= 0) {
            throw new IllegalArgumentException("turnIndex 必须大于 0");
        }
    }
}
