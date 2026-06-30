package com.lunacode.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.config.McpStdioServerConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StdioMcpTransport implements McpTransport {
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpStdioServerConfig config;
    private final Path workspaceRoot;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object writeLock = new Object();
    private volatile McpTransportListener listener;
    private volatile Process process;
    private volatile BufferedWriter stdin;

    public StdioMcpTransport(McpStdioServerConfig config, Path workspaceRoot) {
        this.config = Objects.requireNonNull(config, "config");
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
    }

    @Override
    public String serverName() {
        return config.name();
    }

    @Override
    public CompletableFuture<Void> start(McpTransportListener listener) {
        if (!started.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        this.listener = Objects.requireNonNull(listener, "listener");
        return CompletableFuture.runAsync(() -> {
            try {
                List<String> command = new ArrayList<>();
                command.add(config.command());
                command.addAll(config.args());
                ProcessBuilder builder = new ProcessBuilder(command)
                        .directory(workspaceRoot.toFile())
                        .redirectError(ProcessBuilder.Redirect.PIPE)
                        .redirectOutput(ProcessBuilder.Redirect.PIPE)
                        .redirectInput(ProcessBuilder.Redirect.PIPE);
                builder.environment().putAll(config.env());
                process = builder.start();
                stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                startStdoutReader(process);
                startStderrReader(process);
                startExitWatcher(process);
            } catch (Exception e) {
                notifyClosed(e);
                throw new IllegalStateException("启动 stdio MCP Server 失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> send(JsonNode message) {
        return CompletableFuture.runAsync(() -> {
            Process current = process;
            BufferedWriter writer = stdin;
            if (current == null || writer == null || !current.isAlive()) {
                throw new IllegalStateException("MCP stdio 连接不可用: " + serverName());
            }
            synchronized (writeLock) {
                try {
                    writer.write(mapper.writeValueAsString(message));
                    writer.newLine();
                    writer.flush();
                } catch (Exception e) {
                    notifyClosed(e);
                    throw new IllegalStateException("发送 MCP stdio 消息失败: " + e.getMessage(), e);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        return CompletableFuture.runAsync(() -> {
            closed.set(true);
            try {
                if (stdin != null) {
                    stdin.close();
                }
            } catch (Exception ignored) {
            }
            Process current = process;
            if (current != null && current.isAlive()) {
                try {
                    if (!current.waitFor(2, TimeUnit.SECONDS)) {
                        current.destroyForcibly();
                        current.waitFor(2, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    current.destroyForcibly();
                }
            }
        });
    }

    private void startStdoutReader(Process process) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    listener.onMessage(mapper.readTree(line));
                }
            } catch (Exception e) {
                if (!closed.get()) {
                    notifyClosed(e);
                }
            }
        }, "mcp-stdio-out-" + serverName());
        thread.setDaemon(true);
        thread.start();
    }

    private void startStderrReader(Process process) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    McpTransportListener current = listener;
                    if (current != null && !line.isBlank()) {
                        current.onDiagnostic(line);
                    }
                }
            } catch (Exception ignored) {
            }
        }, "mcp-stdio-err-" + serverName());
        thread.setDaemon(true);
        thread.start();
    }

    private void startExitWatcher(Process process) {
        Thread thread = new Thread(() -> {
            try {
                process.waitFor();
                if (!closed.get()) {
                    notifyClosed(new IllegalStateException("MCP stdio Server 已退出，exitCode=" + process.exitValue()));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                notifyClosed(e);
            }
        }, "mcp-stdio-exit-" + serverName());
        thread.setDaemon(true);
        thread.start();
    }

    private void notifyClosed(Throwable cause) {
        if (closed.compareAndSet(false, true)) {
            McpTransportListener current = listener;
            if (current != null) {
                current.onClosed(cause);
            }
        }
    }
}
