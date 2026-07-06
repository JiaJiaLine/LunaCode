package com.lunacode.permission;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class BashNetworkAccessScanner {
    private static final long MAX_SCRIPT_BYTES = 128L * 1024L;
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\b(?:https?|ftp|ftps|ws|wss)://");
    private static final List<Entry> NETWORK_PATTERNS = List.of(
            entry("URL", URL_PATTERN),
            entry("curl/wget 网络命令", "(?i)(^|[^A-Za-z0-9_-])(?:curl(?:\\.exe)?|wget(?:\\.exe)?)\\b"),
            entry("PowerShell 网络命令", "(?i)\\b(?:invoke-webrequest|iwr|invoke-restmethod|irm|start-bitstransfer)\\b"),
            entry("PowerShell 网络 API", "(?i)\\b(?:system\\.net\\.|net\\.webclient|httpclient|webrequest|downloadfile|downloadstring)\\b"),
            entry("git 远程网络操作", "(?i)(^|[^A-Za-z0-9_-])git\\s+(?:clone|fetch|pull|push|ls-remote)\\b"),
            entry("包管理器联网安装", "(?i)(^|[^A-Za-z0-9_-])(?:npm|pnpm|yarn|bun)\\s+(?:install|add|update|upgrade)\\b"),
            entry("pip 联网安装", "(?i)(^|[^A-Za-z0-9_-])(?:pip|pip3)\\s+install\\b|(^|[^A-Za-z0-9_-])python(?:3|\\.exe)?\\s+-m\\s+pip\\s+install\\b")
    );
    private static final Set<String> SCRIPT_EXTENSIONS = Set.of(
            ".sh", ".bash", ".zsh", ".fish",
            ".ps1", ".psm1", ".bat", ".cmd",
            ".py", ".js", ".mjs", ".cjs", ".ts",
            ".rb", ".pl", ".php"
    );
    private static final Set<String> INTERPRETERS = Set.of(
            "bash", "sh", "zsh", "fish",
            "powershell", "powershell.exe", "pwsh", "pwsh.exe",
            "python", "python.exe", "python3", "python3.exe",
            "node", "node.exe", "ruby", "ruby.exe", "perl", "perl.exe",
            "cmd", "cmd.exe"
    );

    public Optional<String> firstNetworkAccess(String command, PathSandbox sandbox) {
        Optional<String> direct = firstPattern(command);
        if (direct.isPresent()) {
            return direct;
        }
        if (sandbox == null) {
            return Optional.empty();
        }
        for (String candidate : scriptCandidates(command)) {
            PathSandbox.Result result = sandbox.validate(candidate, PathIntent.COMMAND_ARGUMENT);
            if (!result.allowed()) {
                continue;
            }
            Optional<String> scriptMatch = scanScript(result.path());
            if (scriptMatch.isPresent()) {
                return scriptMatch;
            }
        }
        return Optional.empty();
    }

    private Optional<String> firstPattern(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        for (Entry entry : NETWORK_PATTERNS) {
            if (entry.pattern().matcher(value).find()) {
                return Optional.of("网络隔离已启用，检测到" + entry.reason());
            }
        }
        return Optional.empty();
    }

    private Optional<String> scanScript(VirtualPath script) {
        Path realPath = script.realPath();
        try {
            if (!Files.isRegularFile(realPath)) {
                return Optional.empty();
            }
            long size = Files.size(realPath);
            if (size > MAX_SCRIPT_BYTES) {
                return Optional.of("网络隔离已启用，脚本过大无法审计: " + script.virtualPath());
            }
            byte[] bytes = Files.readAllBytes(realPath);
            Optional<String> match = firstPattern(decode(bytes));
            if (match.isPresent()) {
                return Optional.of(match.get() + "，来源脚本: " + script.virtualPath());
            }
            if (looksLikeUtf16WithoutBom(bytes)) {
                match = firstPattern(new String(bytes, StandardCharsets.UTF_16LE));
                if (match.isPresent()) {
                    return Optional.of(match.get() + "，来源脚本: " + script.virtualPath());
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.of("网络隔离已启用，无法读取脚本进行审计: " + script.virtualPath());
        }
    }

    private String decode(byte[] bytes) {
        if (bytes.length >= 2) {
            int b0 = bytes[0] & 0xff;
            int b1 = bytes[1] & 0xff;
            if (b0 == 0xff && b1 == 0xfe) {
                return new String(bytes, StandardCharsets.UTF_16LE);
            }
            if (b0 == 0xfe && b1 == 0xff) {
                return new String(bytes, StandardCharsets.UTF_16BE);
            }
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private boolean looksLikeUtf16WithoutBom(byte[] bytes) {
        int zeros = 0;
        int limit = Math.min(bytes.length, 200);
        for (int i = 1; i < limit; i += 2) {
            if (bytes[i] == 0) {
                zeros++;
            }
        }
        return zeros > limit / 6;
    }

    private List<String> scriptCandidates(String command) {
        List<String> tokens = tokenize(command);
        List<String> result = new ArrayList<>();
        boolean expectScript = false;
        boolean interpreterSeen = false;
        for (String token : tokens) {
            String clean = cleanToken(token);
            if (clean.isBlank()) {
                continue;
            }
            String lower = clean.toLowerCase(Locale.ROOT);
            if (expectScript && looksLikePath(clean)) {
                result.add(clean);
                expectScript = false;
                interpreterSeen = false;
                continue;
            }
            if (isScriptFlag(lower)) {
                expectScript = true;
                continue;
            }
            if (isInterpreter(lower)) {
                interpreterSeen = true;
                continue;
            }
            if (interpreterSeen) {
                if (lower.startsWith("-") || lower.equals("/c") || lower.equals("/d") || lower.equals("bypass")) {
                    continue;
                }
                if (looksLikePath(clean) || hasScriptExtension(clean)) {
                    result.add(clean);
                    interpreterSeen = false;
                    continue;
                }
            }
            if (hasScriptExtension(clean)) {
                result.add(clean);
            }
        }
        return List.copyOf(result);
    }

    private boolean isScriptFlag(String token) {
        return token.equals("-file") || token.equals("/file");
    }

    private boolean isInterpreter(String token) {
        String name = token;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return INTERPRETERS.contains(name);
    }

    private boolean hasScriptExtension(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        int query = lower.indexOf('?');
        if (query >= 0) {
            lower = lower.substring(0, query);
        }
        for (String extension : SCRIPT_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikePath(String token) {
        return token.startsWith("./")
                || token.startsWith(".\\")
                || token.startsWith("../")
                || token.startsWith("..\\")
                || token.contains("/")
                || token.contains("\\")
                || hasScriptExtension(token);
    }

    private List<String> tokenize(String command) {
        if (command == null || command.isBlank()) {
            return List.of();
        }
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
                flush(tokens, current);
            } else {
                current.append(c);
            }
        }
        flush(tokens, current);
        return tokens;
    }

    private void flush(List<String> tokens, StringBuilder current) {
        if (!current.isEmpty()) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    private String cleanToken(String token) {
        String clean = token == null ? "" : token.strip();
        while (!clean.isEmpty() && "();|&".indexOf(clean.charAt(0)) >= 0) {
            clean = clean.substring(1).strip();
        }
        while (!clean.isEmpty() && ";|&)".indexOf(clean.charAt(clean.length() - 1)) >= 0) {
            clean = clean.substring(0, clean.length() - 1).strip();
        }
        return clean;
    }

    private static Entry entry(String reason, String regex) {
        return entry(reason, Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
    }

    private static Entry entry(String reason, Pattern pattern) {
        return new Entry(reason, pattern);
    }

    private record Entry(String reason, Pattern pattern) {}
}
