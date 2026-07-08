package com.lunacode.command;

import com.lunacode.team.TeamManager;
import com.lunacode.team.TeamMemberAddRequest;
import com.lunacode.team.TeamMemberBackendKind;
import com.lunacode.team.TeamMemberRecord;
import com.lunacode.team.TeamRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DefaultTeamCommandHandler implements TeamCommandHandler {
    private final TeamManager manager;
    private final java.util.function.Supplier<String> sessionIdSupplier;

    public DefaultTeamCommandHandler(TeamManager manager, java.util.function.Supplier<String> sessionIdSupplier) {
        this.manager = manager;
        this.sessionIdSupplier = sessionIdSupplier == null ? () -> "" : sessionIdSupplier;
    }

    @Override
    public CommandResult handle(String rawInput, boolean busy) {
        try {
            List<String> args = args(rawInput);
            if (args.isEmpty() || args.get(0).equals("list")) {
                return ok(listTeams());
            }
            return switch (args.get(0)) {
                case "create" -> create(args);
                case "use", "select" -> use(args);
                case "delete", "remove" -> delete(args);
                case "member" -> member(args);
                default -> error("用法: /team [create|use|list|delete|member]");
            };
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    private CommandResult create(List<String> args) {
        if (args.size() < 2) {
            return error("用法: /team create <name>");
        }
        TeamRecord team = manager.createTeam(args.get(1), "lead");
        return ok("已创建并选中团队: " + team.name() + "\n目录: " + team.directory());
    }

    private CommandResult use(List<String> args) {
        if (args.size() < 2) {
            return error("用法: /team use <name>");
        }
        manager.setCurrentTeam(args.get(1));
        return ok("已切换团队: " + args.get(1));
    }

    private CommandResult delete(List<String> args) {
        if (args.size() < 2) {
            return error("用法: /team delete <name> [--force]");
        }
        boolean force = args.contains("--force");
        return ok(manager.deleteTeam(args.get(1), force));
    }

    private CommandResult member(List<String> args) {
        if (args.size() < 2) {
            return error("用法: /team member [add|list]");
        }
        return switch (args.get(1)) {
            case "add" -> addMember(args);
            case "list" -> ok(listMembers());
            default -> error("用法: /team member [add|list]");
        };
    }

    private CommandResult addMember(List<String> args) {
        if (args.size() < 3) {
            return error("用法: /team member add <name> [--agentType type] [--backend same_process|terminal] [--plan] [--role role]");
        }
        String name = args.get(2);
        Optional<String> agentType = Optional.empty();
        String role = "teammate";
        TeamMemberBackendKind backend = TeamMemberBackendKind.SAME_PROCESS;
        boolean plan = false;
        for (int i = 3; i < args.size(); i++) {
            String arg = args.get(i);
            if ("--plan".equals(arg) || "--planModeRequired".equals(arg)) {
                plan = true;
            } else if ("--agentType".equals(arg) && i + 1 < args.size()) {
                agentType = Optional.of(args.get(++i));
            } else if ("--backend".equals(arg) && i + 1 < args.size()) {
                backend = TeamMemberBackendKind.valueOf(args.get(++i).toUpperCase().replace('-', '_'));
            } else if ("--role".equals(arg) && i + 1 < args.size()) {
                role = args.get(++i);
            }
        }
        String team = manager.currentTeam().orElseThrow(() -> new IllegalStateException("当前没有团队，请先 /team create <name>")).name();
        TeamMemberRecord member = manager.addMember(new TeamMemberAddRequest(team, name, role, backend, agentType, plan, Optional.empty(), sessionId()));
        return ok("已添加成员: " + member.name() + "\nagentId: " + member.agentId() + member.worktreePath().map(path -> "\nworktree: " + path).orElse(""));
    }

    private String listTeams() {
        List<TeamRecord> teams = manager.listTeams();
        if (teams.isEmpty()) {
            return "暂无团队。用 /team create <name> 创建。";
        }
        String current = manager.currentTeam().map(TeamRecord::name).orElse("");
        StringBuilder out = new StringBuilder("团队:");
        for (TeamRecord team : teams) {
            out.append('\n').append(current.equals(team.name()) ? "* " : "- ")
                    .append(team.name())
                    .append(" members=").append(team.members().size())
                    .append(" path=").append(team.directory());
        }
        return out.toString();
    }

    private String listMembers() {
        TeamRecord team = manager.currentTeam().orElseThrow(() -> new IllegalStateException("当前没有团队"));
        if (team.members().isEmpty()) {
            return "团队 " + team.name() + " 暂无成员。";
        }
        StringBuilder out = new StringBuilder("成员:");
        for (TeamMemberRecord member : team.members().values()) {
            out.append('\n')
                    .append("- ").append(member.name())
                    .append(" id=").append(member.agentId())
                    .append(" status=").append(member.status())
                    .append(" backend=").append(member.backend())
                    .append(" launch=").append(member.launchMode());
        }
        return out.toString();
    }

    private List<String> args(String rawInput) {
        String input = rawInput == null ? "" : rawInput.strip();
        if (input.startsWith("/team")) {
            input = input.substring("/team".length()).strip();
        }
        if (input.isBlank()) {
            return List.of();
        }
        return new ArrayList<>(List.of(input.split("\\s+")));
    }

    private String sessionId() {
        try {
            return sessionIdSupplier.get();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private CommandResult ok(String message) {
        return new CommandResult("idle", message);
    }

    private CommandResult error(String message) {
        return new CommandResult("error", message);
    }
}
