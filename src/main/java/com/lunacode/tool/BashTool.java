package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class BashTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final JsonNode schema;

    public BashTool() {
        this.schema = MAPPER.createObjectNode()
                .put("type", "object")
                .set("properties", MAPPER.createObjectNode()
                        .set("command", MAPPER.createObjectNode().put("type", "string")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties"))
                .set("timeout_seconds", MAPPER.createObjectNode().put("type", "integer").put("minimum", 1));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema).set("required", MAPPER.createArrayNode().add("command"));
    }

    @Override
    public String name() {
        return "Bash";
    }

    @Override
    public String description() {
        return "在工作区根目录执行一条非交互式 shell 命令，返回退出码、stdout、stderr 和超时状态。";
    }

    @Override
    public JsonNode inputSchema() {
        return schema;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        String command = input.path("command").asText();
        Duration timeout = input.has("timeout_seconds")
                ? Duration.ofSeconds(input.path("timeout_seconds").asLong())
                : context.commandTimeout();
        long started = System.nanoTime();
        Process process = null;
        try {
            process = new ProcessBuilder(shellCommand(command))
                    .directory(context.workspaceRoot().toFile())
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    .start();
            CompletableFuture<String> stdout = readAsync(process.getInputStream());
            CompletableFuture<String> stderr = readAsync(process.getErrorStream());
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
                String out = stdout.getNow("");
                String err = stderr.getNow("");
                return buildResult(context, true, -1, true, started, out, err);
            }
            return buildResult(context, process.exitValue() != 0, process.exitValue(), false, started, stdout.join(), stderr.join());
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return ToolResult.error("命令执行失败: " + e.getMessage(), Map.of("errorType", "command_error"));
        }
    }

    private List<String> shellCommand(String command) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String shell = System.getenv().getOrDefault("ComSpec", "cmd.exe");
            return List.of(shell, "/d", "/c", command);
        }
        return List.of("/bin/sh", "-lc", command);
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

    private ToolResult buildResult(ToolExecutionContext context, boolean error, int exitCode, boolean timedOut, long started, String stdout, String stderr) {
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
        return new ToolResult(limited, error, metadata);
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean isDestructive() {
        return true;
    }

    @Override
    public boolean isConcurrencySafe(JsonNode input) {
        return false;
    }

    @Override
    public String category() {
        return "shell";
    }

    @Override
    public ValidationError validateInput(JsonNode input) {
        if (input == null || !input.hasNonNull("command") || input.path("command").asText().isBlank()) {
            return new ValidationError("missing_command", "Bash 需要 command 参数");
        }
        if (input.has("timeout_seconds") && input.path("timeout_seconds").asLong(0) < 1) {
            return new ValidationError("invalid_timeout", "timeout_seconds 必须大于 0");
        }
        return null;
    }
}
