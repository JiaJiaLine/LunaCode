package com.lunacode.worktree;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface GitWorktreeClient {
    /**
     * 调用 Git 子进程读取主仓库当前 HEAD、分支和 dirty 状态。
     */
    GitRepositoryState inspectRepository(Path repoRoot);

    /**
     * 只通过文件系统读取 worktree 的 .git 指针、HEAD 和 ref，不调用 Git 子进程。
     */
    Optional<FastRestoredHead> tryReadHead(Path worktreePath);

    /**
     * 调用 Git 子进程执行 git worktree add -B。
     */
    void addWorktree(Path repoRoot, Path worktreePath, String branchName, String baseRef);

    /**
     * 调用 Git 子进程统计未提交修改和基线之后新增 commit 数。
     */
    WorktreeChanges countChanges(Path worktreePath, String baseHeadCommit);

    /**
     * 调用 Git 子进程强制删除 worktree 工作目录。
     */
    void removeWorktree(Path repoRoot, Path worktreePath);

    /**
     * 调用 Git 子进程删除 worktree 对应分支。
     */
    void deleteBranch(Path repoRoot, String branchName);

    /**
     * 调用 Git 子进程在 worktree 中显式配置 core.hooksPath。
     */
    void configureHooksPath(Path worktreePath, Path hooksPath);

    /**
     * 调用 Git 子进程列出被 .gitignore 等规则忽略的未追踪文件和目录。
     */
    List<Path> ignoredFiles(Path repoRoot);
}
