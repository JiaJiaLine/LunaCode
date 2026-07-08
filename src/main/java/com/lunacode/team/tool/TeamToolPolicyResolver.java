package com.lunacode.team.tool;

import com.lunacode.coordinator.CoordinatorModeState;
import com.lunacode.skill.ToolAccessPolicy;
import com.lunacode.team.TeamRuntimeContext;
import com.lunacode.team.TeamRuntimeRole;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class TeamToolPolicyResolver {
    public static final Set<String> TEAM_MANAGEMENT_TOOLS = Set.of("TeamCreate", "TeamDelete");
    public static final Set<String> COLLABORATION_TOOLS = Set.of("TaskCreate", "TaskGet", "TaskList", "TaskUpdate", "SendMessage");
    public static final Set<String> ALL_TEAM_TOOLS = union(TEAM_MANAGEMENT_TOOLS, COLLABORATION_TOOLS);

    public ToolAccessPolicy resolve(ToolAccessPolicy base, TeamRuntimeContext teamContext, CoordinatorModeState coordinatorMode) {
        ToolAccessPolicy policy = base == null ? ToolAccessPolicy.unrestricted(Set.of()) : base;
        Set<String> denied = new LinkedHashSet<>(policy.deniedTools());
        TeamRuntimeRole role = teamContext == null ? TeamRuntimeRole.NONE : teamContext.role();
        if (role == TeamRuntimeRole.NONE) {
            Set<String> bootstrapDenied = new LinkedHashSet<>(ALL_TEAM_TOOLS);
            bootstrapDenied.remove("TeamCreate");
            denied.addAll(normalizeSet(bootstrapDenied));
        } else if (role == TeamRuntimeRole.MEMBER) {
            denied.addAll(normalizeSet(TEAM_MANAGEMENT_TOOLS));
        }
        ToolAccessPolicy withTeam = new ToolAccessPolicy(policy.allowedTools(), policy.alwaysVisibleTools(), denied);
        if (coordinatorMode == null || !coordinatorMode.enabled()) {
            return withTeam;
        }
        return ToolAccessPolicy.restricted(coordinatorMode.allowedTools(), withTeam.alwaysVisibleTools());
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.addAll(right);
        return Set.copyOf(result);
    }

    private static Set<String> normalizeSet(Set<String> names) {
        return names.stream()
                .map(name -> name == null ? "" : name.strip().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}

