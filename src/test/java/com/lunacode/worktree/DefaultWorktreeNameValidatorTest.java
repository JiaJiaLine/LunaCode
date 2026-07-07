package com.lunacode.worktree;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DefaultWorktreeNameValidatorTest {
    private final DefaultWorktreeNameValidator validator = new DefaultWorktreeNameValidator();

    @Test
    void acceptsNestedManualNameAndBuildsBranchName() {
        ValidWorktreeName name = validator.validate("team-refactor/alice_1.v2", WorktreeKind.MANUAL);

        assertEquals("team-refactor/alice_1.v2", name.rawName());
        assertEquals(Path.of("team-refactor/alice_1.v2").normalize(), name.relativePath());
        assertEquals("team-refactor+alice_1.v2", name.branchSlug());
        assertEquals("worktree-team-refactor+alice_1.v2", name.branchName());
    }

    @Test
    void rejectsPathTraversalAndUnsafeSegments() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("../x", WorktreeKind.MANUAL));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("a/./b", WorktreeKind.MANUAL));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("a//b", WorktreeKind.MANUAL));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("a\\b", WorktreeKind.MANUAL));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("a b", WorktreeKind.MANUAL));
    }

    @Test
    void rejectsWindowsReservedSegments() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("CON", WorktreeKind.MANUAL));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("feature/NUL.txt", WorktreeKind.MANUAL));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("LPT1/log", WorktreeKind.MANUAL));
    }

    @Test
    void enforcesLengthLimit() {
        String ok = "a".repeat(DefaultWorktreeNameValidator.MAX_NAME_LENGTH);
        assertDoesNotThrow(() -> validator.validate(ok, WorktreeKind.MANUAL));

        String tooLong = "a".repeat(DefaultWorktreeNameValidator.MAX_NAME_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(tooLong, WorktreeKind.MANUAL));
    }

    @Test
    void validatesAutomaticAgentName() {
        ValidWorktreeName name = validator.validate("agent-a3f2b1c", WorktreeKind.AGENT);

        assertEquals("worktree-agent-a3f2b1c", name.branchName());
        assertThrows(IllegalArgumentException.class, () -> validator.validate("agent-3f2b1c0", WorktreeKind.AGENT));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("agent-a3F2B1C", WorktreeKind.AGENT));
    }

    @Test
    void validatesWorkflowName() {
        ValidWorktreeName name = validator.validate("wf_1234abcdef56", WorktreeKind.WORKFLOW);

        assertEquals("worktree-wf_1234abcdef56", name.branchName());
        assertThrows(IllegalArgumentException.class, () -> validator.validate("wf_123", WorktreeKind.WORKFLOW));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("wf_1234ABCDEF56", WorktreeKind.WORKFLOW));
    }
}
