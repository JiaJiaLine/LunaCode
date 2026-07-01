package com.lunacode.context;

import com.lunacode.config.ContextConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public final class RestoredFileContextBuilder {
    public RestoredFileContext build(List<RecentFileAccess> files, ContextConfig config) {
        StringBuilder out = new StringBuilder();
        int restored = 0;
        int limit = config.restoredFileLimit();
        for (RecentFileAccess file : files == null ? List.<RecentFileAccess>of() : files) {
            if (restored >= limit) {
                break;
            }
            restored++;
            out.append("\n--- restored file ").append(restored).append(" ---\n");
            out.append("path: ").append(file.path()).append('\n');
            out.append("tool: ").append(file.toolName()).append('\n');
            try {
                String content = Files.readString(file.path(), StandardCharsets.UTF_8);
                int maxChars = Math.max(0, config.restoredFileTokenLimit() * 4);
                if (content.length() > maxChars) {
                    content = content.substring(0, maxChars) + "\n[文件快照已按恢复预算截断]";
                }
                out.append(content).append('\n');
            } catch (Exception e) {
                out.append("读取失败: ").append(e.getMessage()).append('\n');
            }
        }
        return new RestoredFileContext(out.toString(), restored);
    }

    public record RestoredFileContext(String content, int restoredFiles) {}
}
