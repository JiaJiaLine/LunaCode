package com.lunacode.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.agent.PromptBundle;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.stream.OpenAiStreamMapper;
import com.lunacode.stream.SseParser;
import com.lunacode.stream.StreamEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Stream;

public class OpenAiProvider implements ChatProvider {
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenAiStreamMapper streamMapper = new OpenAiStreamMapper();
    private final OpenAiPromptAdapter promptAdapter = new OpenAiPromptAdapter();

    public OpenAiProvider(HttpClient httpClient) {
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
            HttpRequest request = HttpRequest.newBuilder(endpoint(config.baseUrl(), "/v1/chat/completions"))
                    .header("authorization", "Bearer " + config.apiKey())
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(messages, config, enabledTools, systemPrompt)))
                    .build();
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                return Stream.of(new StreamEvent.Error("OpenAI request failed, HTTP status: " + response.statusCode(), null));
            }
            SseParser parser = new SseParser();
            return response.body()
                    .flatMap(line -> parser.accept(line).stream())
                    .flatMap(event -> streamMapper.map(event).stream())
                    .onClose(response.body()::close);
        } catch (Exception e) {
            return Stream.of(new StreamEvent.Error("OpenAI request failed", e));
        }
    }

    @Override
    public Stream<StreamEvent> streamChat(PromptBundle promptBundle, ProviderConfig config) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint(config.baseUrl(), "/v1/chat/completions"))
                    .header("authorization", "Bearer " + config.apiKey())
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(promptBundle, config)))
                    .build();
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                return Stream.of(new StreamEvent.Error("OpenAI request failed, HTTP status: " + response.statusCode(), null));
            }
            SseParser parser = new SseParser();
            return response.body()
                    .flatMap(line -> parser.accept(line).stream())
                    .flatMap(event -> streamMapper.map(event).stream())
                    .onClose(response.body()::close);
        } catch (Exception e) {
            return Stream.of(new StreamEvent.Error("OpenAI request failed", e));
        }
    }

    String buildRequestBody(PromptBundle promptBundle, ProviderConfig config) throws Exception {
        return promptAdapter.buildRequestBody(promptBundle, config);
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
        ObjectNode streamOptions = root.putObject("stream_options");
        streamOptions.put("include_usage", true);
        ArrayNode messageArray = root.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode item = messageArray.addObject();
            item.put("role", "system");
            item.put("content", systemPrompt);
        }
        for (ApiMessage message : messages) {
            ObjectNode item = messageArray.addObject();
            item.put("role", message.role());
            item.put("content", message.textContent());
        }
        if (enabledTools != null && !enabledTools.isEmpty()) {
            root.set("tools", toOpenAiTools(enabledTools));
            root.put("tool_choice", "auto");
        }
        return mapper.writeValueAsString(root);
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
