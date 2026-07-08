package com.lunacode.team;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record TeamRecord(
        String name,
        Path repoRoot,
        Optional<String> leadAgentId,
        Map<String, TeamMemberRecord> members,
        Path directory,
        Instant createdAt,
        Instant updatedAt
) {
    public TeamRecord {
        name = new TeamNameValidator().validate(name, "teamName");
        repoRoot = Objects.requireNonNull(repoRoot, "repoRoot").toAbsolutePath().normalize();
        leadAgentId = leadAgentId == null ? Optional.empty() : leadAgentId.map(String::strip).filter(value -> !value.isBlank());
        Map<String, TeamMemberRecord> copy = new LinkedHashMap<>();
        if (members != null) {
            members.forEach((memberName, record) -> copy.put(new TeamNameValidator().validate(memberName, "memberName"), Objects.requireNonNull(record, "record")));
        }
        members = Collections.unmodifiableMap(copy);
        directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public static TeamRecord create(String name, Path repoRoot, Path directory, String leadAgentId) {
        return new TeamRecord(name, repoRoot, Optional.ofNullable(leadAgentId), Map.of(), directory, Instant.now(), Instant.now());
    }

    public TeamRecord withMember(TeamMemberRecord member) {
        Map<String, TeamMemberRecord> copy = new LinkedHashMap<>(members);
        copy.put(member.name(), member);
        return new TeamRecord(name, repoRoot, leadAgentId, copy, directory, createdAt, Instant.now());
    }

    public TeamRecord withoutMember(String memberName) {
        Map<String, TeamMemberRecord> copy = new LinkedHashMap<>(members);
        copy.remove(memberName);
        return new TeamRecord(name, repoRoot, leadAgentId, copy, directory, createdAt, Instant.now());
    }
}
