package com.lunacode.tool;

import com.lunacode.config.SandboxConfig;
import com.lunacode.permission.SandboxRoot;

import java.nio.file.Path;
import java.util.List;

public final class DirectCommandSandbox implements CommandSandbox {
    @Override
    public PreparedCommand wrapShellCommand(String command, Path workspaceRoot, List<SandboxRoot> roots, SandboxConfig sandboxConfig) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String shell = System.getenv().getOrDefault("ComSpec", "cmd.exe");
            return PreparedCommand.success(List.of(shell, "/d", "/c", command));
        }
        return PreparedCommand.success(List.of("/bin/sh", "-lc", command));
    }
}
