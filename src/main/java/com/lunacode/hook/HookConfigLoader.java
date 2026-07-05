package com.lunacode.hook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HookConfigLoader {
    private final ObjectMapper mapper;
    private final HookValidator validator;

    public HookConfigLoader() {
        this(new HookValidator());
    }

    public HookConfigLoader(HookValidator validator) {
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.validator = validator == null ? new HookValidator() : validator;
    }

    public HookConfig load(Path workspaceRoot, Path userHome) {
        List<RawHookDefinition> rawHooks = loadRaw(workspaceRoot, userHome);
        return validator.validate(rawHooks);
    }

    List<RawHookDefinition> loadRaw(Path workspaceRoot, Path userHome) {
        Path root = workspaceRoot == null ? Path.of("").toAbsolutePath().normalize() : workspaceRoot.toAbsolutePath().normalize();
        Path home = userHome == null ? Path.of(System.getProperty("user.home")).toAbsolutePath().normalize() : userHome.toAbsolutePath().normalize();
        List<SourcePath> sources = List.of(
                new SourcePath(HookSourceLevel.PROJECT, root.resolve(".lunacode").resolve("config.yaml")),
                new SourcePath(HookSourceLevel.USER, home.resolve(".lunacode").resolve("config.yaml")),
                new SourcePath(HookSourceLevel.LOCAL, root.resolve(".lunacode").resolve("config.local.yaml"))
        );
        List<RawHookDefinition> hooks = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (SourcePath sourcePath : sources) {
            if (!Files.exists(sourcePath.path())) {
                continue;
            }
            try {
                hooks.addAll(readHooks(sourcePath));
            } catch (IOException | IllegalArgumentException e) {
                errors.add(sourcePath.level().name().toLowerCase() + ":" + sourcePath.path() + " 读取失败: " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new HookConfigException(errors);
        }
        return List.copyOf(hooks);
    }

    private List<RawHookDefinition> readHooks(SourcePath sourcePath) throws IOException {
        JsonNode root = mapper.readTree(sourcePath.path().toFile());
        if (root == null || root.isMissingNode() || root.path("hooks").isMissingNode() || root.path("hooks").isNull()) {
            return List.of();
        }
        JsonNode hooksNode = root.path("hooks");
        if (!hooksNode.isArray()) {
            throw new IllegalArgumentException("hooks 必须是数组");
        }
        List<RawHookDefinition> hooks = new ArrayList<>();
        HookSource source = new HookSource(sourcePath.level(), sourcePath.path());
        int order = 0;
        for (JsonNode node : hooksNode) {
            order++;
            if (!node.isObject()) {
                throw new IllegalArgumentException("hooks[" + order + "] 必须是对象");
            }
            hooks.add(new RawHookDefinition(
                    source,
                    order,
                    text(node, "event"),
                    text(node, "if"),
                    actionMap(node.path("action")),
                    bool(node, "reject"),
                    bool(node, "async"),
                    bool(node, "once"),
                    integer(node, "timeout_ms"),
                    bool(node, "inject_result")
            ));
        }
        return hooks;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> actionMap(JsonNode action) {
        if (action == null || action.isMissingNode() || action.isNull()) {
            return Map.of();
        }
        Map<String, Object> raw = mapper.convertValue(action, Map.class);
        return raw == null ? Map.of() : new LinkedHashMap<>(raw);
    }

    private String text(JsonNode node, String field) {
        return node.has(field) && !node.path(field).isNull() ? node.path(field).asText() : null;
    }

    private Boolean bool(JsonNode node, String field) {
        return node.has(field) && !node.path(field).isNull() ? node.path(field).asBoolean() : null;
    }

    private Integer integer(JsonNode node, String field) {
        return node.has(field) && !node.path(field).isNull() ? node.path(field).asInt() : null;
    }

    private record SourcePath(HookSourceLevel level, Path path) {}
}
