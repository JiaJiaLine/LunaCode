package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.skill.DefaultSkillInvocationPlanner;
import com.lunacode.skill.SkillInvocationPlan;
import com.lunacode.skill.SkillInvocationPlanner;
import com.lunacode.skill.SkillInvocationRequest;
import com.lunacode.skill.SkillInvocationTrigger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class LoadSkillTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SkillInvocationPlanner planner;
    private final JsonNode schema;

    public LoadSkillTool(SkillInvocationPlanner planner) {
        this.planner = Objects.requireNonNull(planner, "planner");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("name", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "要加载完整 SOP 的 Skill 名称"));
        properties.set("arguments", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "传给 Skill 的原始参数，会替换 $ARGUMENTS"));
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "object");
        root.set("properties", properties);
        root.set("required", MAPPER.createArrayNode().add("name"));
        this.schema = root;
    }

    @Override
    public String name() {
        return DefaultSkillInvocationPlanner.LOAD_SKILL_TOOL_NAME;
    }

    @Override
    public String description() {
        return "按名称加载一个 LunaCode Skill 的完整 SOP。默认上下文只展示 Skill 摘要，需要执行某个 Skill 时先调用本工具。";
    }

    @Override
    public JsonNode inputSchema() {
        return schema;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        String name = input.path("name").asText("");
        String arguments = input.path("arguments").asText("");
        try {
            SkillInvocationPlan plan = planner.plan(new SkillInvocationRequest(name, arguments, SkillInvocationTrigger.TOOL));
            StringBuilder content = new StringBuilder();
            content.append("Loaded Skill: /").append(plan.definition().name()).append('\n');
            content.append(plan.renderedPrompt());
            plan.definition().resourceRoot().ifPresent(root -> {
                content.append("\n\nResource root: ").append(root);
                content.append("\nDirectory resources are not preloaded. Read them only when the SOP asks for them.");
            });
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("skillName", plan.definition().name());
            metadata.put("loadedSkill", true);
            plan.definition().resourceRoot().ifPresent(root -> metadata.put("resourceRoot", root.toString()));
            return ToolResult.success(content.toString(), metadata);
        } catch (RuntimeException e) {
            return ToolResult.error("Skill 加载失败: " + e.getMessage(), Map.of(
                    "errorType", "skill_load_failed",
                    "skillName", name
            ));
        }
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isDestructive() {
        return false;
    }

    @Override
    public boolean isConcurrencySafe(JsonNode input) {
        return true;
    }

    @Override
    public String category() {
        return "skill";
    }

    @Override
    public ValidationError validateInput(JsonNode input) {
        if (input == null || !input.hasNonNull("name") || input.path("name").asText().isBlank()) {
            return new ValidationError("missing_name", "LoadSkill 需要 name 参数");
        }
        return null;
    }
}
