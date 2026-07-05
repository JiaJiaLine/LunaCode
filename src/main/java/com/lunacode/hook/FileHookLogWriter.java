package com.lunacode.hook;

import com.lunacode.tool.SensitiveValueMasker;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

public final class FileHookLogWriter implements HookLogWriter {
    private final Path workspaceRoot;
    private final SensitiveValueMasker masker;

    public FileHookLogWriter(Path workspaceRoot) {
        this(workspaceRoot, new SensitiveValueMasker());
    }

    public FileHookLogWriter(Path workspaceRoot, SensitiveValueMasker masker) {
        this.workspaceRoot = (workspaceRoot == null ? Path.of("") : workspaceRoot).toAbsolutePath().normalize();
        this.masker = masker == null ? new SensitiveValueMasker() : masker;
    }

    @Override
    public void log(String sessionId, HookLogEntry entry) {
        if (entry == null) {
            return;
        }
        try {
            Path dir = workspaceRoot.resolve(".lunacode").resolve("tmp").resolve("hooks");
            Files.createDirectories(dir);
            Path file = dir.resolve(safeSessionId(sessionId) + ".log");
            Files.writeString(file, line(entry), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // Hook 日志失败不能影响主流程。
        }
    }

    private String line(HookLogEntry entry) {
        return DateTimeFormatter.ISO_INSTANT.format(entry.timestamp())
                + " hookId=" + entry.hookId()
                + " event=" + entry.eventName()
                + " action=" + entry.actionType()
                + " status=" + entry.status()
                + " durationMs=" + entry.durationMillis()
                + " output=\"" + compact(masker.mask(entry.output())) + "\""
                + " error=\"" + compact(masker.mask(entry.error())) + "\""
                + " metadata=" + entry.metadata()
                + System.lineSeparator();
    }

    private String compact(String value) {
        String oneLine = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').strip();
        return oneLine.length() <= 500 ? oneLine : oneLine.substring(0, 500) + "...";
    }

    private String safeSessionId(String sessionId) {
        String value = sessionId == null || sessionId.isBlank() ? "unknown-session" : sessionId;
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
