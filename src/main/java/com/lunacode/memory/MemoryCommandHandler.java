package com.lunacode.memory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class MemoryCommandHandler {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final MemoryStore store;
    private final MemoryContextLoader contextLoader;
    private final MemoryRuntimeState runtimeState;

    public MemoryCommandHandler(MemoryStore store, MemoryContextLoader contextLoader, MemoryRuntimeState runtimeState) {
        this.store = store;
        this.contextLoader = contextLoader;
        this.runtimeState = runtimeState;
    }

    public boolean matches(String input) {
        return input != null && (input.equals("/memory") || input.startsWith("/memory "));
    }

    public CommandResult handle(String input) {
        String stripped = input == null ? "" : input.strip();
        String[] parts = stripped.split("\\s+");
        if (parts.length == 1) {
            MemoryIndexSnapshot snapshot = contextLoader.loadForPrompt();
            return CommandResult.info("自动记忆: " + onOff() + "\n最近状态: " + runtimeState.latestState() + "\n" + snapshot.mergedContent());
        }
        if (parts.length == 2 && "list".equalsIgnoreCase(parts[1])) {
            return CommandResult.info(formatList(store.listAll()));
        }
        if (parts.length == 2 && "on".equalsIgnoreCase(parts[1])) {
            runtimeState.setAutoUpdateEnabled(true);
            runtimeState.setLatestState("on");
            return CommandResult.info("自动记忆已开启。");
        }
        if (parts.length == 2 && "off".equalsIgnoreCase(parts[1])) {
            runtimeState.setAutoUpdateEnabled(false);
            runtimeState.setLatestState("off");
            return CommandResult.info("自动记忆已关闭。");
        }
        if (parts.length == 3 && "delete".equalsIgnoreCase(parts[1])) {
            boolean deleted = store.delete(parts[2]);
            store.rebuildIndexes();
            return deleted ? CommandResult.info("记忆已删除: " + parts[2]) : CommandResult.warning("未找到记忆: " + parts[2]);
        }
        return CommandResult.error("用法: /memory [list|delete <id>|on|off]");
    }

    private String formatList(List<MemoryNote> notes) {
        if (notes == null || notes.isEmpty()) {
            return "暂无记忆。";
        }
        StringBuilder out = new StringBuilder("记忆列表:\n");
        for (MemoryNote note : notes) {
            out.append("- ")
                    .append(note.id())
                    .append(" | ")
                    .append(note.type().value())
                    .append(" | ")
                    .append(note.title())
                    .append(" | ")
                    .append(TIME_FORMAT.format(note.updatedAt()))
                    .append('\n');
        }
        return out.toString().stripTrailing();
    }

    private String onOff() {
        return runtimeState.autoUpdateEnabled() ? "on" : "off";
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
