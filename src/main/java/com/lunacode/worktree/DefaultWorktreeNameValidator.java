package com.lunacode.worktree;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class DefaultWorktreeNameValidator implements WorktreeNameValidator {
    public static final String BRANCH_PREFIX = "worktree-";
    public static final int MAX_NAME_LENGTH = 120;

    private static final Pattern ALLOWED_CHARS = Pattern.compile("[A-Za-z0-9._/-]+");
    private static final Pattern AGENT_NAME = Pattern.compile("agent-a[0-9a-f]{6,7}");
    private static final Pattern WORKFLOW_NAME = Pattern.compile("wf_[0-9a-f]{12}");
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    @Override
    public ValidWorktreeName validate(String name, WorktreeKind kind) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("worktree name must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("worktree kind must not be null");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("worktree name is too long: " + name.length());
        }
        if (name.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("worktree name must not contain backslash");
        }
        if (!ALLOWED_CHARS.matcher(name).matches()) {
            throw new IllegalArgumentException("worktree name contains unsupported characters");
        }
        if (name.contains("..")) {
            throw new IllegalArgumentException("worktree name must not contain '..'");
        }

        validateKindPattern(name, kind);
        validateSegments(name);

        String branchSlug = name.replace('/', '+');
        return new ValidWorktreeName(
                name,
                kind,
                Path.of(name).normalize(),
                branchSlug,
                BRANCH_PREFIX + branchSlug
        );
    }

    private void validateKindPattern(String name, WorktreeKind kind) {
        if (kind == WorktreeKind.AGENT && !AGENT_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("agent worktree name must match agent-a + lowercase hex chars");
        }
        if (kind == WorktreeKind.WORKFLOW && !WORKFLOW_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("workflow worktree name must match wf_ + 12 lowercase hex chars");
        }
    }

    private void validateSegments(String name) {
        String[] segments = name.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException("worktree name must not contain empty path segments");
            }
            if (".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("worktree name must not contain '.' or '..' path segments");
            }
            String reservedKey = segment;
            int dot = reservedKey.indexOf('.');
            if (dot >= 0) {
                reservedKey = reservedKey.substring(0, dot);
            }
            reservedKey = reservedKey.toUpperCase(Locale.ROOT);
            if (WINDOWS_RESERVED_NAMES.contains(reservedKey)) {
                throw new IllegalArgumentException("worktree name contains Windows reserved path segment: " + segment);
            }
        }
    }
}
