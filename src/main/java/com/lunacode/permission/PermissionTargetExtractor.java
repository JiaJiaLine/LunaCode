package com.lunacode.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.lunacode.config.SandboxConfig;
import com.lunacode.tool.ToolUse;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PermissionTargetExtractor {
    private final PathSandbox pathSandbox;
    private final BashPathScanner bashPathScanner;
    private final BashNetworkAccessScanner networkAccessScanner;
    private final SensitivePathPolicy sensitivePathPolicy;
    private final SandboxConfig sandboxConfig;

    public PermissionTargetExtractor(PathSandbox pathSandbox, BashPathScanner bashPathScanner, SensitivePathPolicy sensitivePathPolicy) {
        this(pathSandbox, bashPathScanner, sensitivePathPolicy, SandboxConfig.defaults());
    }

    public PermissionTargetExtractor(PathSandbox pathSandbox, BashPathScanner bashPathScanner, SensitivePathPolicy sensitivePathPolicy, SandboxConfig sandboxConfig) {
        this.pathSandbox = pathSandbox;
        this.bashPathScanner = bashPathScanner == null ? new BashPathScanner() : bashPathScanner;
        this.networkAccessScanner = new BashNetworkAccessScanner();
        this.sensitivePathPolicy = sensitivePathPolicy == null ? new SensitivePathPolicy() : sensitivePathPolicy;
        this.sandboxConfig = sandboxConfig == null ? SandboxConfig.defaults() : sandboxConfig;
    }

    public ExtractionResult extract(ToolUse toolUse, Path workDir) {
        if (workDir == null) {
            return extract(toolUse);
        }
        PathSandbox scopedSandbox = new DefaultPathSandbox(workDir, sandboxConfig);
        return new PermissionTargetExtractor(scopedSandbox, bashPathScanner, sensitivePathPolicy, sandboxConfig).extract(toolUse);
    }

    public ExtractionResult extract(ToolUse toolUse) {
        if (toolUse == null) {
            return new ExtractionResult(List.of(), List.of("工具调用为空"), List.of(), false);
        }
        String toolName = toolUse.name();
        JsonNode input = toolUse.input();
        List<PermissionTarget> targets = new ArrayList<>();
        List<String> sandboxErrors = new ArrayList<>();
        List<String> networkErrors = new ArrayList<>();
        boolean[] sensitive = new boolean[]{false};
        switch (toolName) {
            case "Bash" -> {
                String command = text(input, "command");
                targets.add(PermissionTarget.command(toolName, command));
                if (!sandboxConfig.networkEnabled()) {
                    Optional<String> networkError = networkAccessScanner.firstNetworkAccess(command, pathSandbox);
                    networkError.ifPresent(networkErrors::add);
                }
                for (BashPathScanner.ScannedPath path : bashPathScanner.scan(command, pathSandbox)) {
                    if (path.result().allowed()) {
                        addPathTarget(targets, toolName, path.result().path(), sensitive);
                    } else {
                        sandboxErrors.add(path.result().reason());
                    }
                }
            }
            case "ReadFile" -> addValidatedPath(targets, sandboxErrors, toolName, text(input, "path", "file_path"), PathIntent.READ, sensitive);
            case "WriteFile", "EditFile" -> addValidatedPath(targets, sandboxErrors, toolName, text(input, "path", "file_path"), PathIntent.WRITE, sensitive);
            case "Glob" -> {
                String pattern = text(input, "pattern");
                targets.add(PermissionTarget.pattern(toolName, pattern));
                if (patternLooksLikePath(pattern)) {
                    addValidatedPath(targets, sandboxErrors, toolName, leadingPath(pattern), PathIntent.GLOB, sensitive);
                }
            }
            case "Grep" -> {
                targets.add(PermissionTarget.pattern(toolName, text(input, "pattern")));
                if (input != null && input.hasNonNull("path")) {
                    addValidatedPath(targets, sandboxErrors, toolName, text(input, "path"), PathIntent.READ, sensitive);
                }
            }
            default -> targets.add(PermissionTarget.pattern(toolName, input == null ? "{}" : input.toString()));
        }
        return new ExtractionResult(targets, sandboxErrors, networkErrors, sensitive[0]);
    }

    private void addValidatedPath(
            List<PermissionTarget> targets,
            List<String> sandboxErrors,
            String toolName,
            String requestedPath,
            PathIntent intent,
            boolean[] sensitive
    ) {
        PathSandbox.Result result = pathSandbox.validate(requestedPath, intent);
        if (result.allowed()) {
            addPathTarget(targets, toolName, result.path(), sensitive);
        } else {
            sandboxErrors.add(result.reason());
        }
    }

    private void addPathTarget(List<PermissionTarget> targets, String toolName, VirtualPath path, boolean[] sensitive) {
        boolean isSensitive = sensitivePathPolicy.isSensitive(path);
        sensitive[0] = sensitive[0] || isSensitive;
        targets.add(PermissionTarget.path(toolName, path, isSensitive));
    }

    private String text(JsonNode input, String... names) {
        if (input == null || names == null) {
            return "";
        }
        for (String name : names) {
            if (input.hasNonNull(name)) {
                return input.path(name).asText();
            }
        }
        return "";
    }

    private boolean patternLooksLikePath(String pattern) {
        if (pattern == null) {
            return false;
        }
        return pattern.startsWith("/")
                || pattern.startsWith("../")
                || pattern.startsWith("./")
                || pattern.contains("\\")
                || pattern.contains("/../");
    }

    private String leadingPath(String pattern) {
        int wildcard = firstWildcard(pattern);
        if (wildcard < 0) {
            return pattern;
        }
        int slash = Math.max(pattern.lastIndexOf('/', wildcard), pattern.lastIndexOf('\\', wildcard));
        return slash <= 0 ? "." : pattern.substring(0, slash);
    }

    private int firstWildcard(String value) {
        int star = value.indexOf('*');
        int question = value.indexOf('?');
        if (star < 0) {
            return question;
        }
        if (question < 0) {
            return star;
        }
        return Math.min(star, question);
    }

    public record ExtractionResult(List<PermissionTarget> targets, List<String> sandboxErrors, List<String> networkErrors, boolean containsSensitivePath) {
        public ExtractionResult {
            targets = targets == null ? List.of() : List.copyOf(targets);
            sandboxErrors = sandboxErrors == null ? List.of() : List.copyOf(sandboxErrors);
            networkErrors = networkErrors == null ? List.of() : List.copyOf(networkErrors);
        }
    }
}
