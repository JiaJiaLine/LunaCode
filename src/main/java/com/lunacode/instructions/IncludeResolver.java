package com.lunacode.instructions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IncludeResolver {
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("@include\\s+(.+)$");
    private static final int MAX_DEPTH = 5;

    public String expand(Path sourceFile, IncludeBoundary boundary) {
        return expand(sourceFile, boundary, MAX_DEPTH, new HashSet<>());
    }

    public String expand(Path sourceFile, IncludeBoundary boundary, int depth, Set<Path> visited) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(boundary, "boundary");
        Set<Path> safeVisited = visited == null ? new HashSet<>() : visited;
        Path source = normalizeExisting(sourceFile);
        if (!within(source, boundary.root())) {
            return warning("include 已跳过，来源文件越过允许根: " + source);
        }
        if (safeVisited.contains(source)) {
            return warning("include 已跳过，检测到重复路径: " + source);
        }
        if (depth < 0) {
            return warning("include 已跳过，超过最大嵌套深度 5: " + source);
        }
        if (!Files.exists(source)) {
            return warning("include 已跳过，文件不存在: " + source);
        }
        safeVisited.add(source);
        try {
            StringBuilder out = new StringBuilder();
            for (String line : Files.readAllLines(source)) {
                Matcher matcher = INCLUDE_PATTERN.matcher(line);
                if (!matcher.find()) {
                    out.append(line).append(System.lineSeparator());
                    continue;
                }
                String includePath = cleanPath(matcher.group(1));
                out.append(expandInclude(source, includePath, boundary, depth - 1, safeVisited))
                        .append(System.lineSeparator());
            }
            return out.toString().stripTrailing();
        } catch (IOException e) {
            return warning("include 已跳过，读取失败: " + source + "，原因: " + e.getMessage());
        } finally {
            safeVisited.remove(source);
        }
    }

    private String expandInclude(Path sourceFile, String includePath, IncludeBoundary boundary, int depth, Set<Path> visited) {
        if (includePath.isBlank()) {
            return warning("include 已跳过，路径为空");
        }
        if (includePath.contains("*") || includePath.contains("?")) {
            return warning("include 已跳过，不支持 glob: " + includePath);
        }
        Path raw = Path.of(includePath);
        Path target = raw.isAbsolute() ? raw : sourceFile.getParent().resolve(raw);
        target = normalizeExisting(target);
        if (!within(target, boundary.root())) {
            return warning("include 已跳过，路径越过允许根 " + boundary.description() + ": " + target);
        }
        return expand(target, boundary, depth, visited);
    }

    private String cleanPath(String raw) {
        String value = raw == null ? "" : raw.strip();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1).strip();
        }
        return value;
    }

    private Path normalizeExisting(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        try {
            if (Files.exists(normalized)) {
                return normalized.toRealPath().normalize();
            }
        } catch (IOException ignored) {
            // Fall back to lexical normalization and let the caller report a warning.
        }
        return normalized;
    }

    private boolean within(Path path, Path root) {
        Path normalizedRoot = normalizeExisting(root);
        return path.normalize().startsWith(normalizedRoot);
    }

    private String warning(String message) {
        return "<!-- " + message + " -->";
    }
}
