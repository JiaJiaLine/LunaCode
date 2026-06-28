package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class GlobTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WorkspacePathResolver resolver;
    private final JsonNode schema;

    public GlobTool(WorkspacePathResolver resolver) {
        this.resolver = resolver;
        this.schema = MAPPER.createObjectNode()
                .put("type", "object")
                .set("properties", MAPPER.createObjectNode()
                        .set("pattern", MAPPER.createObjectNode().put("type", "string")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties"))
                .set("limit", MAPPER.createObjectNode().put("type", "integer").put("minimum", 1));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema).set("required", MAPPER.createArrayNode().add("pattern"));
    }

    @Override
    public String name() {
        return "Glob";
    }

    @Override
    public String description() {
        return "按 glob 模式查找工作区文件，支持 ** 递归匹配，返回排序后的相对路径列表。";
    }

    @Override
    public JsonNode inputSchema() {
        return schema;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        try {
            String pattern = input.path("pattern").asText();
            int limit = input.path("limit").asInt(200);
            Pattern regex = Pattern.compile(globToRegex(pattern.replace('\\', '/')));
            List<String> all = Files.walk(context.workspaceRoot())
                    .filter(Files::isRegularFile)
                    .map(resolver::relativize)
                    .filter(path -> regex.matcher(path).matches())
                    .sorted()
                    .toList();
            List<String> selected = all.stream().limit(limit).toList();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("count", all.size());
            metadata.put("truncated", all.size() > selected.size());
            return ToolResult.success(String.join("\n", selected), metadata);
        } catch (Exception e) {
            return ToolResult.error("查找文件失败: " + e.getMessage(), Map.of("errorType", "glob_error"));
        }
    }

    static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                boolean doublestar = i + 1 < glob.length() && glob.charAt(i + 1) == '*';
                if (doublestar) {
                    boolean followedBySlash = i + 2 < glob.length() && glob.charAt(i + 2) == '/';
                    if (followedBySlash) {
                        regex.append("(?:.*/)?");
                        i += 2;
                    } else {
                        regex.append(".*");
                        i++;
                    }
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return regex.append('$').toString();
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
            return new ValidationError("missing_pattern", "Glob 需要 pattern 参数");
        }
        if (input.has("limit") && input.path("limit").asInt(0) < 1) {
            return new ValidationError("invalid_limit", "limit 必须大于 0");
        }
        return null;
    }
}
