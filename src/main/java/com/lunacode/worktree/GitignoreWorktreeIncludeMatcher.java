package com.lunacode.worktree;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class GitignoreWorktreeIncludeMatcher implements WorktreeIncludeMatcher {
    @Override
    public List<Path> match(Path repoRoot, List<Path> ignoredFiles) {
        Path includeFile = repoRoot.toAbsolutePath().normalize().resolve(".worktreeinclude");
        if (!Files.isRegularFile(includeFile) || ignoredFiles == null || ignoredFiles.isEmpty()) {
            return List.of();
        }
        List<Rule> rules = readRules(includeFile);
        if (rules.isEmpty()) {
            return List.of();
        }
        List<Path> matched = new ArrayList<>();
        for (Path ignoredFile : ignoredFiles) {
            String normalized = normalizePath(ignoredFile);
            boolean included = false;
            for (Rule rule : rules) {
                if (rule.matches(normalized)) {
                    included = !rule.negated();
                }
            }
            if (included) {
                matched.add(ignoredFile.normalize());
            }
        }
        return List.copyOf(matched);
    }

    private List<Rule> readRules(Path includeFile) {
        try {
            List<Rule> rules = new ArrayList<>();
            for (String rawLine : Files.readAllLines(includeFile, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                boolean negated = line.startsWith("!");
                if (negated) {
                    line = line.substring(1).trim();
                }
                if (!line.isBlank()) {
                    rules.add(Rule.compile(line, negated));
                }
            }
            return rules;
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String normalizePath(Path path) {
        return path.normalize().toString().replace('\\', '/').replaceAll("/+$", "");
    }

    private record Rule(String pattern, boolean negated, boolean anchored, boolean directory, boolean hasSlash, Pattern regex) {
        static Rule compile(String rawPattern, boolean negated) {
            boolean anchored = rawPattern.startsWith("/");
            if (anchored) {
                rawPattern = rawPattern.substring(1);
            }
            boolean directory = rawPattern.endsWith("/");
            if (directory) {
                rawPattern = rawPattern.substring(0, rawPattern.length() - 1);
            }
            boolean hasSlash = rawPattern.contains("/");
            Pattern regex = Pattern.compile(globToRegex(rawPattern));
            return new Rule(rawPattern, negated, anchored, directory, hasSlash, regex);
        }

        boolean matches(String path) {
            if (directory) {
                return matchesDirectory(path);
            }
            if (anchored) {
                return regex.matcher(path).matches();
            }
            if (hasSlash) {
                return regex.matcher(path).matches() || regex.matcher("/" + path).find();
            }
            for (String segment : path.split("/")) {
                if (regex.matcher(segment).matches()) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchesDirectory(String path) {
            if (anchored || hasSlash) {
                return path.equals(pattern) || path.startsWith(pattern + "/");
            }
            for (String segment : path.split("/")) {
                if (regex.matcher(segment).matches()) {
                    return true;
                }
            }
            return false;
        }

        private static String globToRegex(String pattern) {
            StringBuilder regex = new StringBuilder();
            for (int i = 0; i < pattern.length(); i++) {
                char ch = pattern.charAt(i);
                if (ch == '*') {
                    boolean doublestar = i + 1 < pattern.length() && pattern.charAt(i + 1) == '*';
                    if (doublestar) {
                        regex.append(".*");
                        i++;
                    } else {
                        regex.append("[^/]*");
                    }
                } else if (ch == '?') {
                    regex.append("[^/]");
                } else if (".[]{}()+-^$|\\".indexOf(ch) >= 0) {
                    regex.append('\\').append(ch);
                } else {
                    regex.append(ch);
                }
            }
            return "^" + regex + "$";
        }
    }
}
