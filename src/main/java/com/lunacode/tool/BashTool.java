package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.hook.HookActionResult;
import com.lunacode.hook.ShellCommandRunner;

import java.time.Duration;
import java.util.Map;

public class BashTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ShellCommandRunner commandRunner = new ShellCommandRunner();
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
        HookActionResult result = commandRunner.run(command, timeout, Map.of(), context);
        return new ToolResult(result.output(), !result.success(), result.metadata());
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