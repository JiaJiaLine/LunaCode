package com.lunacode.team;

import java.util.Objects;
import java.util.Optional;

public record TeamRuntimeContext(
        TeamRuntimeRole role,
        String teamName,
        Optional<String> memberName,
        Optional<String> agentId,
        boolean planModeRequired,
        boolean planApproved
) {
    public TeamRuntimeContext(TeamRuntimeRole role, String teamName, Optional<String> memberName, Optional<String> agentId) {
        this(role, teamName, memberName, agentId, false, false);
    }

    public TeamRuntimeContext {
        role = role == null ? TeamRuntimeRole.NONE : role;
        teamName = normalize(teamName);
        memberName = memberName == null ? Optional.empty() : memberName.map(TeamRuntimeContext::normalize).filter(value -> !value.isBlank());
        agentId = agentId == null ? Optional.empty() : agentId.map(TeamRuntimeContext::normalize).filter(value -> !value.isBlank());
        if (role != TeamRuntimeRole.NONE && teamName.isBlank()) {
            throw new IllegalArgumentException("teamName must not be blank when team role is active");
        }
    }

    public static TeamRuntimeContext none() {
        return new TeamRuntimeContext(TeamRuntimeRole.NONE, "", Optional.empty(), Optional.empty(), false, false);
    }

    public static TeamRuntimeContext lead(String teamName) {
        return new TeamRuntimeContext(TeamRuntimeRole.LEAD, teamName, Optional.empty(), Optional.empty(), false, true);
    }

    public static TeamRuntimeContext member(String teamName, String memberName, String agentId) {
        return member(teamName, memberName, agentId, false, false);
    }

    public static TeamRuntimeContext member(String teamName, String memberName, String agentId, boolean planModeRequired, boolean planApproved) {
        return new TeamRuntimeContext(TeamRuntimeRole.MEMBER, teamName, Optional.ofNullable(memberName), Optional.ofNullable(agentId), planModeRequired, planApproved);
    }

    public boolean active() {
        return role != TeamRuntimeRole.NONE;
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").strip();
    }
}