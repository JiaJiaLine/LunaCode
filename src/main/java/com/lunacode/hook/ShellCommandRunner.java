package com.lunacode.hook;

import com.lunacode.permission.DangerousCommandBlacklist;
import com.lunacode.tool.CommandSandbox;
import com.lunacode.tool.ReadFileTool;
import com.lunacode.tool.ToolExecutionContext;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ShellCommandRunner {
    private final DangerousCommandBlacklist blacklist = new DangerousCommandBlacklist();

    public HookActionResult run(
            String command,
            Duration timeout,
            Map<String, String> environment,
            ToolExecutionContext context
    ) {
        if (command == null || command.isBlank()) {
            return HookActionResult.failure("命令不能为空", Map.of("errorType", "missing_command"));
        }
        Optional<String> blacklistReason = blacklist.firstMatch(command);
        if (blacklistReason.isPresent()) {
            return HookActionResult.failure("命令被安全黑名单拒绝: " + blacklistReason.get(), Map.of(
                    "errorType", "blacklisted_command",
                    "permissionLayer", "blacklist"
            ));
        }
        ToolExecutionContext safeContext = context == null
                ? new ToolExecutionContext(java.nio.file.Path.of("."), Duration.ofSeconds(30), 20_000, null)
                : context;
        Duration effectiveTimeout = timeout == null ? safeContext.commandTimeout() : timeout;
        long started = System.nanoTime();
        Process process = null;
        try {
            CommandSandbox.PreparedCommand prepared = safeContext.commandSandbox().wrapShellCommand(
                    command,
                    safeContext.workspaceRoot(),
                    safeContext.sandboxRoots(),
                    safeContext.sandboxConfig()
            );
            if (prepared.isError()) {
                return HookActionResult.failure(prepared.error(), Map.of("errorType", "command_sandbox_error"));
            }
            ProcessBuilder builder = new ProcessBuilder(prepared.command())
                    .directory(safeContext.workspaceRoot().toFile())
                    .redirectInput(ProcessBuilder.Redirect.PIPE);
            if (environment != null && !environment.isEmpty()) {
                builder.environment().putAll(environment);
            }
            process = builder.start();
            CompletableFuture<String> stdout = readAsync(process.getInputStream());
            CompletableFuture<String> stderr = readAsync(process.getErrorStream());
            boolean finished = process.waitFor(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
                return buildResult(safeContext, false, -1, true, started, stdout.getNow(""), stderr.getNow(""));
            }
            int exitCode = process.exitValue();
            return buildResult(safeContext, exitCode == 0, exitCode, false, started, stdout.join(), stderr.join());
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return HookActionResult.failure("命令执行失败: " + e.getMessage(), e);
        }
    }

    private CompletableFuture<String> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream in = stream) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        });
    }

    private HookActionResult buildResult(ToolExecutionContext context, boolean success, int exitCode, boolean timedOut, long started, String stdout, String stderr) {
        String maskedOut = context.masker().mask(stdout);
        String maskedErr = context.masker().mask(stderr);
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        String content = "exitCode=" + exitCode + "\n"
                + "timedOut=" + timedOut + "\n"
                + "stdout:\n" + maskedOut + "\n"
                + "stderr:\n" + maskedErr;
        String limited = ReadFileTool.limitContent(content, context.maxContentChars());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("exitCode", exitCode);
        metadata.put("durationMillis", durationMillis);
        metadata.put("timedOut", timedOut);
        metadata.put("stdoutChars", maskedOut.length());
        metadata.put("stderrChars", maskedErr.length());
        metadata.put("truncated", limited.length() < content.length());
        if (timedOut) {
            metadata.put("errorType", "command_timeout");
        } else if (exitCode != 0) {
            metadata.put("errorType", "non_zero_exit");
        }
        return new HookActionResult(success, limited, metadata);
    }
}
