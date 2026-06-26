package com.lunacode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DefaultToolRegistry implements ToolRegistry {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Set<String> enabled = new LinkedHashSet<>();

    @Override
    public synchronized void register(Tool tool) {
        if (tools.containsKey(tool.name())) {
            throw new IllegalArgumentException("工具已注册: " + tool.name());
        }
        tools.put(tool.name(), tool);
        enabled.add(tool.name());
    }

    @Override
    public synchronized void enable(String name) {
        if (!tools.containsKey(name)) {
            throw new IllegalArgumentException("工具不存在: " + name);
        }
        enabled.add(name);
    }

    @Override
    public synchronized void disable(String name) {
        enabled.remove(name);
    }

    @Override
    public synchronized Optional<Tool> get(String name) {
        if (!enabled.contains(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name));
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
        ArrayNode array = mapper.createArrayNode();
        for (Tool tool : getEnabledTools()) {
            ObjectNode item = array.addObject();
            item.put("name", tool.name());
            item.put("description", tool.description());
            item.set("input_schema", tool.inputSchema());
        }
        return array;
    }
}
