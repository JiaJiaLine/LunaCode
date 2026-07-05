package com.lunacode.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class FrontmatterSkillParser implements SkillParser {
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Override
    public SkillParseResult parseSingleFile(Path markdownFile, SkillOrigin origin) {
        try {
            return parseContent(Files.readString(markdownFile), origin, Optional.empty());
        } catch (IOException e) {
            return failure(origin, "无法读取 Skill 文件: " + e.getMessage());
        }
    }

    @Override
    public SkillParseResult parseDirectory(Path skillDirectory, SkillOrigin origin) {
        Path entry = skillDirectory.resolve("SKILL.md");
        try {
            return parseContent(Files.readString(entry), origin, Optional.of(skillDirectory));
        } catch (IOException e) {
            return failure(origin, "无法读取目录型 Skill 入口: " + e.getMessage());
        }
    }

    @Override
    public SkillParseResult parseBuiltin(String resourceName, String content, SkillOrigin origin) {
        return parseContent(content, origin, Optional.empty());
    }

    private SkillParseResult parseContent(String content, SkillOrigin origin, Optional<Path> resourceRoot) {
        try {
            Frontmatter frontmatter = split(content == null ? "" : content);
            JsonNode root = yamlMapper.readTree(frontmatter.yaml());
            if (root == null || !root.isObject()) {
                return failure(origin, "frontmatter 必须是 YAML 对象");
            }
            String name = requiredText(root, "name");
            String description = requiredText(root, "description");
            if (!NAME_PATTERN.matcher(name).matches()) {
                return failure(origin, "Skill name 只能包含小写字母、数字和连字符: " + name);
            }
            SkillExecutionMode mode;
            SkillContextPolicy context;
            try {
                mode = SkillExecutionMode.fromFrontmatter(optionalText(root, "mode").orElse(null));
                context = SkillContextPolicy.fromFrontmatter(optionalText(root, "context").orElse(null));
            } catch (IllegalArgumentException e) {
                return failure(origin, e.getMessage());
            }
            List<String> tools = parseTools(root.path("tools"));
            SkillDefinition definition = new SkillDefinition(
                    name,
                    description,
                    mode,
                    context,
                    optionalText(root, "model"),
                    tools,
                    frontmatter.body(),
                    origin,
                    resourceRoot
            );
            return new SkillParseResult.Success(definition);
        } catch (IllegalArgumentException e) {
            return failure(origin, e.getMessage());
        } catch (IOException e) {
            return failure(origin, "frontmatter YAML 解析失败: " + e.getMessage());
        }
    }

    private Frontmatter split(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.startsWith("---\n")) {
            throw new IllegalArgumentException("Skill 文件必须以 YAML frontmatter 开头");
        }
        int end = normalized.indexOf("\n---", 4);
        if (end < 0) {
            throw new IllegalArgumentException("Skill frontmatter 缺少结束分隔符");
        }
        int bodyStart = end + "\n---".length();
        if (bodyStart < normalized.length() && normalized.charAt(bodyStart) == '\n') {
            bodyStart++;
        }
        String yaml = normalized.substring(4, end);
        String body = bodyStart >= normalized.length() ? "" : normalized.substring(bodyStart);
        return new Frontmatter(yaml, body);
    }

    private String requiredText(JsonNode root, String field) {
        Optional<String> value = optionalText(root, field);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Skill frontmatter 缺少必填字段: " + field);
        }
        return value.get();
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

    private List<String> parseTools(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("tools 必须是字符串列表");
        }
        List<String> tools = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new IllegalArgumentException("tools 只能包含字符串");
            }
            String value = item.asText().strip();
            if (!value.isBlank()) {
                tools.add(value);
            }
        }
        return List.copyOf(tools);
    }

    private SkillParseResult failure(SkillOrigin origin, String reason) {
        return new SkillParseResult.Failure(origin, reason);
    }

    private record Frontmatter(String yaml, String body) {
    }
}
