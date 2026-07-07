package com.lunacode.prompt;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record EnvironmentContext(Path workDir, String osName, Instant now, GitStatusSnapshot gitStatus) {
    public EnvironmentContext {
        workDir = Objects.requireNonNull(workDir, "workDir").toAbsolutePath().normalize();
        osName = osName == null || osName.isBlank() ? "unknown" : osName;
        now = Objects.requireNonNull(now, "now");
        gitStatus = gitStatus == null ? GitStatusSnapshot.unknown("unknown") : gitStatus;
    }

    public String render() {
        String worktreeLine = worktreeSummary()
                .map(summary -> "Worktree: " + summary + "\n")
                .orElse("");
        return """
                # 环境上下文
                当前工作目录：%s
                %s操作系统：%s
                当前时间：%s
                Git 状态：%s
                """.formatted(workDir, worktreeLine, osName, now, gitStatus.render()).strip();
    }

    private Optional<String> worktreeSummary() {
        String normalized = workDir.toString().replace('\\', '/');
        String marker = "/.lunacode/worktrees/";
        int index = normalized.indexOf(marker);
        if (index < 0) {
            return Optional.empty();
        }
        String name = normalized.substring(index + marker.length());
        if (name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(name + " (所有工具调用默认在该隔离目录执行)");
    }
}