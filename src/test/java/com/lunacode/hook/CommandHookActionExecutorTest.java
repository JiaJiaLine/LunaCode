package com.lunacode.hook;

import com.lunacode.tool.SensitiveValueMasker;
import com.lunacode.tool.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CommandHookActionExecutorTest {
    @TempDir
    Path tempDir;

    @Test
    void injectsHookContextEnvironmentVariables() {
        CommandHookActionExecutor executor = new CommandHookActionExecutor(new ShellCommandRunner(), new ToolExecutionContext(tempDir, Duration.ofSeconds(3), 10_000, new SensitiveValueMasker()));
        HookDefinition hook = new HookDefinition("h1", new HookSource(HookSourceLevel.PROJECT, tempDir.resolve("config.yaml")), 1, HookEventName.POST_TOOL_USE, Optional.empty(), new HookAction.Command(envCommand()), false, false, false, Optional.empty(), false);
        HookContext context = new HookContext("post_tool_use", "WriteFile", Map.of("path", "a.txt"), "a.txt", "", "");

        HookActionResult result = executor.execute(hook, context, new HookExecutionScope("s1", 1, tempDir));

        assertTrue(result.success(), result.output());
        assertTrue(result.output().contains("post_tool_use"));
        assertTrue(result.output().contains("WriteFile"));
        assertTrue(result.output().contains("a.txt"));
    }

    private String envCommand() {
        return isWindows()
                ? "echo %EVENT_NAME% %TOOL_NAME% %ARGS_PATH%"
                : "printf '%s %s %s' \"$EVENT_NAME\" \"$TOOL_NAME\" \"$ARGS_PATH\"";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
