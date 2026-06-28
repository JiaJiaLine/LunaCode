package com.lunacode.agent;

public final class SystemReminderRenderer {
    public String render(SystemReminder reminder) {
        return """
                [System Reminder]
                这是系统级补充上下文，不是用户请求；不要直接回答本段内容本身。
                类型：%s
                轮次：%d
                内容：
                %s
                """.formatted(reminder.kind(), reminder.turnIndex(), reminder.content()).strip();
    }
}
