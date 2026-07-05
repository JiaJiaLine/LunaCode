package com.lunacode.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.prompt.PromptBundle;
import com.lunacode.prompt.SystemReminder;
import com.lunacode.prompt.SystemReminderRenderer;
import com.lunacode.prompt.SkillPromptRenderer;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ContentBlock;

public final class AnthropicPromptAdapter implements ProviderPromptAdapter {
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final ObjectMapper mapper = new ObjectMapper();
    private final SystemReminderRenderer reminderRenderer = new SystemReminderRenderer();
    private final SkillPromptRenderer skillPromptRenderer = new SkillPromptRenderer();

    @Override
    public String buildRequestBody(PromptBundle promptBundle, ProviderConfig config) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", config.model());
        root.put("stream", true);
        root.put("max_tokens", DEFAULT_MAX_TOKENS);
        root.set("system", systemBlocks(promptBundle));
        ArrayNode messageArray = root.putArray("messages");
        for (SystemReminder reminder : promptBundle.messages().reminders()) {
            ObjectNode item = messageArray.addObject();
            item.put("role", "user");
            item.put("content", reminderRenderer.render(reminder));
        }
        for (ApiMessage message : promptBundle.messages().history()) {
            ObjectNode item = messageArray.addObject();
            item.put("role", message.role());
            writeContent(item, message);
        }
        if (!promptBundle.toolDeclarations().isEmpty()) {
            root.set("tools", tools(promptBundle));
        }
        if (config.thinking().enabled()) {
            ObjectNode thinking = root.putObject("thinking");
            thinking.put("type", "enabled");
            if (config.thinking().budgetTokens() != null) {
                thinking.put("budget_tokens", config.thinking().budgetTokens());
            }
        }
        return mapper.writeValueAsString(root);
    }

    private ArrayNode systemBlocks(PromptBundle promptBundle) {
        ArrayNode system = mapper.createArrayNode();
        ObjectNode staticBlock = system.addObject();
        staticBlock.put("type", "text");
        staticBlock.put("text", promptBundle.system().staticPrompt().render());
        if (promptBundle.cachePolicy().cacheStaticSystemPrompt()) {
            staticBlock.putObject("cache_control").put("type", "ephemeral");
        }
        ObjectNode environmentBlock = system.addObject();
        environmentBlock.put("type", "text");
        environmentBlock.put("text", promptBundle.system().environmentContext().render());
        String skillContext = skillPromptRenderer.render(promptBundle.messages().skillContext());
        if (!skillContext.isBlank()) {
            ObjectNode skillBlock = system.addObject();
            skillBlock.put("type", "text");
            skillBlock.put("text", skillContext);
        }
        promptBundle.messages().projectInstructions().filter(instructions -> !instructions.isEmpty()).ifPresent(instructions -> {
            ObjectNode block = system.addObject();
            block.put("type", "text");
            block.put("text", instructions.render());
        });
        promptBundle.messages().memory().filter(memory -> !memory.isEmpty()).ifPresent(memory -> {
            ObjectNode block = system.addObject();
            block.put("type", "text");
            block.put("text", memory.render());
        });
        return system;
    }

    private ArrayNode tools(PromptBundle promptBundle) {
        ArrayNode tools = mapper.createArrayNode();
        for (JsonNode tool : promptBundle.toolDeclarations()) {
            ObjectNode copy = tool.deepCopy();
            if (promptBundle.cachePolicy().cacheToolDeclarations()) {
                copy.putObject("cache_control").put("type", "ephemeral");
            }
            tools.add(copy);
        }
        return tools;
    }

    private void writeContent(ObjectNode item, ApiMessage message) {
        if (message.content().size() == 1 && message.content().get(0) instanceof ContentBlock.Text text) {
            item.put("content", text.text());
            return;
        }
        ArrayNode content = item.putArray("content");
        for (ContentBlock block : message.content()) {
            if (block instanceof ContentBlock.Text text) {
                ObjectNode node = content.addObject();
                node.put("type", "text");
                node.put("text", text.text());
            } else if (block instanceof ContentBlock.ToolUseBlock toolUse) {
                ObjectNode node = content.addObject();
                node.put("type", "tool_use");
                node.put("id", toolUse.id());
                node.put("name", toolUse.name());
                node.set("input", toolUse.input());
            } else if (block instanceof ContentBlock.ToolResultBlock toolResult) {
                ObjectNode node = content.addObject();
                node.put("type", "tool_result");
                node.put("tool_use_id", toolResult.toolUseId());
                node.put("content", toolResult.content());
                node.put("is_error", toolResult.isError());
            }
        }
    }
}
