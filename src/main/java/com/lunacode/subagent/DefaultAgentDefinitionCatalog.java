package com.lunacode.subagent;

import com.lunacode.config.AgentConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class DefaultAgentDefinitionCatalog implements AgentDefinitionCatalog {
    private final List<AgentDefinitionSource> sources;
    private final AgentDefinitionParser parser;
    private final Path projectRoot;
    private final Path userHome;
    private final Supplier<Set<String>> availableToolNames;
    private final AgentConfig agentConfig;
    private AgentDefinitionCatalogSnapshot lastSnapshot = new AgentDefinitionCatalogSnapshot(List.of(), List.of());
    private Map<String, AgentDefinition> activeDefinitions = Map.of();

    public DefaultAgentDefinitionCatalog(
            List<AgentDefinitionSource> sources,
            AgentDefinitionParser parser,
            Path projectRoot,
            Path userHome,
            Supplier<Set<String>> availableToolNames,
            AgentConfig agentConfig
    ) {
        this.sources = sources == null ? List.of() : List.copyOf(sources);
        this.parser = parser == null ? new FrontmatterAgentDefinitionParser() : parser;
        this.projectRoot = projectRoot == null ? Path.of("").toAbsolutePath().normalize() : projectRoot.toAbsolutePath().normalize();
        this.userHome = userHome == null ? Path.of(System.getProperty("user.home")).toAbsolutePath().normalize() : userHome.toAbsolutePath().normalize();
        this.availableToolNames = availableToolNames == null ? Set::of : availableToolNames;
        this.agentConfig = agentConfig == null ? AgentConfig.defaults() : agentConfig;
        rebuild();
    }

    @Override
    public synchronized AgentDefinitionCatalogSnapshot snapshot() {
        rebuild();
        return lastSnapshot;
    }

    @Override
    public synchronized Optional<AgentDefinition> find(String agentType) {
        rebuild();
        return Optional.ofNullable(activeDefinitions.get(normalizeAgentType(agentType)));
    }

    @Override
    public synchronized List<AgentDefinitionDiagnostic> diagnostics() {
        return snapshot().diagnostics();
    }

    private void rebuild() {
        List<AgentDefinitionDiagnostic> diagnostics = new ArrayList<>();
        Map<String, AgentDefinition> merged = new LinkedHashMap<>();
        Set<String> availableTools = normalizeTools(safeGet(availableToolNames));
        availableTools = withBuiltInAliases(availableTools);

        for (AgentDefinitionCandidate candidate : discoverCandidates()) {
            AgentDefinitionParseResult result = parser.parse(candidate);
            if (result instanceof AgentDefinitionParseResult.Failure failure) {
                diagnostics.add(new AgentDefinitionDiagnostic(
                        DiagnosticLevel.WARNING,
                        candidate.sourceId(),
                        "Agent 定义解析失败，已跳过: " + failure.reason()
                ));
                continue;
            }
            AgentDefinition definition = ((AgentDefinitionParseResult.Success) result).definition();
            Optional<String> invalid = invalidReason(definition, availableTools);
            if (invalid.isPresent()) {
                diagnostics.add(new AgentDefinitionDiagnostic(
                        DiagnosticLevel.WARNING,
                        definition.filePath().toString(),
                        invalid.get()
                ));
                continue;
            }
            merged.put(normalizeAgentType(definition.agentType()), definition);
        }
        List<AgentDefinition> definitions = merged.values().stream()
                .sorted(Comparator.comparing(AgentDefinition::agentType))
                .toList();
        this.activeDefinitions = Map.copyOf(merged);
        this.lastSnapshot = new AgentDefinitionCatalogSnapshot(definitions, diagnostics);
    }

    private List<AgentDefinitionCandidate> discoverCandidates() {
        List<AgentDefinitionCandidate> result = new ArrayList<>();
        for (AgentDefinitionSource source : sources) {
            result.addAll(source.discover(projectRoot, userHome));
        }
        return result.stream()
                .sorted(Comparator
                        .comparingInt((AgentDefinitionCandidate candidate) -> candidate.source().priority())
                        .thenComparing(AgentDefinitionCandidate::sourceId))
                .toList();
    }

    private Optional<String> invalidReason(AgentDefinition definition, Set<String> availableTools) {
        List<String> missingTools = new ArrayList<>();
        for (String tool : definition.tools()) {
            if (!availableTools.contains(normalizeToolName(tool))) {
                missingTools.add(tool);
            }
        }
        for (String tool : definition.disallowedTools()) {
            if (!availableTools.contains(normalizeToolName(tool))) {
                missingTools.add(tool);
            }
        }
        if (!missingTools.isEmpty()) {
            return Optional.of("Agent tools 引用了不存在的工具，已跳过: " + String.join(", ", missingTools));
        }
        String model = definition.model();
        if (isConfiguredAlias(model) && !agentConfig.modelAliases().containsKey(model.toLowerCase(Locale.ROOT))) {
            return Optional.of("Agent model 别名未配置，已跳过: " + model);
        }
        return Optional.empty();
    }

    private boolean isConfiguredAlias(String model) {
        String normalized = model == null ? "" : model.strip().toLowerCase(Locale.ROOT);
        return normalized.equals("sonnet") || normalized.equals("opus") || normalized.equals("haiku");
    }

    private Set<String> safeGet(Supplier<Set<String>> supplier) {
        try {
            Set<String> values = supplier.get();
            return values == null ? Set.of() : values;
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    private Set<String> normalizeTools(Set<String> values) {
        return values.stream()
                .map(this::normalizeToolName)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> withBuiltInAliases(Set<String> tools) {
        Set<String> result = new LinkedHashSet<>(tools);
        result.addAll(List.of("agent", "read", "readfile", "write", "writefile", "edit", "editfile", "bash", "grep", "glob", "askuserquestion", "loadskill", "notebookedit"));
        return result;
    }

    private String normalizeAgentType(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeToolName(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "read" -> "readfile";
            case "write" -> "writefile";
            case "edit" -> "editfile";
            default -> normalized;
        };
    }
}
