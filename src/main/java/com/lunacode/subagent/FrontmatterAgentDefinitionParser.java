package com.lunacode.subagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.lunacode.permission.PermissionMode;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class FrontmatterAgentDefinitionParser implements AgentDefinitionParser {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Override
    public AgentDefinitionParseResult parse(AgentDefinitionCandidate candidate) {
        try {
            String content = candidate.content().orElseGet(() -> read(candidate));
            Frontmatter frontmatter = split(content);
            JsonNode root = yamlMapper.readTree(frontmatter.yaml());
            if (root == null || !root.isObject()) {
                return failure(candidate, "frontmatter 必须是 YAML 对象");
            }
            String name = requiredText(root, "name");
            String description = requiredText(root, "description");
            AgentDefinition definition = new AgentDefinition(
                    name,
                    description,
                    parseStringList(root.path("tools"), "tools"),
                    parseStringList(root.path("disallowedTools"), "disallowedTools"),
                    optionalText(root, "model").orElse("inherit"),
                    parseMaxTurns(root.path("maxTurns")),
                    parsePermissionMode(root.path("permissionMode")),
                    parseBoolean(root.path("background"), "background"),
                    frontmatter.body(),
                    candidate.path().orElse(java.nio.file.Path.of(candidate.sourceId().isBlank() ? name + ".md" : candidate.sourceId())),
                    candidate.source()
            );
            return new AgentDefinitionParseResult.Success(definition);
        } catch (IllegalArgumentException e) {
            return failure(candidate, e.getMessage());
        } catch (IOException e) {
            return failure(candidate, "frontmatter YAML 解析失败: " + e.getMessage());
        }
    }

    private String read(AgentDefinitionCandidate candidate) {
        try {
            return Files.readString(candidate.path().orElseThrow());
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取 Agent 定义文件: " + e.getMessage());
        }
    }

    private Frontmatter split(String content) {
        String normalized = (content == null ? "" : content).replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.startsWith("---\n")) {
            throw new IllegalArgumentException("Agent 定义文件必须以 YAML frontmatter 开头");
        }
        int end = normalized.indexOf("\n---", 4);
        if (end < 0) {
            throw new IllegalArgumentException("Agent frontmatter 缺少结束分隔符");
        }
        int bodyStart = end + "\n---".length();
        if (bodyStart < normalized.length() && normalized.charAt(bodyStart) == '\n') {
            bodyStart++;
        }
        return new Frontmatter(normalized.substring(4, end), bodyStart >= normalized.length() ? "" : normalized.substring(bodyStart));
    }

    private String requiredText(JsonNode root, String field) {
        return optionalText(root, field).orElseThrow(() -> new IllegalArgumentException("Agent frontmatter 缺少必填字段: " + field));
    }

    private Optional<String> optionalText(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("字段必须是字符串: " + field);
        }
        String value = node.asText().strip();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private List<String> parseStringList(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException(field + " 必须是字符串列表");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new IllegalArgumentException(field + " 只能包含字符串");
            }
            String value = item.asText().strip();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private OptionalInt parseMaxTurns(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return OptionalInt.empty();
        }
        if (!node.canConvertToInt()) {
            throw new IllegalArgumentException("maxTurns 必须是正整数");
        }
        int value = node.asInt();
        if (value <= 0) {
            throw new IllegalArgumentException("maxTurns 必须大于 0");
        }
        return OptionalInt.of(value);
    }

    private boolean parseBoolean(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (!node.isBoolean()) {
            throw new IllegalArgumentException(field + " 必须是布尔值");
        }
        return node.asBoolean();
    }
    private Optional<PermissionMode> parsePermissionMode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("permissionMode 必须是字符串");
        }
        String value = node.asText().strip();
        if (value.isBlank()) {
            return Optional.empty();
        }
        if (value.equalsIgnoreCase("dontAsk") || value.equalsIgnoreCase("don'tAsk") || value.equalsIgnoreCase("noAsk")) {
            return Optional.of(PermissionMode.DEFAULT);
        }
        try {
            return Optional.of(PermissionMode.fromConfig(value));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("permissionMode 无效: " + value);
        }
    }

    private AgentDefinitionParseResult failure(AgentDefinitionCandidate candidate, String reason) {
        return new AgentDefinitionParseResult.Failure(candidate, reason);
    }

    private record Frontmatter(String yaml, String body) {}
}
