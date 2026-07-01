package com.lunacode.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.lunacode.tool.ToolExecutionRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecentFileAccessTracker {
    private final Map<Path, RecentFileAccess> accesses = new LinkedHashMap<>();

    public synchronized void record(List<ToolExecutionRecord> records, Path workspaceRoot) {
        Path root = (workspaceRoot == null ? Path.of("") : workspaceRoot).toAbsolutePath().normalize();
        for (ToolExecutionRecord record : records == null ? List.<ToolExecutionRecord>of() : records) {
            if (record == null || record.result() == null || record.result().isError()) {
                continue;
            }
            String toolName = record.toolUse() == null ? "" : record.toolUse().name();
            if (!toolName.equals("ReadFile") && !toolName.equals("WriteFile") && !toolName.equals("EditFile")) {
                continue;
            }
            Path path = extractPath(record, root);
            if (path == null || !path.normalize().startsWith(root)) {
                continue;
            }
            long size = 0;
            try {
                size = Files.exists(path) ? Files.size(path) : 0;
            } catch (Exception ignored) {
                size = 0;
            }
            accesses.put(path, new RecentFileAccess(path, toolName, Instant.now(), size));
        }
    }

    public synchronized List<RecentFileAccess> recentFiles(int limit) {
        return accesses.values().stream()
                .sorted(Comparator.comparing(RecentFileAccess::accessedAt).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    private Path extractPath(ToolExecutionRecord record, Path root) {
        Object metadataPath = record.result().metadata().get("path");
        String pathText = metadataPath == null ? null : metadataPath.toString();
        if ((pathText == null || pathText.isBlank()) && record.toolUse() != null) {
            JsonNode input = record.toolUse().input();
            if (input != null) {
                pathText = text(input, "path", "file_path");
            }
        }
        if (pathText == null || pathText.isBlank()) {
            return null;
        }
        Path path = Path.of(pathText);
        return path.isAbsolute() ? path.toAbsolutePath().normalize() : root.resolve(path).normalize();
    }

    private String text(JsonNode input, String... names) {
        for (String name : names) {
            if (input.hasNonNull(name) && !input.path(name).asText().isBlank()) {
                return input.path(name).asText();
            }
        }
        return null;
    }
}
