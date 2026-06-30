package com.lunacode.mcp;

import com.lunacode.config.McpConfig;
import com.lunacode.config.McpServerConfig;
import com.lunacode.mcp.transport.McpTransport;
import com.lunacode.mcp.transport.McpTransportFactory;
import com.lunacode.tool.McpToolWrapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class McpClientManager implements AutoCloseable {
    private final McpTransportFactory transportFactory;
    private final McpToolNameAllocator nameAllocator;
    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();

    public McpClientManager(Path workspaceRoot, Collection<String> reservedToolNames) {
        this(new McpTransportFactory(workspaceRoot), reservedToolNames);
    }

    public McpClientManager(McpTransportFactory transportFactory, Collection<String> reservedToolNames) {
        this.transportFactory = Objects.requireNonNull(transportFactory, "transportFactory");
        this.nameAllocator = new McpToolNameAllocator(reservedToolNames);
    }

    public McpDiscoveryResult discoverAll(McpConfig config, Duration timeout) {
        if (config == null || config.servers().isEmpty()) {
            return new McpDiscoveryResult(List.of(), List.of(), config == null ? List.of() : config.warnings());
        }
        Duration safeTimeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(8, config.servers().size())), runnable -> {
            Thread thread = new Thread(runnable, "mcp-discovery");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<CompletableFuture<SingleDiscovery>> futures = config.servers().values().stream()
                    .map(server -> CompletableFuture.supplyAsync(() -> discoverOne(server, safeTimeout), executor)
                            .completeOnTimeout(SingleDiscovery.failed(
                                    server.name(),
                                    "MCP Server `" + server.name() + "` 发现超时，已跳过"
                            ), safeTimeout.toMillis(), TimeUnit.MILLISECONDS))
                    .toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            List<McpToolWrapper> tools = new ArrayList<>();
            List<McpServerStatus> statuses = new ArrayList<>();
            List<String> warnings = new ArrayList<>(config.warnings());
            for (CompletableFuture<SingleDiscovery> future : futures) {
                SingleDiscovery discovery = future.join();
                tools.addAll(discovery.tools());
                statuses.add(discovery.status());
                warnings.addAll(discovery.warnings());
            }
            return new McpDiscoveryResult(tools, statuses, warnings);
        } finally {
            executor.shutdownNow();
        }
    }

    public Optional<McpSession> session(String serverName) {
        return Optional.ofNullable(sessions.get(serverName));
    }

    public CompletableFuture<Void> closeAsync() {
        List<CompletableFuture<Void>> closes = sessions.values().stream()
                .map(session -> session.closeAsync().exceptionally(error -> null))
                .toList();
        sessions.clear();
        return CompletableFuture.allOf(closes.toArray(CompletableFuture[]::new));
    }

    @Override
    public void close() {
        closeAsync().join();
    }

    private SingleDiscovery discoverOne(McpServerConfig server, Duration timeout) {
        McpSession session = null;
        try {
            McpTransport transport = transportFactory.create(server);
            session = new McpSession(transport);
            session.initialize(timeout).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            List<McpToolDefinition> definitions = session.listTools(timeout).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            sessions.put(server.name(), session);
            List<McpToolWrapper> wrappers = new ArrayList<>();
            for (McpToolDefinition definition : definitions) {
                String publicName = nameAllocator.allocate(server.name(), definition.originalName());
                wrappers.add(new McpToolWrapper(definition.withPublicName(publicName), session));
            }
            return new SingleDiscovery(
                    wrappers,
                    McpServerStatus.ready(server.name(), "已发现 " + wrappers.size() + " 个 MCP 工具"),
                    session.warnings()
            );
        } catch (Exception e) {
            if (session != null) {
                session.closeAsync();
            }
            String warning = "MCP Server `" + server.name() + "` 不可用，已跳过: " + summarize(e);
            return SingleDiscovery.failed(server.name(), warning);
        }
    }

    private String summarize(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        String oneLine = message.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= 240 ? oneLine : oneLine.substring(0, 240) + "...";
    }

    private record SingleDiscovery(
            List<McpToolWrapper> tools,
            McpServerStatus status,
            List<String> warnings
    ) {
        private SingleDiscovery {
            tools = tools == null ? List.of() : List.copyOf(tools);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        private static SingleDiscovery failed(String serverName, String warning) {
            return new SingleDiscovery(List.of(), McpServerStatus.failed(serverName, warning), List.of(warning));
        }
    }
}