package com.lunacode.hook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpHookActionExecutor implements HookActionExecutor {
    private final HttpClient client;

    public HttpHookActionExecutor() {
        this(HttpClient.newHttpClient());
    }

    public HttpHookActionExecutor(HttpClient client) {
        this.client = client == null ? HttpClient.newHttpClient() : client;
    }

    @Override
    public HookActionResult execute(HookDefinition hook, HookContext context, HookExecutionScope scope) {
        if (!(hook.action() instanceof HookAction.Http http)) {
            return HookActionResult.failure("Hook action 不是 http", Map.of("errorType", "invalid_action"));
        }
        try {
            Duration timeout = http.timeout().or(() -> hook.timeout()).orElse(Duration.ofSeconds(30));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(new URI(resolve(http.url().toString(), context)))
                    .timeout(timeout);
            http.headers().forEach((key, value) -> builder.header(key, resolve(value, context)));
            String body = resolve(http.body(), context);
            if (body.isBlank() && ("GET".equalsIgnoreCase(http.method()) || "DELETE".equalsIgnoreCase(http.method()))) {
                builder.method(http.method(), HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(http.method(), HttpRequest.BodyPublishers.ofString(body));
            }
            long started = System.nanoTime();
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long durationMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("statusCode", response.statusCode());
            metadata.put("durationMillis", durationMillis);
            metadata.put("uri", response.uri().toString());
            return new HookActionResult(response.statusCode() >= 200 && response.statusCode() < 400, response.body(), metadata);
        } catch (Exception e) {
            return HookActionResult.failure("HTTP Hook 执行失败: " + e.getMessage(), e);
        }
    }

    private String resolve(String template, HookContext context) {
        String result = template == null ? "" : template;
        HookContext safe = context == null ? HookContext.empty(null) : context;
        for (Map.Entry<String, String> entry : safe.variables().entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
