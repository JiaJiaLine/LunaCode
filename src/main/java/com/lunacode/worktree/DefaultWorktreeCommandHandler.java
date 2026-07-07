package com.lunacode.worktree;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class DefaultWorktreeCommandHandler implements WorktreeCommandHandler {
    private final WorktreeManager manager;

    public DefaultWorktreeCommandHandler(WorktreeManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public CommandResult handle(String rawInput, boolean busy) {
        List<String> args = parseArgs(rawInput);
        if (!args.isEmpty() && "/worktree".equals(args.get(0))) {
            args = args.subList(1, args.size());
        }
        if (args.isEmpty() || "help".equals(args.get(0))) {
            return info(usage());
        }
        String subcommand = args.get(0).toLowerCase(Locale.ROOT);
        if (busy && !"list".equals(subcommand)) {
            return warning("当前忙碌，/worktree " + subcommand + " 暂不可用；可以使用 /worktree list 查看。");
        }
        try {
            return switch (subcommand) {
                case "create" -> create(args);
                case "list" -> list();
                case "enter" -> enter(args);
                case "exit" -> exit();
                case "remove" -> remove(args);
                default -> error("未知 /worktree 子命令: " + subcommand + "\n" + usage());
            };
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    private CommandResult create(List<String> args) {
        if (args.size() != 2) {
            return error("用法: /worktree create <name>");
        }
        WorktreeCreateResult result = manager.create(WorktreeCreateRequest.manual(args.get(1)));
        StringBuilder out = new StringBuilder();
        out.append(result.fastRestored() ? "已复用 Worktree" : "已创建 Worktree")
                .append(": ").append(result.record().name())
                .append("\n路径: ").append(result.record().path())
                .append("\n分支: ").append(result.record().branchName());
        appendWarnings(out, result.warnings());
        return result.warnings().isEmpty() ? info(out.toString()) : warning(out.toString());
    }

    private CommandResult list() {
        List<WorktreeSnapshot> snapshots = manager.list();
        if (snapshots.isEmpty()) {
            return info("暂无 Worktree。");
        }
        StringBuilder out = new StringBuilder("Worktree:");
        for (WorktreeSnapshot snapshot : snapshots) {
            out.append('\n')
                    .append(snapshot.current() ? "* " : "- ")
                    .append(snapshot.name())
                    .append(" [").append(snapshot.kind().name().toLowerCase(Locale.ROOT)).append("]")
                    .append(" branch=").append(snapshot.branchName())
                    .append(" changes=").append(snapshot.changes().summary())
                    .append("\n  path=").append(snapshot.path());
        }
        return info(out.toString());
    }

    private CommandResult enter(List<String> args) {
        if (args.size() != 2) {
            return error("用法: /worktree enter <name>");
        }
        WorktreeSession session = manager.enter(args.get(1), UUID.randomUUID().toString());
        return info("已进入 Worktree: " + session.worktreeName()
                + "\n路径: " + session.worktreePath()
                + "\n分支: " + session.worktreeBranch());
    }

    private CommandResult exit() {
        manager.exit();
        return info("已退出 Worktree，后续工具调用回到主仓库。");
    }

    private CommandResult remove(List<String> args) {
        if (args.size() < 2 || args.size() > 3) {
            return error("用法: /worktree remove <name> [--discardChanges]");
        }
        boolean discard = args.size() == 3 && ("--discardChanges".equals(args.get(2)) || "--discard-changes".equals(args.get(2)));
        if (args.size() == 3 && !discard) {
            return error("未知参数: " + args.get(2));
        }
        WorktreeRemoveResult result = manager.remove(WorktreeRemoveRequest.manual(args.get(1), discard));
        StringBuilder out = new StringBuilder();
        out.append(result.removed() ? "已删除 Worktree: " : "已保留 Worktree: ").append(result.name());
        result.path().ifPresent(path -> out.append("\n路径: ").append(path));
        result.branchName().ifPresent(branch -> out.append("\n分支: ").append(branch));
        out.append("\n变更: ").append(result.changes().summary());
        if (!result.message().isBlank()) {
            out.append("\n").append(result.message());
        }
        appendWarnings(out, result.warnings());
        return result.removed() ? info(out.toString()) : warning(out.toString());
    }

    private List<String> parseArgs(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return List.of();
        }
        return List.of(rawInput.strip().split("\\s+"));
    }

    private void appendWarnings(StringBuilder out, List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return;
        }
        out.append("\n警告:");
        warnings.forEach(warning -> out.append("\n- ").append(warning));
    }

    private String usage() {
        return """
                用法:
                /worktree create <name>
                /worktree list
                /worktree enter <name>
                /worktree exit
                /worktree remove <name> [--discardChanges]
                """.strip();
    }

    private CommandResult info(String message) {
        return new CommandResult("idle", message);
    }

    private CommandResult warning(String message) {
        return new CommandResult("warning", message);
    }

    private CommandResult error(String message) {
        return new CommandResult("error", message);
    }
}
