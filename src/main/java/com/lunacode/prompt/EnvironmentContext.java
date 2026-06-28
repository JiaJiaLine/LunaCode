package com.lunacode.prompt;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public record EnvironmentContext(Path workDir, String osName, Instant now, GitStatusSnapshot gitStatus) {
    public EnvironmentContext {
        workDir = Objects.requireNonNull(workDir, "workDir").toAbsolutePath().normalize();
        osName = osName == null || osName.isBlank() ? "unknown" : osName;
        now = Objects.requireNonNull(now, "now");
        gitStatus = gitStatus == null ? GitStatusSnapshot.unknown("unknown") : gitStatus;
    }

    public String render() {
        return """
                # 环境上下文
                当前工作目录：%s
                操作系统：%s
                当前时间：%s
                Git 状态：%s
                """.formatted(workDir, osName, now, gitStatus.render()).strip();
    }
}
