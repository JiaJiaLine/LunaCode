package com.lunacode.skill;

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

public final class DefaultSkillCatalog implements SkillCatalog {
    private final List<SkillSource> sources;
    private final SkillParser parser;
    private final Path projectRoot;
    private final Path userHome;
    private final Supplier<Set<String>> reservedCommandNames;
    private final Supplier<Set<String>> availableToolNames;
    private final Map<String, SkillDefinition> cacheBySourceId = new LinkedHashMap<>();
    private SkillCatalogSnapshot lastSnapshot = new SkillCatalogSnapshot(List.of(), List.of());
    private Map<String, SkillDefinition> activeDefinitions = Map.of();

    public DefaultSkillCatalog(
            List<SkillSource> sources,
            SkillParser parser,
            Path projectRoot,
            Path userHome,
            Supplier<Set<String>> reservedCommandNames,
            Supplier<Set<String>> availableToolNames
    ) {
        this.sources = sources == null ? List.of() : List.copyOf(sources);
        this.parser = parser == null ? new FrontmatterSkillParser() : parser;
        this.projectRoot = projectRoot == null ? Path.of("").toAbsolutePath().normalize() : projectRoot.toAbsolutePath().normalize();
        this.userHome = userHome == null ? Path.of(System.getProperty("user.home")).toAbsolutePath().normalize() : userHome.toAbsolutePath().normalize();
        this.reservedCommandNames = reservedCommandNames == null ? Set::of : reservedCommandNames;
        this.availableToolNames = availableToolNames == null ? Set::of : availableToolNames;
        rebuild();
    }

    @Override
    public synchronized SkillCatalogSnapshot snapshot() {
        rebuild();
        return lastSnapshot;
    }

    @Override
    public synchronized Optional<SkillDefinition> loadForExecution(String name) {
        rebuild();
        return Optional.ofNullable(activeDefinitions.get(normalizeSkillName(name)));
    }

    @Override
    public synchronized List<SkillDiagnostic> diagnostics() {
        return lastSnapshot.diagnostics();
    }

    private void rebuild() {
        List<SkillDiagnostic> diagnostics = new ArrayList<>();
        Map<String, SkillDefinition> merged = new LinkedHashMap<>();
        List<SkillCandidate> candidates = discoverCandidates();
        Set<String> reserved = normalizeNames(safeGet(reservedCommandNames));
        Set<String> availableTools = normalizeNames(safeGet(availableToolNames));

        for (SkillCandidate candidate : candidates) {
            SkillParseResult result = parse(candidate);
            SkillDefinition definition = null;
            if (result instanceof SkillParseResult.Success success) {
                SkillDefinition parsed = success.definition();
                Optional<SkillDiagnostic> invalid = invalidReason(parsed, reserved, availableTools);
                if (invalid.isPresent()) {
                    diagnostics.add(invalid.get());
                    continue;
                }
                cacheBySourceId.put(candidate.origin().sourceId(), parsed);
                definition = parsed;
            } else if (result instanceof SkillParseResult.Failure failure) {
                SkillDefinition cached = cacheBySourceId.get(candidate.origin().sourceId());
                if (cached == null) {
                    diagnostics.add(new SkillDiagnostic(
                            SkillDiagnosticLevel.WARNING,
                            candidate.origin().sourceId(),
                            "Skill 解析失败，已跳过: " + failure.reason()
                    ));
                    continue;
                }
                diagnostics.add(new SkillDiagnostic(
                        SkillDiagnosticLevel.WARNING,
                        candidate.origin().sourceId(),
                        "Skill 解析失败，已回退到上一次成功解析版本: " + failure.reason()
                ));
                definition = cached;
            }

            if (definition != null) {
                merged.put(normalizeSkillName(definition.name()), definition);
            }
        }

        List<SkillSummary> summaries = merged.values().stream()
                .map(SkillDefinition::summary)
                .sorted(Comparator.comparing(SkillSummary::name))
                .toList();
        this.activeDefinitions = Map.copyOf(merged);
        this.lastSnapshot = new SkillCatalogSnapshot(summaries, diagnostics);
    }

    private List<SkillCandidate> discoverCandidates() {
        List<SkillCandidate> result = new ArrayList<>();
        for (SkillSource source : sources) {
            result.addAll(source.discover(projectRoot, userHome));
        }
        return result.stream()
                .sorted(Comparator
                        .comparingInt((SkillCandidate candidate) -> candidate.origin().priority())
                        .thenComparing(candidate -> candidate.origin().sourceId()))
                .toList();
    }

    private SkillParseResult parse(SkillCandidate candidate) {
        return switch (candidate.kind()) {
            case SINGLE_FILE -> parser.parseSingleFile(candidate.path().orElseThrow(), candidate.origin());
            case DIRECTORY -> parser.parseDirectory(candidate.path().orElseThrow(), candidate.origin());
            case BUILTIN -> parser.parseBuiltin(
                    candidate.resourceName().orElse(candidate.origin().sourceId()),
                    candidate.content().orElse(""),
                    candidate.origin()
            );
        };
    }

    private Optional<SkillDiagnostic> invalidReason(
            SkillDefinition definition,
            Set<String> reserved,
            Set<String> availableTools
    ) {
        String commandName = normalizeSkillName(definition.name());
        if (reserved.contains(commandName)) {
            return Optional.of(new SkillDiagnostic(
                    SkillDiagnosticLevel.WARNING,
                    definition.origin().sourceId(),
                    "Skill 名称与内置斜杠命令冲突，已跳过: /" + definition.name()
            ));
        }
        List<String> missingTools = definition.tools().stream()
                .filter(tool -> !availableTools.contains(normalizeToolName(tool)))
                .toList();
        if (!missingTools.isEmpty()) {
            return Optional.of(new SkillDiagnostic(
                    SkillDiagnosticLevel.WARNING,
                    definition.origin().sourceId(),
                    "Skill tools 引用了不存在的工具，已跳过: " + String.join(", ", missingTools)
            ));
        }
        return Optional.empty();
    }

    private Set<String> safeGet(Supplier<Set<String>> supplier) {
        try {
            Set<String> values = supplier.get();
            return values == null ? Set.of() : values;
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    private Set<String> normalizeNames(Set<String> values) {
        return values.stream()
                .map(this::normalizeSkillName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeSkillName(String value) {
        String stripped = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return stripped.startsWith("/") ? stripped.substring(1) : stripped;
    }

    private String normalizeToolName(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
