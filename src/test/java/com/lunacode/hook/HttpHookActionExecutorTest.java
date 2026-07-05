package com.lunacode.hook;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpHookActionExecutorTest {
    @Test
    void sendsConfiguredRequestWithContextVariables() throws Exception {
        AtomicReference<String> body = new AtomicReference<>("");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
            HookDefinition hook = new HookDefinition(
                    "h1",
                    new HookSource(HookSourceLevel.PROJECT, java.nio.file.Path.of("config.yaml")),
                    1,
                    HookEventName.POST_TOOL_USE,
                    Optional.empty(),
                    new HookAction.Http(uri, "POST", Map.of("Content-Type", "text/plain"), "tool=${toolName};path=${args.path}", Optional.of(Duration.ofSeconds(3))),
                    false,
                    false,
                    false,
                    Optional.empty(),
                    false
            );
            HookContext context = new HookContext("post_tool_use", "WriteFile", Map.of("path", "a.txt"), "a.txt", "", "");

            HookActionResult result = new HttpHookActionExecutor().execute(hook, context, new HookExecutionScope("s1", 1, java.nio.file.Path.of(".")));

            assertTrue(result.success(), result.output());
            assertEquals("tool=WriteFile;path=a.txt", body.get());
        } finally {
            server.stop(0);
        }
    }
}
