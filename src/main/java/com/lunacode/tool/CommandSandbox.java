package com.lunacode.tool;

import com.lunacode.config.SandboxConfig;
import com.lunacode.permission.SandboxRoot;

import java.nio.file.Path;
import java.util.List;

public interface CommandSandbox {
    PreparedCommand wrapShellCommand(String command, Path workspaceRoot, List<SandboxRoot> roots, SandboxConfig sandboxConfig);

    record PreparedCommand(List<String> command, String error) {
        public PreparedCommand {
            command = command == null ? List.of() : List.copyOf(command);
            error = error == null ? "" : error;
        }

        public static PreparedCommand success(List<String> command) {
            return new PreparedCommand(command, "");
        }

        public static PreparedCommand error(String error) {
            return new PreparedCommand(List.of(), error);
        }

        public boolean isError() {
            return !error.isBlank();
        }
    }
}
