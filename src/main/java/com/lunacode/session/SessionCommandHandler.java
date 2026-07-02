package com.lunacode.session;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class SessionCommandHandler {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final SessionService sessionService;
    private final SessionBackedConversationManager conversationManager;

    public SessionCommandHandler(SessionService sessionService, SessionBackedConversationManager conversationManager) {
        this.sessionService = sessionService;
        this.conversationManager = conversationManager;
    }

    public boolean matches(String input) {
        return input != null && (input.equals("/session") || input.startsWith("/session "));
    }

    public CommandResult handle(String input, boolean busy) {
        String stripped = input == null ? "" : input.strip();
        String[] parts = stripped.split("\\s+");
        if (parts.length == 1 || (parts.length == 2 && "current".equalsIgnoreCase(parts[1]))) {
            return CommandResult.info(formatCurrent());
        }
        if (parts.length == 2 && "list".equalsIgnoreCase(parts[1])) {
            return CommandResult.info(formatList(sessionService.listSessions()));
        }
        if (parts.length == 2 && "new".equalsIgnoreCase(parts[1])) {
            if (busy) {
                return CommandResult.warning("当前有任务运行或等待输入，请结束后再新建会话。");
            }
            SessionId id = sessionService.newSession();
            conversationManager.restoreHistory(List.of());
            return CommandResult.info("已创建新会话: " + id.value());
        }
        if (parts.length == 3 && "resume".equalsIgnoreCase(parts[1])) {
            if (busy) {
                return CommandResult.warning("当前有任务运行或等待输入，请结束后再恢复会话。");
            }
            try {
                SessionRecoveryResult result = sessionService.resume(new SessionId(parts[2]));
                conversationManager.restoreHistory(result.messages());
                String suffix = result.warnings().isEmpty() ? "" : "\n" + String.join("\n", result.warnings());
                return CommandResult.info("已恢复会话: " + result.sessionId() + suffix);
            } catch (RuntimeException e) {
                return CommandResult.error("恢复会话失败: " + e.getMessage());
            }
        }
        return CommandResult.error("用法: /session [current|list|resume <id>|new]");
    }

    private String formatCurrent() {
        SessionInfo info = sessionService.currentSession();
        return """
                当前会话: %s
                标题: %s
                消息数: %d
                最后活跃: %s
                """.formatted(
                info.id(),
                info.title(),
                info.messageCount(),
                TIME_FORMAT.format(info.lastActiveAt())
        ).strip();
    }

    private String formatList(List<SessionInfo> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return "暂无历史会话。";
        }
        StringBuilder out = new StringBuilder("历史会话:\n");
        for (SessionInfo info : sessions) {
            out.append("- ")
                    .append(info.id())
                    .append(" | ")
                    .append(info.title())
                    .append(" | 消息 ")
                    .append(info.messageCount())
                    .append(" | ")
                    .append(TIME_FORMAT.format(info.lastActiveAt()));
            if (info.expired()) {
                out.append(" | 已过期");
            }
            out.append('\n');
        }
        return out.toString().stripTrailing();
    }

    public record CommandResult(String state, String message) {
        public static CommandResult info(String message) {
            return new CommandResult("idle", message);
        }

        public static CommandResult warning(String message) {
            return new CommandResult("warning", message);
        }

        public static CommandResult error(String message) {
            return new CommandResult("error", message);
        }
    }
}
