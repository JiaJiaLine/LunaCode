package com.lunacode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.agent.AgentMode;

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
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Map<String, String> aliases = new HashMap<>();
    private final Set<String> enabled = new LinkedHashSet<>();

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
        Optional<String> canonical = resolveName(name);
        if (canonical.isEmpty() || !enabled.contains(canonical.get())) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(canonical.get()));
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
    public synchronized ArrayNode toAPIFormat() {
        return toAPIFormat(AgentMode.DEFAULT);
    }

    @Override
    public synchronized ArrayNode toAPIFormat(AgentMode mode) {
        ArrayNode array = mapper.createArrayNode();
        for (Tool tool : getEnabledTools()) {
            if (mode != AgentMode.PLAN && "AskUserQuestion".equals(tool.name())) {
                continue;
            }
            ObjectNode item = array.addObject();
            item.put("name", tool.name());
            item.put("description", tool.description());
            item.set("input_schema", tool.inputSchema());
        }
        return array;
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
}
