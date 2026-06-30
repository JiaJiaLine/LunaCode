package com.lunacode.mcp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class McpToolNameAllocator {
    private static final int MAX_NAME_LENGTH = 64;
    private final Set<String> used = new LinkedHashSet<>();

    public McpToolNameAllocator(Collection<String> reservedNames) {
        if (reservedNames != null) {
            used.addAll(reservedNames);
        }
    }

    public synchronized String allocate(String serverName, String originalToolName) {
        String raw = "mcp_" + nullToTool(serverName) + "_" + nullToTool(originalToolName);
        String base = legalize(raw);
        String candidate = trimWithHash(base, raw);
        if (!used.contains(candidate)) {
            used.add(candidate);
            return candidate;
        }
        String hash = shortHash(raw);
        candidate = appendSuffix(base, hash);
        int counter = 2;
        while (used.contains(candidate)) {
            candidate = appendSuffix(base, shortHash(raw + "#" + counter));
            counter++;
        }
        used.add(candidate);
        return candidate;
    }

    private String legalize(String value) {
        String normalized = value == null ? "" : value.strip();
        normalized = normalized.replaceAll("[^A-Za-z0-9_-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^-+", "")
                .replaceAll("^_+", "")
                .replaceAll("[_-]+$", "");
        if (normalized.isBlank()) {
            normalized = "mcp_tool";
        }
        if (!Character.isLetter(normalized.charAt(0))) {
            normalized = "mcp_" + normalized;
        }
        return normalized;
    }

    private String trimWithHash(String base, String raw) {
        if (base.length() <= MAX_NAME_LENGTH) {
            return base;
        }
        return appendSuffix(base, shortHash(raw));
    }

    private String appendSuffix(String base, String suffix) {
        int prefixLength = Math.max(1, MAX_NAME_LENGTH - suffix.length() - 1);
        String prefix = base.length() <= prefixLength ? base : base.substring(0, prefixLength);
        return prefix.replaceAll("[_-]+$", "") + "_" + suffix;
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                hex.append(String.format(Locale.ROOT, "%02x", bytes[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String nullToTool(String value) {
        return value == null || value.isBlank() ? "tool" : value;
    }
}
