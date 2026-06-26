package com.lunacode.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    public OpenAiProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint(config.baseUrl(), "/v1/chat/completions"))
                    .header("authorization", "Bearer " + config.apiKey())
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(messages, config)))
                    .build();
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                return Stream.of(new StreamEvent.Error("OpenAI 请求失败，HTTP 状态码: " + response.statusCode(), null));
            }
            SseParser parser = new SseParser();
            return response.body()
                    .flatMap(line -> parser.accept(line).stream())
                    .flatMap(event -> streamMapper.map(event).stream())
                    .onClose(response.body()::close);
        } catch (Exception e) {
            return Stream.of(new StreamEvent.Error("OpenAI 请求失败", e));
        }
    }

    String buildRequestBody(List<ApiMessage> messages, ProviderConfig config) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", config.model());
        root.put("stream", true);
        ObjectNode streamOptions = root.putObject("stream_options");
        streamOptions.put("include_usage", true);
        ArrayNode messageArray = root.putArray("messages");
        for (ApiMessage message : messages) {
            ObjectNode item = messageArray.addObject();
            item.put("role", message.role());
            item.put("content", message.textContent());
        }
        return mapper.writeValueAsString(root);
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
