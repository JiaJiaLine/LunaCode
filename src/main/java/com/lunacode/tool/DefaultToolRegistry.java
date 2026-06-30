package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.runtime.AgentMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DefaultToolRegistry implements ToolRegistry {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ToolDescriptionEnhancer descriptionEnhancer = new ToolDescriptionEnhancer();
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Map<String, String> aliases = new HashMap<>();
    private final Set<String> enabled = new LinkedHashSet<>();
    private final Set<String> discoveredDeferred = new LinkedHashSet<>();

    @Override
    public synchronized void register(Tool tool) {
        if (tools.containsKey(tool.name())) {
            throw new IllegalArgumentException("工具已注册: " + tool.name());
        }
        tools.put(tool.name(), tool);
        registerNameAlias(tool.name(), tool.name());
        registerCommonAliases(tool.name());
        enabled.add(tool.name());
    }

    @Override
    public synchronized void enable(String name) {
        String canonical = resolveName(name).orElse(name);
        if (!tools.containsKey(canonical)) {
            throw new IllegalArgumentException("工具不存在: " + name);
        }
        enabled.add(canonical);
    }

    @Override
    public synchronized void disable(String name) {
        resolveName(name).ifPresentOrElse(enabled::remove, () -> enabled.remove(name));
    }

    @Override
    public synchronized Optional<Tool> get(String name) {
        Optional<String> canonical = resolveEnabledName(name);
        if (canonical.isEmpty()) {
            return Optional.empty();
        }
        Tool tool = tools.get(canonical.get());
        if (tool != null && tool.shouldDefer() && !discoveredDeferred.contains(canonical.get())) {
            return Optional.empty();
        }
        return Optional.ofNullable(tool);
    }

    @Override
    public synchronized Optional<Tool> getRegistered(String name) {
        return resolveEnabledName(name).map(tools::get);
    }

    @Override
    public synchronized List<Tool> getEnabledTools() {
        List<Tool> result = new ArrayList<>();
        for (String name : enabled) {
            Tool tool = tools.get(name);
            if (tool != null) {
                result.add(tool);
            }
        }
        return List.copyOf(result);
    }

    @Override
    public synchronized Optional<ToolDefinitionSnapshot> discoverDeferredTool(String name) {
        Optional<String> canonical = resolveEnabledName(name);
        if (canonical.isEmpty()) {
            return Optional.empty();
        }
        Tool tool = tools.get(canonical.get());
        if (tool == null || !tool.shouldDefer()) {
            return Optional.empty();
        }
        discoveredDeferred.add(canonical.get());
        return Optional.of(new ToolDefinitionSnapshot(
                tool.name(),
                descriptionEnhancer.enhance(tool),
                tool.inputSchema()
        ));
    }

    @Override
    public synchronized boolean isDeferredDiscovered(String name) {
        Optional<String> canonical = resolveName(name);
        return canonical.isPresent() && discoveredDeferred.contains(canonical.get());
    }

    @Override
    public synchronized List<DeferredToolSummary> deferredToolSummaries() {
        List<DeferredToolSummary> result = new ArrayList<>();
        for (String name : enabled) {
            Tool tool = tools.get(name);
            if (tool != null && tool.shouldDefer() && !discoveredDeferred.contains(name)) {
                result.add(new DeferredToolSummary(
                        tool.name(),
                        limit(descriptionEnhancer.enhance(tool), 240),
                        tool.category()
                ));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public synchronized ToolDeclarationSet declarationsForModel(AgentMode mode) {
        return new ToolDeclarationSet(visibleTools(mode), deferredToolSummaries());
    }

    @Override
    public synchronized ArrayNode toAPIFormat() {
        return toAPIFormat(AgentMode.DEFAULT);
    }

    @Override
    public synchronized ArrayNode toAPIFormat(AgentMode mode) {
        return visibleTools(mode);
    }

    private ArrayNode visibleTools(AgentMode mode) {
        ArrayNode array = mapper.createArrayNode();
        for (String name : enabled) {
            Tool tool = tools.get(name);
            if (tool == null) {
                continue;
            }
            if (mode != AgentMode.PLAN && "AskUserQuestion".equals(tool.name())) {
                continue;
            }
            if (tool.shouldDefer() && !discoveredDeferred.contains(name)) {
                continue;
            }
            array.add(toApiNode(tool));
        }
        return array;
    }

    private ObjectNode toApiNode(Tool tool) {
        ObjectNode item = mapper.createObjectNode();
        item.put("name", tool.name());
        item.put("description", descriptionEnhancer.enhance(tool));
        JsonNode schema = tool.inputSchema();
        item.set("input_schema", schema == null ? mapper.createObjectNode().put("type", "object") : schema);
        return item;
    }

    private Optional<String> resolveEnabledName(String name) {
        Optional<String> canonical = resolveName(name);
        if (canonical.isEmpty() || !enabled.contains(canonical.get())) {
            return Optional.empty();
        }
        return canonical;
    }

    private Optional<String> resolveName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        if (tools.containsKey(name)) {
            return Optional.of(name);
        }
        return Optional.ofNullable(aliases.get(normalize(name)));
    }

    private void registerNameAlias(String alias, String canonical) {
        aliases.put(normalize(alias), canonical);
    }

    private void registerCommonAliases(String canonical) {
        switch (canonical) {
            case "ReadFile" -> registerAliases(canonical, "read", "read_file", "readfile");
            case "WriteFile" -> registerAliases(canonical, "write", "write_file", "writefile");
            case "EditFile" -> registerAliases(canonical, "edit", "edit_file", "editfile");
            case "Bash" -> registerAliases(canonical, "bash", "shell", "run_command", "command");
            case "Glob" -> registerAliases(canonical, "glob", "find_files", "list_files");
            case "Grep" -> registerAliases(canonical, "grep", "search", "search_files");
            case "AskUserQuestion" -> registerAliases(canonical, "ask_user_question", "ask", "question");
            default -> {
            }
        }
    }

    private void registerAliases(String canonical, String... names) {
        for (String name : names) {
            registerNameAlias(name, canonical);
        }
    }

    private String normalize(String name) {
        return name.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    private String limit(String value, int maxChars) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars)) + "...";
    }
}
