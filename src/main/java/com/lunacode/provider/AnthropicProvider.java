package com.lunacode.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.stream.AnthropicStreamMapper;
import com.lunacode.stream.SseParser;
import com.lunacode.stream.StreamEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Stream;

public class AnthropicProvider implements ChatProvider {
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AnthropicStreamMapper streamMapper = new AnthropicStreamMapper();

    public AnthropicProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config) {
        return streamChat(messages, config, mapper.createArrayNode(), null);
    }

    @Override
    public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config, ArrayNode enabledTools) {
        return streamChat(messages, config, enabledTools, null);
    }

    @Override
    public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config, ArrayNode enabledTools, String systemPrompt) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint(config.baseUrl(), "/v1/messages"))
                    .header("x-api-key", config.apiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(messages, config, enabledTools, systemPrompt)))
                    .build();
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                return Stream.of(new StreamEvent.Error("Anthropic 请求失败，HTTP 状态码: " + response.statusCode(), null));
            }
            SseParser parser = new SseParser();
            return response.body()
                    .flatMap(line -> parser.accept(line).stream())
                    .flatMap(event -> streamMapper.map(event).stream())
                    .onClose(response.body()::close);
        } catch (Exception e) {
            return Stream.of(new StreamEvent.Error("Anthropic 请求失败", e));
        }
    }

    String buildRequestBody(List<ApiMessage> messages, ProviderConfig config) throws Exception {
        return buildRequestBody(messages, config, mapper.createArrayNode(), null);
    }

    String buildRequestBody(List<ApiMessage> messages, ProviderConfig config, ArrayNode enabledTools) throws Exception {
        return buildRequestBody(messages, config, enabledTools, null);
    }

    String buildRequestBody(List<ApiMessage> messages, ProviderConfig config, ArrayNode enabledTools, String systemPrompt) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", config.model());
        root.put("stream", true);
        root.put("max_tokens", DEFAULT_MAX_TOKENS);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            root.put("system", systemPrompt);
        }
        ArrayNode messageArray = root.putArray("messages");
        for (ApiMessage message : messages) {
            ObjectNode item = messageArray.addObject();
            item.put("role", message.role());
            writeContent(item, message);
        }
        if (enabledTools != null && !enabledTools.isEmpty()) {
            root.set("tools", enabledTools);
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

    private URI endpoint(URI baseUrl, String suffix) {
        String base = baseUrl.toString();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/v1")) {
            return URI.create(base + suffix.substring(3));
        }
        return URI.create(base + suffix);
    }
}
