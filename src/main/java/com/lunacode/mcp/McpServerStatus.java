package com.lunacode.mcp;

public record McpServerStatus(
        String serverName,
        State state,
        String summary
) {
    public enum State {
        CONNECTING,
        READY,
        FAILED,
        CLOSED
    }

    public static McpServerStatus ready(String serverName, String summary) {
        return new McpServerStatus(serverName, State.READY, summary);
    }

    public static McpServerStatus failed(String serverName, String summary) {
        return new McpServerStatus(serverName, State.FAILED, summary);
    }

    public static McpServerStatus closed(String serverName, String summary) {
        return new McpServerStatus(serverName, State.CLOSED, summary);
    }
}
