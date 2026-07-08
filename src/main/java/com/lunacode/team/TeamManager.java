package com.lunacode.team;

import com.lunacode.team.mailbox.TeamMessageRecord;
import com.lunacode.team.mailbox.TeamMessageType;
import com.lunacode.team.task.TaskCreateRequest;
import com.lunacode.team.task.TaskListFilter;
import com.lunacode.team.task.TaskUpdatePatch;
import com.lunacode.team.task.TeamTaskRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TeamManager {
    TeamRecord createTeam(String name, String leadAgentId);

    Optional<TeamRecord> currentTeam();

    Optional<TeamRecord> findTeam(String name);

    List<TeamRecord> listTeams();

    void setCurrentTeam(String name);

    String deleteTeam(String name, boolean force);

    TeamMemberRecord addMember(TeamMemberAddRequest request);

    TeamMemberRecord registerMemberAgentId(String teamName, String memberName, String agentId);

    TeamMemberRecord updateMemberStatus(String teamName, String memberName, TeamMemberStatus status);

    AgentNameRegistry registry(String teamName);

    TeamTaskRecord createTask(String teamName, TaskCreateRequest request);

    Optional<TeamTaskRecord> getTask(String teamName, String taskId);

    List<TeamTaskRecord> listTasks(String teamName, TaskListFilter filter);

    TeamTaskRecord updateTask(String teamName, String taskId, TaskUpdatePatch patch, String actor);

    List<TeamMessageRecord> sendMessage(String teamName, String from, String to, TeamMessageType type, String summary, String message, Map<String, String> metadata);
}
