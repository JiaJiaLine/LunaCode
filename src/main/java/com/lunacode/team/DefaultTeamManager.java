package com.lunacode.team;

import com.lunacode.config.FeatureGate;
import com.lunacode.config.FeatureGateService;
import com.lunacode.team.mailbox.JsonTeamMailboxStore;
import com.lunacode.team.mailbox.TeamMailboxStore;
import com.lunacode.team.mailbox.TeamMessageRecord;
import com.lunacode.team.mailbox.TeamMessageType;
import com.lunacode.team.task.JsonTeamTaskStore;
import com.lunacode.team.task.TaskCreateRequest;
import com.lunacode.team.task.TaskListFilter;
import com.lunacode.team.task.TaskUpdatePatch;
import com.lunacode.team.task.TeamTaskRecord;
import com.lunacode.worktree.WorktreeCreateRequest;
import com.lunacode.worktree.WorktreeCreateResult;
import com.lunacode.worktree.WorktreeKind;
import com.lunacode.worktree.WorktreeManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DefaultTeamManager implements TeamManager {
    private final TeamStore store;
    private final WorktreeManager worktreeManager;
    private final FeatureGateService featureGateService;
    private final TeamNameValidator validator = new TeamNameValidator();

    public DefaultTeamManager(TeamStore store, WorktreeManager worktreeManager, FeatureGateService featureGateService) {
        this.store = store;
        this.worktreeManager = worktreeManager;
        this.featureGateService = featureGateService;
    }

    @Override
    public synchronized TeamRecord createTeam(String name, String leadAgentId) {
        return store.create(name, leadAgentId);
    }

    @Override
    public synchronized Optional<TeamRecord> currentTeam() {
        return store.currentTeamName().flatMap(store::load);
    }

    @Override
    public synchronized Optional<TeamRecord> findTeam(String name) {
        return store.load(name);
    }

    @Override
    public synchronized List<TeamRecord> listTeams() {
        return store.list();
    }

    @Override
    public synchronized void setCurrentTeam(String name) {
        String safeName = validator.validate(name, "teamName");
        if (store.load(safeName).isEmpty()) {
            throw new IllegalArgumentException("team not found: " + safeName);
        }
        store.setCurrentTeamName(safeName);
    }

    @Override
    public synchronized String deleteTeam(String name, boolean force) {
        TeamRecord team = requiredTeam(name);
        if (!force && (!team.members().isEmpty() || !new JsonTeamTaskStore(team.directory()).list(TaskListFilter.all()).isEmpty())) {
            throw new IllegalStateException("team is not empty; use force to delete");
        }
        deleteManagedDirectory(team.directory());
        if (store.currentTeamName().map(team.name()::equals).orElse(false)) {
            store.clearCurrentTeamName();
        }
        return "deleted team: " + team.name();
    }

    @Override
    public synchronized TeamMemberRecord addMember(TeamMemberAddRequest request) {
        TeamRecord team = request.teamName().isBlank()
                ? currentTeam().orElseThrow(() -> new IllegalStateException("no current team"))
                : requiredTeam(request.teamName());
        String memberName = validator.validate(request.name(), "memberName");
        TeamMemberRecord existing = team.members().get(memberName);
        if (existing != null) {
            if (existing.status() == TeamMemberStatus.RUNNING) {
                throw new IllegalArgumentException("team member is already running: " + memberName);
            }
            return existing;
        }
        if (request.backend() == TeamMemberBackendKind.TERMINAL) {
            throw new IllegalStateException("terminal backend is not available in this build; no fallback was used");
        }
        TeamMemberLaunchMode launchMode = launchMode(request.agentType());
        String agentId = request.agentId().orElseGet(() -> "agent-" + UUID.randomUUID().toString().substring(0, 8));
        TeamMemberRecord member = new TeamMemberRecord(
                memberName,
                agentId,
                request.role(),
                request.backend(),
                launchMode,
                request.agentType(),
                request.planModeRequired(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                TeamMemberStatus.IDLE,
                Optional.of(team.directory().resolve("contexts").resolve(memberName + ".jsonl")),
                Instant.now(),
                Instant.now()
        );
        if (worktreeManager != null) {
            WorktreeCreateResult result = worktreeManager.create(WorktreeCreateRequest.automatic(
                    worktreeManager.generateAgentName(),
                    WorktreeKind.AGENT,
                    request.sessionId()
            ));
            member = member.withWorktree(result.record().name(), result.record().path(), result.record().branchName());
        }
        TeamRecord updated = team.withMember(member);
        store.save(updated);
        if (request.agentId().isPresent()) {
            AgentNameRegistry registry = store.loadRegistry(team.name()).register(member.name(), member.agentId());
            store.saveRegistry(team.name(), registry);
        }
        return member;
    }

    @Override
    public synchronized TeamMemberRecord registerMemberAgentId(String teamName, String memberName, String agentId) {
        TeamRecord team = requiredTeam(teamName);
        TeamMemberRecord existing = requiredMember(team, memberName);
        TeamMemberRecord updatedMember = existing.withAgentId(agentId);
        store.save(team.withMember(updatedMember));
        store.saveRegistry(team.name(), store.loadRegistry(team.name()).rebind(updatedMember.name(), updatedMember.agentId()));
        return updatedMember;
    }

    @Override
    public synchronized TeamMemberRecord updateMemberStatus(String teamName, String memberName, TeamMemberStatus status) {
        TeamRecord team = requiredTeam(teamName);
        TeamMemberRecord updated = requiredMember(team, memberName).withStatus(status);
        store.save(team.withMember(updated));
        return updated;
    }

    @Override
    public synchronized AgentNameRegistry registry(String teamName) {
        return store.loadRegistry(requiredTeam(teamName).name());
    }

    @Override
    public synchronized TeamTaskRecord createTask(String teamName, TaskCreateRequest request) {
        return taskStore(requiredTeam(teamName)).create(request);
    }

    @Override
    public synchronized Optional<TeamTaskRecord> getTask(String teamName, String taskId) {
        return taskStore(requiredTeam(teamName)).get(taskId);
    }

    @Override
    public synchronized List<TeamTaskRecord> listTasks(String teamName, TaskListFilter filter) {
        return taskStore(requiredTeam(teamName)).list(filter);
    }

    @Override
    public synchronized TeamTaskRecord updateTask(String teamName, String taskId, TaskUpdatePatch patch, String actor) {
        return taskStore(requiredTeam(teamName)).update(taskId, patch, actor);
    }

    @Override
    public synchronized List<TeamMessageRecord> sendMessage(String teamName, String from, String to, TeamMessageType type, String summary, String message, Map<String, String> metadata) {
        TeamRecord team = requiredTeam(teamName);
        TeamMessageType safeType = type == null ? TeamMessageType.TEXT : type;
        validateProtocol(team, from, to, safeType, summary);
        List<String> recipients = recipients(team, to, from);
        TeamMailboxStore mailbox = new JsonTeamMailboxStore(team.directory());
        List<TeamMessageRecord> sent = new ArrayList<>();
        for (String recipient : recipients) {
            Map<String, String> safeMetadata = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
            team.members().values().stream()
                    .filter(member -> member.agentId().equals(recipient) && member.status() == TeamMemberStatus.STOPPED)
                    .findFirst()
                    .ifPresent(member -> {
                        safeMetadata.put("restored", "true");
                        updateMemberStatus(team.name(), member.name(), TeamMemberStatus.IDLE);
                    });
            sent.add(mailbox.append(recipient, new TeamMessageRecord(null, safeType, from, recipient, summary, message, Instant.now(), false, safeMetadata)));
        }
        return List.copyOf(sent);
    }

    private TeamMemberLaunchMode launchMode(Optional<String> agentType) {
        if (agentType != null && agentType.isPresent()) {
            return TeamMemberLaunchMode.DEFINED;
        }
        return featureGateService != null && featureGateService.enabled(FeatureGate.FORK_SUBAGENT)
                ? TeamMemberLaunchMode.FORK
                : TeamMemberLaunchMode.GENERAL_PURPOSE;
    }

    private List<String> recipients(TeamRecord team, String to, String from) {
        String target = to == null ? "" : to.strip();
        if (target.isBlank()) {
            throw new IllegalArgumentException("message target must not be blank");
        }
        AgentNameRegistry registry = store.loadRegistry(team.name());
        if ("*".equals(target)) {
            String sender = registry.resolveNameOrAgentId(from);
            return team.members().values().stream()
                    .map(TeamMemberRecord::agentId)
                    .filter(agentId -> !agentId.equals(sender))
                    .sorted()
                    .toList();
        }
        String resolved = registry.resolveNameOrAgentId(target);
        boolean known = team.members().values().stream().anyMatch(member -> member.agentId().equals(resolved))
                || team.leadAgentId().map(resolved::equals).orElse(false);
        if (!known && !"lead".equals(resolved)) {
            throw new IllegalArgumentException("message target not found: " + target);
        }
        return List.of(resolved);
    }

    private void validateProtocol(TeamRecord team, String from, String to, TeamMessageType type, String summary) {
        if (type == TeamMessageType.TEXT) {
            int words = summary == null || summary.isBlank() ? 0 : summary.strip().split("\\s+").length;
            if (words < 5 || words > 10) {
                throw new IllegalArgumentException("summary must contain 5-10 words for text messages");
            }
        }
        if (type == TeamMessageType.SHUTDOWN_RESPONSE && !("lead".equals(to) || team.leadAgentId().map(to::equals).orElse(false))) {
            throw new IllegalArgumentException("shutdown_response can only be sent to Lead");
        }
        if (type == TeamMessageType.PLAN_APPROVAL_RESPONSE && !("lead".equals(from) || team.leadAgentId().map(from::equals).orElse(false))) {
            throw new IllegalArgumentException("plan_approval_response can only be sent by Lead");
        }
    }

    private JsonTeamTaskStore taskStore(TeamRecord team) {
        return new JsonTeamTaskStore(team.directory());
    }

    private TeamRecord requiredTeam(String name) {
        return store.load(validator.validate(name, "teamName"))
                .orElseThrow(() -> new IllegalArgumentException("team not found: " + name));
    }

    private TeamMemberRecord requiredMember(TeamRecord team, String name) {
        String safeName = validator.validate(name, "memberName");
        TeamMemberRecord member = team.members().get(safeName);
        if (member == null) {
            throw new IllegalArgumentException("team member not found: " + safeName);
        }
        return member;
    }

    private void deleteManagedDirectory(Path directory) {
        try {
            if (!Files.exists(directory)) {
                return;
            }
            try (var stream = Files.walk(directory)) {
                List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
                for (Path path : paths) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to delete team directory: " + directory, e);
        }
    }
}
