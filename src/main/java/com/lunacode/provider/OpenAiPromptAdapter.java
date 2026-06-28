package com.lunacode.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.agent.PromptBundle;
import com.lunacode.agent.SystemReminder;
import com.lunacode.agent.SystemReminderRenderer;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ApiMessage;

public final class OpenAiPromptAdapter implements ProviderPromptAdapter {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SystemReminderRenderer reminderRenderer = new SystemReminderRenderer();

    @Override
    public String buildRequestBody(PromptBundle promptBundle, ProviderConfig config) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", config.model());
        root.put("stream", true);
        root.putObject("stream_options").put("include_usage", true);
        ArrayNode messageArray = root.putArray("messages");
        addMessage(messageArray, "system", promptBundle.system().staticPrompt().render());
        addMessage(messageArray, "developer", promptBundle.system().environmentContext().render());
        for (SystemReminder reminder : promptBundle.messages().reminders()) {
            addMessage(messageArray, "system", reminderRenderer.render(reminder));
        }
        for (ApiMessage message : promptBundle.messages().history()) {
            addMessage(messageArray, message.role(), message.textContent());
        }
        if (!promptBundle.toolDeclarations().isEmpty()) {
            root.set("tools", toOpenAiTools(promptBundle.toolDeclarations()));
            root.put("tool_choice", "auto");
        }
        return mapper.writeValueAsString(root);
    }

    private void addMessage(ArrayNode messages, String role, String content) {
        ObjectNode item = messages.addObject();
        item.put("role", role);
        item.put("content", content == null ? "" : content);
    }

    private ArrayNode toOpenAiTools(ArrayNode enabledTools) {
        ArrayNode tools = mapper.createArrayNode();
        for (JsonNode tool : enabledTools) {
            ObjectNode item = tools.addObject();
            item.put("type", "function");
            ObjectNode function = item.putObject("function");
            function.put("name", tool.path("name").asText());
            function.put("description", tool.path("description").asText());
            JsonNode schema = tool.path("input_schema");
            function.set("parameters", schema.isMissingNode() || schema.isNull() ? mapper.createObjectNode().put("type", "object") : schema);
        }
        return tools;
    }
}
