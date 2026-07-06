package com.lunacode.background;

import java.util.Locale;

public final class TaskNotificationFormatter {
    public String format(BackgroundTaskSnapshot task) {
        if (task == null) {
            return "<task-notification>\nstatus: failed\nsummary: 后台任务不存在\nresult:\n后台任务不存在。\n</task-notification>";
        }
        String status = task.status().name().toLowerCase(Locale.ROOT);
        String summary = summary(task);
        StringBuilder out = new StringBuilder();
        out.append("<task-notification>\n");
        out.append("id: ").append(task.id()).append('\n');
        out.append("status: ").append(status).append('\n');
        out.append("summary: ").append(summary).append('\n');
        out.append("tool_calls: ").append(task.toolCallCount()).append('\n');
        if (task.usage().totalTokens() != null) {
            out.append("tokens: ").append(task.usage().totalTokens()).append('\n');
        }
        out.append("result:\n");
        out.append(task.result().isBlank() ? task.failureReason() : task.result()).append('\n');
        out.append("</task-notification>");
        return out.toString();
    }

    private String summary(BackgroundTaskSnapshot task) {
        if (task.status() == BackgroundTaskStatus.FAILED) {
            return task.failureReason().isBlank() ? "后台子 Agent 失败" : compact(task.failureReason());
        }
        if (!task.result().isBlank()) {
            return compact(task.result());
        }
        if (!task.recentActivity().isBlank()) {
            return compact(task.recentActivity());
        }
        return "后台子 Agent " + task.status().name().toLowerCase(Locale.ROOT);
    }

    private String compact(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180) + "...";
    }
}
