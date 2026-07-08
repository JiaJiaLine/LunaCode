package com.lunacode.team;

import java.util.List;
import java.util.Optional;

public interface TeamStore {
    TeamRecord create(String name, String leadAgentId);

    Optional<TeamRecord> load(String name);

    TeamRecord save(TeamRecord team);

    List<TeamRecord> list();

    Optional<String> currentTeamName();

    void setCurrentTeamName(String name);

    void clearCurrentTeamName();

    AgentNameRegistry loadRegistry(String teamName);

    AgentNameRegistry saveRegistry(String teamName, AgentNameRegistry registry);
}
