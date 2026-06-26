package com.lunacode.tool;

import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.List;
import java.util.Optional;

public interface ToolRegistry {
    void register(Tool tool);

    void enable(String name);

    void disable(String name);

    Optional<Tool> get(String name);

    List<Tool> getEnabledTools();

    ArrayNode toAPIFormat();
}
