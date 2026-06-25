package com.lunacode.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigLoader {
    private static final Pattern ENV_PATTERN = Pattern.compile("^\\$\\{([A-Za-z_][A-Za-z0-9_]*)}$");
    private final ObjectMapper mapper;
    private final Map<String, String> environment;

    public ConfigLoader() {
        this(System.getenv());
    }

    public ConfigLoader(Map<String, String> environment) {
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public ProviderConfig load(Path path) {
        RawConfig raw;
        try {
            raw = mapper.readValue(path.toFile(), RawConfig.class);
        } catch (IOException e) {
            throw new ConfigException("无法读取配置文件: " + path, e);
        }

        String protocol = requireText(raw.protocol(), "protocol").toLowerCase();
        if (!protocol.equals("openai") && !protocol.equals("anthropic")) {
            throw new ConfigException("protocol 只支持 openai 或 anthropic");
        }

        String model = requireText(raw.model(), "model");
        URI baseUrl = parseUri(requireText(raw.baseUrl(), "base_url"));
        String apiKey = resolveApiKey(requireText(raw.apiKey(), "api_key"));
        ThinkingConfig thinking = toThinkingConfig(raw.thinking());
        return new ProviderConfig(protocol, model, baseUrl, apiKey, thinking);
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
                throw new ConfigException("base_url 必须是完整 URL");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new ConfigException("base_url 格式无效", e);
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

    private record RawConfig(
            String protocol,
            String model,
            @JsonProperty("base_url") String baseUrl,
            @JsonProperty("api_key") String apiKey,
            RawThinking thinking
    ) {}

    private record RawThinking(
            boolean enabled,
            @JsonProperty("budget_tokens") Integer budgetTokens
    ) {}

    public static class ConfigException extends RuntimeException {
        public ConfigException(String message) {
            super(message);
        }

        public ConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}