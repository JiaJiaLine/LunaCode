package com.lunacode.permission;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class YamlPermissionRuleStore implements PermissionRuleStore {
    private static final TypeReference<List<RawRule>> RULE_LIST = new TypeReference<>() {};

    private final Path workspaceRoot;
    private final Path userHome;
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final PermissionRuleParser parser = new PermissionRuleParser();

    public YamlPermissionRuleStore(Path workspaceRoot) {
        this(workspaceRoot, Path.of(System.getProperty("user.home", ".")));
    }

    public YamlPermissionRuleStore(Path workspaceRoot, Path userHome) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        this.userHome = Objects.requireNonNull(userHome, "userHome").toAbsolutePath().normalize();
    }

    @Override
    public LoadedPermissionRules load() {
        List<String> warnings = new ArrayList<>();
        List<PermissionRule> user = loadLayer(userPath(), PermissionRuleLevel.USER, warnings);
        List<PermissionRule> project = loadLayer(projectPath(), PermissionRuleLevel.PROJECT, warnings);
        List<PermissionRule> local = loadLayer(localPath(), PermissionRuleLevel.LOCAL, warnings);
        return new LoadedPermissionRules(user, project, local, warnings);
    }

    @Override
    public AppendResult appendLocalAllow(String rule) {
        Path local = localPath();
        try {
            if (Files.exists(local)) {
                List<String> warnings = new ArrayList<>();
                loadLayer(local, PermissionRuleLevel.LOCAL, warnings);
                if (!warnings.isEmpty()) {
                    return AppendResult.failure("本地权限规则文件损坏，请先修复: " + local);
                }
            } else {
                Files.createDirectories(local.getParent());
            }
            String prefix = Files.exists(local) && Files.size(local) > 0 ? System.lineSeparator() : "";
            String yaml = prefix
                    + "- rule: \"" + escapeYaml(rule) + "\"" + System.lineSeparator()
                    + "  effect: allow" + System.lineSeparator();
            Files.writeString(local, yaml, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return AppendResult.ok();
        } catch (Exception e) {
            return AppendResult.failure(e.getMessage());
        }
    }

    public Path userPath() {
        return userHome.resolve(".lunacode").resolve("permissions.yaml");
    }

    public Path projectPath() {
        return workspaceRoot.resolve(".lunacode").resolve("permissions.yaml");
    }

    public Path localPath() {
        return workspaceRoot.resolve(".lunacode").resolve("permissions.local.yaml");
    }

    private List<PermissionRule> loadLayer(Path path, PermissionRuleLevel level, List<String> warnings) {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            List<RawRule> rawRules = mapper.readValue(path.toFile(), RULE_LIST);
            if (rawRules == null) {
                return List.of();
            }
            List<PermissionRule> rules = new ArrayList<>();
            int order = 0;
            for (RawRule rawRule : rawRules) {
                order++;
                rules.add(parser.parse(rawRule.rule(), rawRule.effect(), level, order, path));
            }
            return List.copyOf(rules);
        } catch (IOException | RuntimeException e) {
            warnings.add(level + " 权限规则加载失败，已忽略该层: " + path + " (" + e.getMessage() + ")");
            return List.of();
        }
    }

    private String escapeYaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record RawRule(
            String rule,
            @JsonProperty("effect") String effect
    ) {}
}
