package com.lunacode.team;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JsonTeamStore implements TeamStore {
    private static final String TEAM_FILE = "team.json";
    private static final String REGISTRY_FILE = "registry.json";

    private final TeamPaths paths;
    private final ObjectMapper mapper;
    private final TeamNameValidator validator = new TeamNameValidator();

    public JsonTeamStore(TeamPaths paths) {
        this(paths, new ObjectMapper());
    }

    public JsonTeamStore(TeamPaths paths, ObjectMapper mapper) {
        this.paths = paths;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
        migrateLegacyRoot();
    }

    @Override
    public synchronized TeamRecord create(String name, String leadAgentId) {
        String safeName = validator.validate(name, "teamName");
        Optional<TeamRecord> existing = load(safeName);
        if (existing.isPresent()) {
            setCurrentTeamName(safeName);
            return existing.get();
        }
        TeamRecord created = TeamRecord.create(safeName, paths.repoRoot(), paths.teamDir(safeName), leadAgentId);
        save(created);
        saveRegistry(safeName, AgentNameRegistry.empty());
        setCurrentTeamName(safeName);
        return created;
    }

    @Override
    public synchronized Optional<TeamRecord> load(String name) {
        String safeName = validator.validate(name, "teamName");
        Path path = teamFile(safeName);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(readTeam(mapper.readTree(Files.readString(path, StandardCharsets.UTF_8)), paths.teamDir(safeName)));
        } catch (Exception e) {
            throw new IllegalStateException("failed to load team: " + safeName, e);
        }
    }

    @Override
    public synchronized TeamRecord save(TeamRecord team) {
        try {
            Files.createDirectories(team.directory());
            writeJson(teamFile(team.name()), writeTeam(team));
            return team;
        } catch (IOException e) {
            throw new IllegalStateException("failed to save team: " + team.name(), e);
        }
    }

    @Override
    public synchronized List<TeamRecord> list() {
        if (!Files.isDirectory(paths.root())) {
            return List.of();
        }
        try {
            List<TeamRecord> teams = new ArrayList<>();
            try (var stream = Files.list(paths.root())) {
                stream.filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .forEach(name -> load(name).ifPresent(teams::add));
            }
            return List.copyOf(teams);
        } catch (IOException e) {
            throw new IllegalStateException("failed to list teams", e);
        }
    }

    @Override
    public synchronized Optional<String> currentTeamName() {
        Path file = paths.currentTeamFile();
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String value = Files.readString(file, StandardCharsets.UTF_8).strip();
            if (value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(validator.validate(value, "teamName"));
        } catch (IOException e) {
            throw new IllegalStateException("failed to read current team", e);
        }
    }

    @Override
    public synchronized void setCurrentTeamName(String name) {
        String safeName = validator.validate(name, "teamName");
        try {
            Files.createDirectories(paths.root());
            Files.writeString(paths.currentTeamFile(), safeName, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write current team", e);
        }
    }

    @Override
    public synchronized void clearCurrentTeamName() {
        try {
            Files.deleteIfExists(paths.currentTeamFile());
        } catch (IOException e) {
            throw new IllegalStateException("failed to clear current team", e);
        }
    }

    @Override
    public synchronized AgentNameRegistry loadRegistry(String teamName) {
        String safeName = validator.validate(teamName, "teamName");
        Path path = registryFile(safeName);
        if (!Files.isRegularFile(path)) {
            return AgentNameRegistry.empty();
        }
        try {
            JsonNode namesNode = mapper.readTree(Files.readString(path, StandardCharsets.UTF_8)).path("names");
            Map<String, String> names = new LinkedHashMap<>();
            if (namesNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = namesNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    if (entry.getValue().isTextual()) {
                        names.put(entry.getKey(), entry.getValue().asText());
                    }
                }
            }
            return new AgentNameRegistry(names);
        } catch (Exception e) {
            throw new IllegalStateException("failed to load agent name registry: " + safeName, e);
        }
    }

    @Override
    public synchronized AgentNameRegistry saveRegistry(String teamName, AgentNameRegistry registry) {
        String safeName = validator.validate(teamName, "teamName");
        AgentNameRegistry safeRegistry = registry == null ? AgentNameRegistry.empty() : registry;
        ObjectNode root = mapper.createObjectNode();
        root.put("version", 1);
        ObjectNode names = root.putObject("names");
        safeRegistry.names().forEach(names::put);
        try {
            Files.createDirectories(paths.teamDir(safeName));
            writeJson(registryFile(safeName), root);
            return safeRegistry;
        } catch (IOException e) {
            throw new IllegalStateException("failed to save agent name registry: " + safeName, e);
        }
    }

    private void migrateLegacyRoot() {
        Path legacyRoot = paths.legacyRoot();
        Path root = paths.root();
        if (legacyRoot == null || legacyRoot.equals(root) || !Files.isDirectory(legacyRoot)) {
            return;
        }
        try (var stream = Files.walk(legacyRoot)) {
            List<Path> sources = stream.toList();
            for (Path source : sources) {
                Path relative = legacyRoot.relativize(source);
                Path target = root.resolve(relative).normalize();
                if (!target.startsWith(root)) {
                    throw new IllegalStateException("legacy team path escapes managed root");
                }
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else if (!Files.exists(target)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to migrate legacy teams: " + legacyRoot, e);
        }
    }

    private Path teamFile(String teamName) {
        return paths.teamDir(teamName).resolve(TEAM_FILE).normalize();
    }

    private Path registryFile(String teamName) {
        return paths.teamDir(teamName).resolve(REGISTRY_FILE).normalize();
    }

    private TeamRecord readTeam(JsonNode node, Path directory) {
        Map<String, TeamMemberRecord> members = new LinkedHashMap<>();
        JsonNode membersNode = node.path("members");
        if (membersNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = membersNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                members.put(entry.getKey(), readMember(entry.getValue()));
            }
        }
        return new TeamRecord(
                text(node, "name"),
                Path.of(text(node, "repoRoot")),
                optionalText(node, "leadAgentId"),
                members,
                directory,
                instant(node, "createdAt"),
                instant(node, "updatedAt")
        );
    }

    private ObjectNode writeTeam(TeamRecord team) {
        ObjectNode node = mapper.createObjectNode();
        node.put("version", 1);
        node.put("name", team.name());
        node.put("repoRoot", team.repoRoot().toString());
        team.leadAgentId().ifPresent(value -> node.put("leadAgentId", value));
        node.put("directory", team.directory().toString());
        node.put("createdAt", team.createdAt().toString());
        node.put("updatedAt", team.updatedAt().toString());
        ObjectNode members = node.putObject("members");
        team.members().forEach((name, member) -> members.set(name, writeMember(member)));
        return node;
    }

    private TeamMemberRecord readMember(JsonNode node) {
        return new TeamMemberRecord(
                text(node, "name"),
                text(node, "agentId"),
                text(node, "role"),
                enumValue(TeamMemberBackendKind.class, text(node, "backend"), TeamMemberBackendKind.SAME_PROCESS),
                enumValue(TeamMemberLaunchMode.class, text(node, "launchMode"), TeamMemberLaunchMode.GENERAL_PURPOSE),
                optionalText(node, "agentType"),
                node.path("planModeRequired").asBoolean(false),
                optionalText(node, "worktreeName"),
                optionalText(node, "worktreePath").map(Path::of),
                optionalText(node, "worktreeBranch"),
                enumValue(TeamMemberStatus.class, text(node, "status"), TeamMemberStatus.IDLE),
                optionalText(node, "contextPath").map(Path::of),
                instant(node, "createdAt"),
                instant(node, "updatedAt")
        );
    }

    private ObjectNode writeMember(TeamMemberRecord member) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", member.name());
        node.put("agentId", member.agentId());
        node.put("role", member.role());
        node.put("backend", member.backend().name());
        node.put("launchMode", member.launchMode().name());
        member.agentType().ifPresent(value -> node.put("agentType", value));
        node.put("planModeRequired", member.planModeRequired());
        member.worktreeName().ifPresent(value -> node.put("worktreeName", value));
        member.worktreePath().ifPresent(value -> node.put("worktreePath", value.toString()));
        member.worktreeBranch().ifPresent(value -> node.put("worktreeBranch", value));
        node.put("status", member.status().name());
        member.contextPath().ifPresent(value -> node.put("contextPath", value.toString()));
        node.put("createdAt", member.createdAt().toString());
        node.put("updatedAt", member.updatedAt().toString());
        return node;
    }

    private void writeJson(Path path, ObjectNode root) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private Optional<String> optionalText(JsonNode node, String field) {
        String value = text(node, field);
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        return value.isBlank() ? Instant.now() : Instant.parse(value);
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
