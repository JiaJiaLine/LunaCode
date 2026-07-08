package com.lunacode.subagent;

import com.lunacode.background.BackgroundTaskManager;
import com.lunacode.background.ForegroundSubAgentTracker;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.hook.HookExecutionScope;
import com.lunacode.permission.PermissionMode;
import com.lunacode.runtime.AgentMode;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.team.TeamManager;
import com.lunacode.team.TeamMemberAddRequest;
import com.lunacode.team.TeamMemberBackendKind;
import com.lunacode.team.TeamMemberLaunchMode;
import com.lunacode.team.TeamMemberRecord;
import com.lunacode.team.TeamRuntimeContext;
import com.lunacode.tool.ToolResult;
import com.lunacode.worktree.WorktreeCreateRequest;
import com.lunacode.worktree.WorktreeCreateResult;
import com.lunacode.worktree.WorktreeKind;
import com.lunacode.worktree.WorktreeManager;
import com.lunacode.worktree.WorktreeRecord;

import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class DefaultSubAgentService implements SubAgentService {
    private final AgentDefinitionCatalog catalog;
    private final SubAgentRunnerFactory runnerFactory;
    private final BackgroundTaskManager backgroundTaskManager;
    private final ForegroundSubAgentTracker foregroundTracker;
    private final ProviderConfig providerConfig;
    private final WorktreeManager worktreeManager;
    private final TeamManager teamManager;

    public DefaultSubAgentService(
            AgentDefinitionCatalog catalog,
            SubAgentRunnerFactory runnerFactory,
            BackgroundTaskManager backgroundTaskManager,
            ForegroundSubAgentTracker foregroundTracker,
            ProviderConfig providerConfig
    ) {
        this(catalog, runnerFactory, backgroundTaskManager, foregroundTracker, providerConfig, null, null);
    }

    public DefaultSubAgentService(
            AgentDefinitionCatalog catalog,
            SubAgentRunnerFactory runnerFactory,
            BackgroundTaskManager backgroundTaskManager,
            ForegroundSubAgentTracker foregroundTracker,
            ProviderConfig providerConfig,
            WorktreeManager worktreeManager
    ) {
        this(catalog, runnerFactory, backgroundTaskManager, foregroundTracker, providerConfig, worktreeManager, null);
    }

    public DefaultSubAgentService(
            AgentDefinitionCatalog catalog,
            SubAgentRunnerFactory runnerFactory,
            BackgroundTaskManager backgroundTaskManager,
            ForegroundSubAgentTracker foregroundTracker,
            ProviderConfig providerConfig,
            WorktreeManager worktreeManager,
            TeamManager teamManager
    ) {
        this.catalog = catalog;
        this.runnerFactory = runnerFactory;
        this.backgroundTaskManager = backgroundTaskManager;
        this.foregroundTracker = foregroundTracker;
        this.providerConfig = providerConfig;
        this.worktreeManager = worktreeManager;
        this.teamManager = teamManager;
    }

    @Override
    public ToolResult launchFromTool(AgentToolRequest request, SubAgentParentContext parentContext) {
        if (request == null || request.task().isBlank()) {
            return error("Agent tool requires task", "invalid_arguments");
        }
        if (parentContext == null) {
            return error("Agent tool requires parent context", "missing_parent_context");
        }
        if (parentContext.parentIsBackground()) {
            return error("Background Agent cannot spawn another Agent", "nested_agent_forbidden");
        }
        if (request.name().isPresent() || request.teamName().isPresent()) {
            return launchTeamMember(request, parentContext);
        }
        if (request.subagentType().isEmpty()) {
            if (parentContext.parentIsFork()) {
                return error("Fork sub agent cannot fork again", "fork_cannot_fork");
            }
            SubAgentLaunchRequest launch = new SubAgentLaunchRequest(
                    SubAgentKind.FORK,
                    Optional.empty(),
                    request.task(),
                    true,
                    parentContext,
                    SubAgentNotificationPolicy.TOOL
            );
            return asyncResult(backgroundTaskManager.launch(launch), "fork");
        }

        Optional<AgentDefinition> definition = catalog.find(request.subagentType().orElseThrow());
        if (definition.isEmpty()) {
            return error("Sub agent type not found: " + request.subagentType().orElse(""), "subagent_not_found");
        }
        Optional<WorktreeRecord> worktree;
        try {
            worktree = prepareWorktree(definition.get(), parentContext);
        } catch (RuntimeException e) {
            return error("Sub agent worktree creation failed: " + e.getMessage(), "worktree_create_failed");
        }
        boolean runInBackground = request.runInBackground() || definition.get().background();
        SubAgentLaunchRequest launch = new SubAgentLaunchRequest(
                SubAgentKind.DEFINED,
                definition,
                worktree.map(record -> withWorktreeInstructions(request.task(), record)).orElse(request.task()),
                runInBackground,
                parentContext,
                SubAgentNotificationPolicy.TOOL,
                worktree
        );
        if (runInBackground) {
            return asyncResult(backgroundTaskManager.launch(launch), definition.get().agentType());
        }
        return runForeground(launch);
    }

    private ToolResult launchTeamMember(AgentToolRequest request, SubAgentParentContext parentContext) {
        if (teamManager == null) {
            return error("Team manager is not configured", "team_not_configured");
        }
        String teamName = request.teamName()
                .or(() -> parentContext.parentConfig().teamRuntimeContext().active() ? Optional.of(parentContext.parentConfig().teamRuntimeContext().teamName()) : Optional.empty())
                .or(() -> teamManager.currentTeam().map(team -> team.name()))
                .orElse("");
        if (teamName.isBlank()) {
            return error("No current team; create or select a team first", "team_not_found");
        }
        if (request.name().isEmpty()) {
            return error("Team Agent launch requires name", "missing_member_name");
        }
        TeamMemberBackendKind backend;
        try {
            backend = request.backend().map(value -> TeamMemberBackendKind.valueOf(value.toUpperCase().replace('-', '_'))).orElse(TeamMemberBackendKind.SAME_PROCESS);
        } catch (IllegalArgumentException e) {
            return error("Unknown team member backend: " + request.backend().orElse(""), "invalid_backend");
        }
        TeamMemberRecord member;
        try {
            member = teamManager.addMember(new TeamMemberAddRequest(
                    teamName,
                    request.name().orElseThrow(),
                    request.role().orElse("teammate"),
                    backend,
                    request.subagentType(),
                    request.planModeRequired(),
                    Optional.empty(),
                    parentContext.sessionId()
            ));
        } catch (RuntimeException e) {
            return error("Team member creation failed: " + e.getMessage(), "team_member_create_failed");
        }

        Optional<AgentDefinition> definition = definitionForMember(member);
        if (member.launchMode() == TeamMemberLaunchMode.DEFINED && definition.isEmpty()) {
            return error("Sub agent type not found: " + member.agentType().orElse(""), "subagent_not_found");
        }
        Optional<WorktreeRecord> worktree = member.worktreeName().flatMap(name -> worktreeManager == null ? Optional.empty() : worktreeManager.find(name));
        SubAgentKind kind = member.launchMode() == TeamMemberLaunchMode.FORK ? SubAgentKind.FORK : SubAgentKind.DEFINED;
        TeamRuntimeContext teamContext = TeamRuntimeContext.member(teamName, member.name(), member.agentId(), member.planModeRequired(), false);
        String task = withTeamInstructions(request.task(), teamName, member, worktree, request.planModeRequired());
        SubAgentLaunchRequest launch = new SubAgentLaunchRequest(
                kind,
                definition,
                task,
                true,
                parentContext,
                SubAgentNotificationPolicy.TOOL,
                worktree,
                teamContext
        );
        String taskId = backgroundTaskManager.launch(launch);
        TeamMemberRecord registered = teamManager.registerMemberAgentId(teamName, member.name(), taskId);
        return asyncResult(taskId, "team:" + registered.name());
    }

    private Optional<AgentDefinition> definitionForMember(TeamMemberRecord member) {
        if (member.launchMode() == TeamMemberLaunchMode.DEFINED) {
            return member.agentType().flatMap(catalog::find);
        }
        if (member.launchMode() == TeamMemberLaunchMode.GENERAL_PURPOSE) {
            return catalog.find("general-purpose");
        }
        return Optional.empty();
    }

    private Optional<WorktreeRecord> prepareWorktree(AgentDefinition definition, SubAgentParentContext parentContext) {
        if (definition.isolation() != AgentIsolation.WORKTREE) {
            return Optional.empty();
        }
        if (worktreeManager == null) {
            throw new IllegalStateException("Worktree manager is not configured");
        }
        String sessionId = parentContext == null ? "" : parentContext.sessionId();
        WorktreeCreateResult result = worktreeManager.create(WorktreeCreateRequest.automatic(
                worktreeManager.generateAgentName(),
                WorktreeKind.AGENT,
                sessionId
        ));
        return Optional.of(result.record());
    }

    private String withWorktreeInstructions(String task, WorktreeRecord record) {
        return "You are working inside an isolated Git worktree. All file and Bash tool calls default to this directory: "
                + record.path()
                + "\nBranch: " + record.branchName()
                + "\nDo not switch back to the main repository. When you finish, the parent Agent will decide whether to keep or clean this worktree.\n\n"
                + task;
    }

    private String withTeamInstructions(String task, String teamName, TeamMemberRecord member, Optional<WorktreeRecord> worktree, boolean planModeRequired) {
        StringBuilder out = new StringBuilder();
        out.append("You are teammate `").append(member.name()).append("` in team `").append(teamName).append("`. ");
        out.append("Use TaskList/TaskGet/TaskUpdate and SendMessage to coordinate directly with teammates. ");
        if (planModeRequired) {
            out.append("Before modifying files, send your plan to Lead and wait for a plan_approval_response approval. ");
        }
        worktree.ifPresent(record -> out.append("Your isolated worktree is ").append(record.path()).append(" on branch ").append(record.branchName()).append(". "));
        out.append("When done, mark relevant tasks DONE and notify Lead.\n\n").append(task);
        return out.toString();
    }

    @Override
    public String launchFromHook(String subagentType, String task, HookExecutionScope scope) {
        Optional<AgentDefinition> definition = catalog.find(subagentType);
        if (definition.isEmpty()) {
            throw new IllegalArgumentException("Sub agent type not found: " + subagentType);
        }
        HookExecutionScope safeScope = scope == null ? new HookExecutionScope("unknown-session", 0, Path.of(".")) : scope;
        AgentRunConfig parentConfig = new AgentRunConfig(
                safeScope.workspaceRoot(),
                AgentMode.DEFAULT,
                PermissionMode.DEFAULT,
                safeScope.workspaceRoot().resolve(".lunacode/plan.md"),
                providerConfig.agent().maxIterations(),
                providerConfig.agent().maxConsecutiveUnknownTools(),
                Clock.systemDefaultZone()
        );
        SubAgentParentContext parentContext = new SubAgentParentContext(
                new DefaultConversationManager(),
                parentConfig,
                parentConfig.toolAccessPolicy(),
                false,
                false,
                safeScope.sessionId(),
                safeScope.workspaceRoot()
        );
        return backgroundTaskManager.launch(new SubAgentLaunchRequest(
                SubAgentKind.DEFINED,
                definition,
                task,
                true,
                parentContext,
                SubAgentNotificationPolicy.HOOK
        ));
    }

    @Override
    public SubAgentRunHandle startForeground(SubAgentLaunchRequest request) {
        return runnerFactory.start(request);
    }

    @Override
    public String launchBackground(SubAgentLaunchRequest request) {
        return backgroundTaskManager.launch(request);
    }

    private ToolResult runForeground(SubAgentLaunchRequest launch) {
        SubAgentRunHandle handle = runnerFactory.start(launch);
        foregroundTracker.setCurrent(handle, launch.task());
        try {
            long timeout = providerConfig.agent().getAutoBackgroundMs();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
            while (true) {
                if (handle.backgroundTaskId().isPresent()) {
                    return asyncResult(handle.backgroundTaskId().orElseThrow(), launch.definition().map(AgentDefinition::agentType).orElse("defined"));
                }
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw new TimeoutException("auto background timeout");
                }
                long waitMillis = Math.min(100L, Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
                try {
                    SubAgentResult result = handle.completion().get(waitMillis, TimeUnit.MILLISECONDS);
                    foregroundTracker.clear(handle);
                    if (result.failureReason().isPresent()) {
                        return error(result.fullResult(), "subagent_failed");
                    }
                    return ToolResult.success(result.fullResult(), Map.of(
                            "status", "completed",
                            "summary", result.summary(),
                            "toolCallCount", result.toolCallCount()
                    ));
                } catch (TimeoutException ignored) {
                    // 短轮询用于观察 ESC/adoptRunning，不代表自动后台超时。
                }
            }
        } catch (TimeoutException e) {
            String taskId = foregroundTracker.adoptCurrentToBackground()
                    .orElseGet(() -> backgroundTaskManager.adoptRunning(handle, launch.task()));
            return asyncResult(taskId, launch.definition().map(AgentDefinition::agentType).orElse("defined"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            foregroundTracker.clear(handle);
            return error("Sub agent foreground wait was interrupted", "interrupted");
        } catch (ExecutionException e) {
            foregroundTracker.clear(handle);
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return error("Sub agent failed: " + cause.getMessage(), "subagent_failed");
        } finally {
            foregroundTracker.clear(handle);
        }
    }

    private ToolResult asyncResult(String taskId, String agentType) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", "async_launched");
        metadata.put("taskId", taskId);
        metadata.put("agentType", agentType);
        return ToolResult.success("async_launched\nagent_id: " + taskId, metadata);
    }

    private ToolResult error(String message, String errorType) {
        return ToolResult.error(message, Map.of("errorType", errorType));
    }
}