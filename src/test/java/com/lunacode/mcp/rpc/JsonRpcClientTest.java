package com.lunacode.mcp.rpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.mcp.transport.McpTransport;
import com.lunacode.mcp.transport.McpTransportListener;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class JsonRpcClientTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void pairsOutOfOrderResponsesById() throws Exception {
        FakeTransport transport = new FakeTransport();
        JsonRpcClient client = new JsonRpcClient(transport, mapper);

        CompletableFuture<JsonNode> first = client.request("one", mapper.createObjectNode(), Duration.ofSeconds(1));
        CompletableFuture<JsonNode> second = client.request("two", mapper.createObjectNode(), Duration.ofSeconds(1));
        String firstId = transport.sent.get(0).path("id").asText();
        String secondId = transport.sent.get(1).path("id").asText();

        client.onMessage(response(secondId, "second"));
        client.onMessage(response(firstId, "first"));

        assertEquals("first", first.get().path("value").asText());
        assertEquals("second", second.get().path("value").asText());
        client.close();
    }

    @Test
    void repliesMethodNotFoundForUnsupportedServerRequest() {
        FakeTransport transport = new FakeTransport();
        JsonRpcClient client = new JsonRpcClient(transport, mapper);
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", "server-1");
        request.put("method", "unsupported");

        client.onMessage(request);

        JsonNode reply = transport.sent.get(0);
        assertEquals("server-1", reply.path("id").asText());
        assertEquals(-32601, reply.path("error").path("code").asInt());
        client.close();
    }

    private ObjectNode response(String id, String value) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.putObject("result").put("value", value);
        return response;
    }

    private static final class FakeTransport implements McpTransport {
        private final List<JsonNode> sent = new ArrayList<>();

        @Override public String serverName() { return "fake"; }
        @Override public CompletableFuture<Void> start(McpTransportListener listener) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> send(JsonNode message) {
            sent.add(message.deepCopy());
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> closeAsync() { return CompletableFuture.completedFuture(null); }
    }
}
