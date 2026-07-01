package com.lunacode.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.lunacode.permission.PermissionMode;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigLoader {
    private static final Pattern ENV_PATTERN = Pattern.compile("^\\$\\{([A-Za-z_][A-Za-z0-9_]*)}$");
    private final ObjectMapper mapper;
    private final Map<String, String> environment;
    private final Path userConfigPath;
    private final EnvironmentValueExpander environmentValueExpander;

    public ConfigLoader() {
        this(System.getenv());
    }

    public ConfigLoader(Map<String, String> environment) {
        this(environment, defaultUserConfigPath());
    }

    public ConfigLoader(Map<String, String> environment, Path userConfigPath) {
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.environment = Objects.requireNonNull(environment, "environment");
        this.userConfigPath = userConfigPath;
        this.environmentValueExpander = new EnvironmentValueExpander(environment);
    }

    public ProviderConfig load(Path path) {
        RawConfig raw;
        try {
            raw = mapper.readValue(path.toFile(), RawConfig.class);
        } catch (IOException e) {
            throw new ConfigException("读取配置文件失败: " + path, e);
        }

        String protocol = requireText(raw.protocol(), "protocol").toLowerCase();
        if (!protocol.equals("openai") && !protocol.equals("anthropic")) {
            throw new ConfigException("protocol 只能是 openai 或 anthropic");
        }

        String model = requireText(raw.model(), "model");
        URI baseUrl = parseUri(requireText(raw.baseUrl(), "base_url"));
        String apiKey = resolveApiKey(requireText(raw.apiKey(), "api_key"));
        ThinkingConfig thinking = toThinkingConfig(raw.thinking());
        AgentConfig agent = toAgentConfig(raw.agent());
        PermissionConfig permissions = toPermissionConfig(raw.permissions());
        SandboxConfig sandbox = toSandboxConfig(raw.sandbox());
        McpConfig mcp = toMergedMcpConfig(readUserMcp(path), raw.mcp());
        ContextConfig context = toContextConfig(raw.context());
        return new ProviderConfig(protocol, model, baseUrl, apiKey, thinking, agent, permissions, sandbox, mcp, context);
    }

    private RawMcp readUserMcp(Path projectConfigPath) {
        if (userConfigPath == null || !Files.exists(userConfigPath)) {
            return null;
        }
        Path normalizedUserPath = userConfigPath.toAbsolutePath().normalize();
        Path normalizedProjectPath = projectConfigPath == null ? null : projectConfigPath.toAbsolutePath().normalize();
        if (normalizedUserPath.equals(normalizedProjectPath)) {
            return null;
        }
        try {
            RawConfig raw = mapper.readValue(normalizedUserPath.toFile(), RawConfig.class);
            return raw.mcp();
        } catch (IOException e) {
            throw new ConfigException("读取用户级 MCP 配置失败: " + normalizedUserPath, e);
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ConfigException("配置缺少必填字段: " + field);
        }
        return value.trim();
    }

    private URI parseUri(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new ConfigException("base_url 必须是有效 URL");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new ConfigException("base_url 格式无效", e);
        }
    }

    private URI parseMcpUri(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null) {
                throw new McpServerParseException("url 必须是有效 URL");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new McpServerParseException("url 格式无效");
        }
    }

    private String resolveApiKey(String rawApiKey) {
        Matcher matcher = ENV_PATTERN.matcher(rawApiKey);
        if (!matcher.matches()) {
            return rawApiKey;
        }
        String name = matcher.group(1);
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new ConfigException("环境变量未设置或为空: " + name);
        }
        return value;
    }

    private ThinkingConfig toThinkingConfig(RawThinking raw) {
        if (raw == null) {
            return ThinkingConfig.disabled();
        }
        return new ThinkingConfig(raw.enabled(), raw.budgetTokens());
    }

    private AgentConfig toAgentConfig(RawAgent raw) {
        if (raw == null) {
            return AgentConfig.defaults();
        }
        AgentConfig defaults = AgentConfig.defaults();
        int maxIterations = raw.maxIterations() == null ? defaults.maxIterations() : raw.maxIterations();
        int maxUnknownTools = raw.maxConsecutiveUnknownTools() == null
                ? defaults.maxConsecutiveUnknownTools()
                : raw.maxConsecutiveUnknownTools();
        Path planFile = raw.planFile() == null || raw.planFile().isBlank()
                ? defaults.planFile()
                : Path.of(raw.planFile().trim());
        return new AgentConfig(maxIterations, maxUnknownTools, planFile);
    }

    private PermissionConfig toPermissionConfig(RawPermissions raw) {
        if (raw == null) {
            return PermissionConfig.defaults();
        }
        try {
            return new PermissionConfig(PermissionMode.fromConfig(raw.mode()));
        } catch (IllegalArgumentException e) {
            throw new ConfigException("permissions.mode 无效: " + raw.mode(), e);
        }
    }

    private SandboxConfig toSandboxConfig(RawSandbox raw) {
        if (raw == null) {
            return SandboxConfig.defaults();
        }
        boolean networkEnabled = raw.networkEnabled() != null && raw.networkEnabled();
        List<SandboxRootConfig> extraRoots = raw.extraRoots() == null ? List.of() : raw.extraRoots().stream()
                .map(root -> new SandboxRootConfig(root.name(), root.path()))
                .toList();
        return new SandboxConfig(networkEnabled, extraRoots);
    }

    private ContextConfig toContextConfig(RawContext raw) {
        ContextConfig defaults = ContextConfig.defaults();
        if (raw == null) {
            return defaults;
        }
        return new ContextConfig(
                raw.contextWindowTokens() == null ? defaults.contextWindowTokens() : raw.contextWindowTokens(),
                raw.summaryOutputReserveTokens() == null ? defaults.summaryOutputReserveTokens() : raw.summaryOutputReserveTokens(),
                raw.autoCompactMarginTokens() == null ? defaults.autoCompactMarginTokens() : raw.autoCompactMarginTokens(),
                raw.forceCompactExtraTokens() == null ? defaults.forceCompactExtraTokens() : raw.forceCompactExtraTokens(),
                raw.singleToolResultCharLimit() == null ? defaults.singleToolResultCharLimit() : raw.singleToolResultCharLimit(),
                raw.toolMessageCharLimit() == null ? defaults.toolMessageCharLimit() : raw.toolMessageCharLimit(),
                raw.recentTokenBudget() == null ? defaults.recentTokenBudget() : raw.recentTokenBudget(),
                raw.minimumRecentMessages() == null ? defaults.minimumRecentMessages() : raw.minimumRecentMessages(),
                raw.restoredFileLimit() == null ? defaults.restoredFileLimit() : raw.restoredFileLimit(),
                raw.restoredFileTokenLimit() == null ? defaults.restoredFileTokenLimit() : raw.restoredFileTokenLimit(),
                raw.skillDefinitionTokenBudget() == null ? defaults.skillDefinitionTokenBudget() : raw.skillDefinitionTokenBudget(),
                raw.maxAutoSummaryFailures() == null ? defaults.maxAutoSummaryFailures() : raw.maxAutoSummaryFailures(),
                raw.promptTooLongGroupRetries() == null ? defaults.promptTooLongGroupRetries() : raw.promptTooLongGroupRetries(),
                raw.promptTooLongDropFraction() == null ? defaults.promptTooLongDropFraction() : raw.promptTooLongDropFraction(),
                raw.sessionRoot() == null || raw.sessionRoot().isBlank()
                        ? defaults.sessionRoot()
                        : Path.of(raw.sessionRoot().trim())
        );
    }

    private McpConfig toMergedMcpConfig(RawMcp userMcp, RawMcp projectMcp) {
        LinkedHashMap<String, McpServerConfig> servers = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        parseMcpServers("用户级", userMcp, servers, warnings, false);
        parseMcpServers("项目级", projectMcp, servers, warnings, true);
        return new McpConfig(servers, warnings);
    }

    private void parseMcpServers(
            String source,
            RawMcp rawMcp,
            LinkedHashMap<String, McpServerConfig> servers,
            List<String> warnings,
            boolean projectOverrides
    ) {
        if (rawMcp == null || rawMcp.servers() == null || rawMcp.servers().isEmpty()) {
            return;
        }
        for (Map.Entry<String, RawMcpServer> entry : rawMcp.servers().entrySet()) {
            String serverName = entry.getKey() == null ? "" : entry.getKey().strip();
            if (projectOverrides) {
                servers.remove(serverName);
            }
            try {
                servers.put(serverName, parseMcpServer(serverName, entry.getValue()));
            } catch (McpServerParseException e) {
                warnings.add(source + " MCP Server `" + safeServerName(serverName) + "` 已跳过: " + e.getMessage());
            }
        }
    }

    private McpServerConfig parseMcpServer(String serverName, RawMcpServer raw) {
        if (serverName == null || serverName.isBlank()) {
            throw new McpServerParseException("Server 名不能为空");
        }
        if (raw == null) {
            throw new McpServerParseException("配置不能为空");
        }
        boolean hasCommand = hasText(raw.command());
        boolean hasUrl = hasText(raw.url());
        if (hasCommand == hasUrl) {
            throw new McpServerParseException("必须且只能配置 command 或 url");
        }
        if (hasCommand) {
            return parseStdioServer(serverName, raw);
        }
        return parseHttpServer(serverName, raw);
    }

    private McpStdioServerConfig parseStdioServer(String serverName, RawMcpServer raw) {
        String command = expandMcpValue("command", raw.command());
        if (!hasText(command)) {
            throw new McpServerParseException("command 展开后为空");
        }
        List<String> args = raw.args() == null ? List.of() : raw.args().stream()
                .map(value -> expandMcpValue("args", value))
                .toList();
        LinkedHashMap<String, String> env = new LinkedHashMap<>();
        LinkedHashMap<String, String> sensitive = new LinkedHashMap<>();
        if (raw.env() != null) {
            for (Map.Entry<String, String> entry : raw.env().entrySet()) {
                String key = requireMcpMapKey(entry.getKey(), "env");
                String value = expandMcpValue("env." + key, entry.getValue());
                env.put(key, value);
                sensitive.put("env." + key, value);
            }
        }
        if (environmentValueExpander.containsPlaceholder(raw.command())) {
            sensitive.put("command", command);
        }
        for (int i = 0; raw.args() != null && i < raw.args().size(); i++) {
            String rawArg = raw.args().get(i);
            if (environmentValueExpander.containsPlaceholder(rawArg)) {
                sensitive.put("args." + i, args.get(i));
            }
        }
        return new McpStdioServerConfig(serverName, command, args, env, sensitive);
    }

    private McpHttpServerConfig parseHttpServer(String serverName, RawMcpServer raw) {
        String urlText = expandMcpValue("url", raw.url());
        URI url = parseMcpUri(urlText);
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        LinkedHashMap<String, String> sensitive = new LinkedHashMap<>();
        if (raw.headers() != null) {
            for (Map.Entry<String, String> entry : raw.headers().entrySet()) {
                String key = requireMcpMapKey(entry.getKey(), "headers");
                String value = expandMcpValue("headers." + key, entry.getValue());
                headers.put(key, value);
                sensitive.put("headers." + key, value);
            }
        }
        if (environmentValueExpander.containsPlaceholder(raw.url())) {
            sensitive.put("url", urlText);
        }
        return new McpHttpServerConfig(serverName, url, headers, sensitive);
    }

    private String expandMcpValue(String field, String value) {
        if (value == null) {
            throw new McpServerParseException(field + " 不能为空");
        }
        try {
            return environmentValueExpander.expand(value);
        } catch (EnvironmentValueExpander.MissingEnvironmentValueException e) {
            throw new McpServerParseException(field + " " + e.getMessage());
        }
    }

    private String requireMcpMapKey(String key, String field) {
        if (key == null || key.isBlank()) {
            throw new McpServerParseException(field + " 不能包含空 key");
        }
        return key.strip();
    }

    private String safeServerName(String name) {
        if (name == null || name.isBlank()) {
            return "<空名称>";
        }
        String oneLine = name.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 80) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Path defaultUserConfigPath() {
        return Path.of(System.getProperty("user.home"), ".lunacode", "config.yaml");
    }

    private record RawConfig(
            String protocol,
            String model,
            @JsonProperty("base_url") String baseUrl,
            @JsonProperty("api_key") String apiKey,
            RawThinking thinking,
            RawAgent agent,
            RawPermissions permissions,
            RawSandbox sandbox,
            RawMcp mcp,
            RawContext context
    ) {}

    private record RawThinking(
            boolean enabled,
            @JsonProperty("budget_tokens") Integer budgetTokens
    ) {}

    private record RawAgent(
            @JsonProperty("max_iterations") Integer maxIterations,
            @JsonProperty("max_consecutive_unknown_tools") Integer maxConsecutiveUnknownTools,
            @JsonProperty("plan_file") String planFile
    ) {}

    private record RawPermissions(
            String mode
    ) {}

    private record RawSandbox(
            @JsonProperty("network_enabled") Boolean networkEnabled,
            @JsonProperty("extra_roots") List<RawSandboxRoot> extraRoots
    ) {}

    private record RawSandboxRoot(
            String name,
            String path
    ) {}

    private record RawContext(
            @JsonProperty("context_window_tokens") Long contextWindowTokens,
            @JsonProperty("summary_output_reserve_tokens") Long summaryOutputReserveTokens,
            @JsonProperty("auto_compact_margin_tokens") Long autoCompactMarginTokens,
            @JsonProperty("force_compact_extra_tokens") Long forceCompactExtraTokens,
            @JsonProperty("single_tool_result_char_limit") Integer singleToolResultCharLimit,
            @JsonProperty("tool_message_char_limit") Integer toolMessageCharLimit,
            @JsonProperty("recent_token_budget") Integer recentTokenBudget,
            @JsonProperty("minimum_recent_messages") Integer minimumRecentMessages,
            @JsonProperty("restored_file_limit") Integer restoredFileLimit,
            @JsonProperty("restored_file_token_limit") Integer restoredFileTokenLimit,
            @JsonProperty("skill_definition_token_budget") Integer skillDefinitionTokenBudget,
            @JsonProperty("max_auto_summary_failures") Integer maxAutoSummaryFailures,
            @JsonProperty("prompt_too_long_group_retries") Integer promptTooLongGroupRetries,
            @JsonProperty("prompt_too_long_drop_fraction") Double promptTooLongDropFraction,
            @JsonProperty("session_root") String sessionRoot
    ) {}

    private record RawMcp(
            Map<String, RawMcpServer> servers
    ) {}

    private record RawMcpServer(
            String command,
            List<String> args,
            Map<String, String> env,
            String url,
            Map<String, String> headers
    ) {}

    private static class McpServerParseException extends RuntimeException {
        private McpServerParseException(String message) {
            super(message);
        }
    }

    public static class ConfigException extends RuntimeException {
        public ConfigException(String message) {
            super(message);
        }

        public ConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
