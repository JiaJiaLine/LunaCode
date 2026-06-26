package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public class GrepTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WorkspacePathResolver resolver;
    private final JsonNode schema;

    public GrepTool(WorkspacePathResolver resolver) {
        this.resolver = resolver;
        this.schema = MAPPER.createObjectNode()
                .put("type", "object")
                .set("properties", MAPPER.createObjectNode()
                        .set("pattern", MAPPER.createObjectNode().put("type", "string")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties")).set("path", MAPPER.createObjectNode().put("type", "string"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties")).set("limit", MAPPER.createObjectNode().put("type", "integer").put("minimum", 1));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema).set("required", MAPPER.createArrayNode().add("pattern"));
    }

    @Override
    public String name() {
        return "Grep";
    }

    @Override
    public String description() {
        return "在工作区内搜索文本内容，返回文件路径、行号、列号和匹配行摘要。";
    }

    @Override
    public JsonNode inputSchema() {
        return schema;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        try {
            Pattern pattern = compilePattern(input.path("pattern").asText());
            Path searchRoot = input.hasNonNull("path")
                    ? resolver.resolveInsideWorkspace(input.path("path").asText())
                    : context.workspaceRoot();
            int limit = input.path("limit").asInt(100);
            StringBuilder content = new StringBuilder();
            int scanned = 0;
            int matches = 0;
            try (Stream<Path> stream = Files.walk(searchRoot)) {
                List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
                for (Path file : files) {
                    if (shouldSkip(context.workspaceRoot(), file) || isBinary(file)) {
                        continue;
                    }
                    scanned++;
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        Matcher matcher = pattern.matcher(lines.get(i));
                        if (matcher.find()) {
                            matches++;
                            if (matches <= limit) {
                                content.append(resolver.relativize(file))
                                        .append(':').append(i + 1)
                                        .append(':').append(matcher.start() + 1)
                                        .append('\t').append(lines.get(i).strip())
                                        .append('\n');
                            }
                        }
                    }
                }
            }
            String limited = ReadFileTool.limitContent(context.masker().mask(content.toString()), context.maxContentChars());
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("matchCount", matches);
            metadata.put("scannedFiles", scanned);
            metadata.put("truncated", matches > limit || limited.length() < content.length());
            return ToolResult.success(limited, metadata);
        } catch (Exception e) {
            return ToolResult.error("搜索代码失败: " + e.getMessage(), Map.of("errorType", "grep_error"));
        }
    }

    private Pattern compilePattern(String value) {
        try {
            return Pattern.compile(value);
        } catch (PatternSyntaxException e) {
            return Pattern.compile(Pattern.quote(value));
        }
    }

    private boolean shouldSkip(Path root, Path file) {
        String rel = root.relativize(file).toString().replace('\\', '/');
        return rel.startsWith(".git/") || rel.startsWith("target/");
    }

    private boolean isBinary(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            int max = Math.min(bytes.length, 1024);
            for (int i = 0; i < max; i++) {
                if (bytes[i] == 0) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isDestructive() {
        return false;
    }

    @Override
    public boolean isConcurrencySafe(JsonNode input) {
        return true;
    }

    @Override
    public String category() {
        return "search";
    }

    @Override
    public ValidationError validateInput(JsonNode input) {
        if (input == null || !input.hasNonNull("pattern") || input.path("pattern").asText().isBlank()) {
            return new ValidationError("missing_pattern", "Grep 需要 pattern 参数");
        }
        if (input.has("limit") && input.path("limit").asInt(0) < 1) {
            return new ValidationError("invalid_limit", "limit 必须大于 0");
        }
        return null;
    }
}
