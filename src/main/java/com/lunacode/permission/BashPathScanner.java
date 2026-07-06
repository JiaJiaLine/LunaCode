package com.lunacode.permission;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class BashPathScanner {
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final Pattern URI = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://.*");

    public List<ScannedPath> scan(String command, PathSandbox sandbox) {
        if (command == null || command.isBlank() || sandbox == null) {
            return List.of();
        }
        List<String> tokens = tokenize(command);
        List<ScannedPath> scanned = new ArrayList<>();
        boolean nextIsRedirectionTarget = false;
        for (String token : tokens) {
            String clean = cleanToken(token);
            if (clean.isBlank()) {
                continue;
            }
            if (nextIsRedirectionTarget || looksLikePath(clean)) {
                PathSandbox.Result result = sandbox.validate(clean, PathIntent.COMMAND_ARGUMENT);
                scanned.add(new ScannedPath(clean, result));
                nextIsRedirectionTarget = false;
                continue;
            }
            nextIsRedirectionTarget = isRedirection(clean);
        }
        return List.copyOf(scanned);
    }

    private List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (Character.isWhitespace(c)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private String cleanToken(String token) {
        String clean = token.strip();
        while (!clean.isEmpty() && ";|&".indexOf(clean.charAt(clean.length() - 1)) >= 0) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.startsWith("<") || clean.startsWith(">")) {
            clean = clean.replaceFirst("^[<>]+", "");
        }
        return clean.strip();
    }

    private boolean looksLikePath(String token) {
        if (URI.matcher(token).matches()) {
            return false;
        }
        if (token.startsWith("-") && !token.contains("..")) {
            return false;
        }
        return token.startsWith("/")
                || token.startsWith("./")
                || token.startsWith("../")
                || token.contains("/../")
                || token.contains("\\..\\")
                || token.contains("/")
                || token.contains("\\")
                || WINDOWS_ABSOLUTE.matcher(token).matches();
    }

    private boolean isRedirection(String token) {
        return token.equals(">") || token.equals(">>") || token.equals("<") || token.equals("2>") || token.equals("2>>");
    }

    public record ScannedPath(String token, PathSandbox.Result result) {}
}
