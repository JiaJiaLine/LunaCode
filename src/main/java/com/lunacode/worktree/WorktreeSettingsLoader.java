package com.lunacode.worktree;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class WorktreeSettingsLoader {
    private final ObjectMapper mapper;

    public WorktreeSettingsLoader() {
        this(new ObjectMapper());
    }

    public WorktreeSettingsLoader(ObjectMapper mapper) {
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public WorktreeSettings load(Path repoRoot) {
        Path root = repoRoot.toAbsolutePath().normalize();
        Path settingsFile = root.resolve("settings.local.json");
        if (!Files.isRegularFile(settingsFile)) {
            return WorktreeSettings.defaults();
        }
        List<String> warnings = new ArrayList<>();
        List<String> symlinkDirectories = new ArrayList<>();
        try {
            JsonNode rootNode = mapper.readTree(Files.readString(settingsFile, StandardCharsets.UTF_8));
            JsonNode symlinkNode = rootNode.path("settings").path("worktree").path("symlinkDirectories");
            if (symlinkNode.isMissingNode()) {
                return new WorktreeSettings(List.of("settings.local.json"), List.of(), warnings);
            }
            if (!symlinkNode.isArray()) {
                warnings.add("settings.worktree.symlinkDirectories must be an array");
                return new WorktreeSettings(List.of("settings.local.json"), List.of(), warnings);
            }
            symlinkNode.forEach(value -> {
                if (value.isTextual() && !value.asText().isBlank()) {
                    symlinkDirectories.add(value.asText());
                } else {
                    warnings.add("ignored non-string symlinkDirectories entry");
                }
            });
            return new WorktreeSettings(List.of("settings.local.json"), symlinkDirectories, warnings);
        } catch (IOException e) {
            warnings.add("failed to read settings.local.json: " + e.getMessage());
            return new WorktreeSettings(List.of("settings.local.json"), List.of(), warnings);
        }
    }
}
