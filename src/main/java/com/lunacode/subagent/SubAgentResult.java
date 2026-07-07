package com.lunacode.subagent;

import com.lunacode.conversation.TokenUsage;

import java.nio.file.Path;
import java.util.Optional;

public record SubAgentResult(
        String summary,
        String fullResult,
        TokenUsage usage,
        int toolCallCount,
        boolean reachedMaxTurns,
        Optional<String> failureReason,
        Optional<String> retainedWorktreePath,
        Optional<String> retainedWorktreeBranch
) {
    public SubAgentResult(
            String summary,
            String fullResult,
            TokenUsage usage,
            int toolCallCount,
            boolean reachedMaxTurns,
            Optional<String> failureReason
    ) {
        this(summary, fullResult, usage, toolCallCount, reachedMaxTurns, failureReason, Optional.empty(), Optional.empty());
    }

    public SubAgentResult {
        summary = summary == null ? "" : summary;
        fullResult = fullResult == null ? "" : fullResult;
        usage = usage == null ? TokenUsage.unknown() : usage;
        failureReason = failureReason == null ? Optional.empty() : failureReason.map(String::strip).filter(value -> !value.isBlank());
        retainedWorktreePath = retainedWorktreePath == null ? Optional.empty() : retainedWorktreePath.map(String::strip).filter(value -> !value.isBlank());
        retainedWorktreeBranch = retainedWorktreeBranch == null ? Optional.empty() : retainedWorktreeBranch.map(String::strip).filter(value -> !value.isBlank());
    }

    public SubAgentResult withRetainedWorktree(Path path, String branch) {
        String worktreePath = path == null ? "" : path.toAbsolutePath().normalize().toString();
        String worktreeBranch = branch == null ? "" : branch.strip();
        String suffix = "\n\nWorktree 已保留，等待主 Agent review。\npath: " + worktreePath + "\nbranch: " + worktreeBranch;
        return new SubAgentResult(
                summary,
                fullResult + suffix,
                usage,
                toolCallCount,
                reachedMaxTurns,
                failureReason,
                Optional.of(worktreePath),
                Optional.of(worktreeBranch)
        );
    }
}