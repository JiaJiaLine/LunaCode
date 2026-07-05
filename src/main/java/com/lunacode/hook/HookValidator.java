package com.lunacode.hook;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class HookValidator {
    private final HookConditionParser conditionParser;

    public HookValidator() {
        this(new HookConditionParser());
    }

    public HookValidator(HookConditionParser conditionParser) {
        this.conditionParser = conditionParser == null ? new HookConditionParser() : conditionParser;
    }

    public HookConfig validate(List<RawHookDefinition> rawHooks) {
        List<String> errors = new ArrayList<>();
        List<HookDefinition> hooks = new ArrayList<>();
        for (RawHookDefinition raw : rawHooks == null ? List.<RawHookDefinition>of() : rawHooks) {
            try {
                hooks.add(toDefinition(raw));
            } catch (IllegalArgumentException e) {
                errors.add(location(raw) + " " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new HookConfigException(errors);
        }
        hooks.sort(Comparator
                .comparingInt((HookDefinition hook) -> hook.source().level().order())
                .thenComparingInt(HookDefinition::order));
        return new HookConfig(hooks);
    }

    private HookDefinition toDefinition(RawHookDefinition raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Hook 不能为空");
        }
        HookEventName event = HookEventName.fromYamlName(raw.event())
                .orElseThrow(() -> new IllegalArgumentException("event 缺失或未知: " + safe(raw.event())));
        boolean reject = Boolean.TRUE.equals(raw.reject());
        boolean async = Boolean.TRUE.equals(raw.async());
        boolean once = Boolean.TRUE.equals(raw.once());
        boolean injectResult = Boolean.TRUE.equals(raw.injectResult());
        if (reject && event != HookEventName.PRE_TOOL_USE) {
            throw new IllegalArgumentException("reject 只能用于 pre_tool_use");
        }
        if (event == HookEventName.PRE_TOOL_USE && async) {
            throw new IllegalArgumentException("pre_tool_use Hook 不允许 async");
        }
        HookAction action = parseAction(raw.action());
        Optional<HookCondition> condition;
        try {
            condition = conditionParser.parse(raw.condition());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("if 无效: " + e.getMessage());
        }
        Optional<Duration> timeout = timeout(raw.timeoutMs(), "timeout_ms");
        String id = raw.source().level().name().toLowerCase() + "-" + raw.order() + "-" + event.yamlName();
        return new HookDefinition(id, raw.source(), raw.order(), event, condition, action, reject, async, once, timeout, injectResult);
    }
    private HookAction parseAction(Map<String, Object> rawAction) {
        if (rawAction == null || rawAction.isEmpty()) {
            throw new IllegalArgumentException("action 缺失");
        }
        String typeText = string(rawAction.get("type"));
        HookActionType type = HookActionType.fromYamlName(typeText)
                .orElseThrow(() -> new IllegalArgumentException("action.type 缺失或未知: " + safe(typeText)));
        return switch (type) {
            case COMMAND -> new HookAction.Command(requireText(rawAction, "command", "command 动作需要 command"));
            case PROMPT -> new HookAction.Prompt(requireText(rawAction, "prompt", "prompt 动作需要 prompt"));
            case HTTP -> parseHttp(rawAction);
            case SUB_AGENT -> new HookAction.SubAgent(
                    requireText(rawAction, "name", "sub_agent 动作需要 name"),
                    requireText(rawAction, "prompt", "sub_agent 动作需要 prompt")
            );
        };
    }

    private HookAction.Http parseHttp(Map<String, Object> rawAction) {
        String urlText = requireText(rawAction, "url", "http 动作需要 url");
        URI url;
        try {
            url = new URI(urlText);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("http.url 格式无效: " + urlText);
        }
        String method = string(rawAction.getOrDefault("method", "POST"));
        Map<String, String> headers = stringMap(rawAction.get("headers"));
        String body = string(rawAction.get("body"));
        Optional<Duration> timeout = timeout(integer(rawAction.get("timeout_ms")), "action.timeout_ms");
        return new HookAction.Http(url, method, headers, body, timeout);
    }

    private Optional<Duration> timeout(Integer value, String field) {
        if (value == null) {
            return Optional.empty();
        }
        if (value <= 0) {
            throw new IllegalArgumentException(field + " 必须大于 0");
        }
        return Optional.of(Duration.ofMillis(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> stringMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("http.headers 必须是对象");
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), string(item)));
        return result;
    }

    private String requireText(Map<String, Object> map, String field, String message) {
        String value = string(map.get(field));
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("timeout_ms 必须是整数");
        }
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safe(String value) {
        return value == null ? "<空>" : value;
    }

    private String location(RawHookDefinition raw) {
        if (raw == null || raw.source() == null) {
            return "<unknown>";
        }
        return raw.source().displayName() + "#" + raw.order() + ":";
    }
}
