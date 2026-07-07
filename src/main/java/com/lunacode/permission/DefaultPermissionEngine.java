package com.lunacode.permission;

import com.lunacode.tool.ToolUse;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class DefaultPermissionEngine {
    private final PermissionRuleStore ruleStore;
    private final PermissionTargetExtractor targetExtractor;
    private final PermissionRuleMatcher ruleMatcher;
    private final PermissionModePolicy modePolicy;
    private final DangerousCommandBlacklist blacklist;

    public DefaultPermissionEngine(
            PermissionRuleStore ruleStore,
            PermissionTargetExtractor targetExtractor,
            PermissionRuleMatcher ruleMatcher,
            PermissionModePolicy modePolicy,
            DangerousCommandBlacklist blacklist
    ) {
        this.ruleStore = ruleStore;
        this.targetExtractor = targetExtractor;
        this.ruleMatcher = ruleMatcher == null ? new PermissionRuleMatcher() : ruleMatcher;
        this.modePolicy = modePolicy == null ? new PermissionModePolicy() : modePolicy;
        this.blacklist = blacklist == null ? new DangerousCommandBlacklist() : blacklist;
    }

    public PermissionEvaluation evaluate(PermissionEvaluationRequest request) {
        LoadedPermissionRules rules = ruleStore == null ? LoadedPermissionRules.empty() : ruleStore.load();
        if (request.tool() == null) {
            return PermissionEvaluation.deny(PermissionDecisionLayer.TOOL_NOT_FOUND, "工具不存在或已禁用", List.of(), rules.warnings());
        }
        ToolUse toolUse = request.toolUse();
        if ("Bash".equals(toolUse.name())) {
            Optional<String> blacklistReason = blacklist.firstMatch(text(toolUse, "command"));
            if (blacklistReason.isPresent()) {
                return PermissionEvaluation.deny(PermissionDecisionLayer.BLACKLIST, blacklistReason.get(), List.of(), rules.warnings());
            }
        }

        PermissionTargetExtractor.ExtractionResult extracted = targetExtractor.extract(toolUse, request.workDir());
        if (!extracted.networkErrors().isEmpty()) {
            return PermissionEvaluation.deny(
                    PermissionDecisionLayer.NETWORK,
                    String.join("; ", extracted.networkErrors()),
                    List.of(),
                    rules.warnings()
            );
        }
        if (!extracted.sandboxErrors().isEmpty()) {
            return PermissionEvaluation.deny(
                    PermissionDecisionLayer.SANDBOX,
                    String.join("; ", extracted.sandboxErrors()),
                    List.of(),
                    rules.warnings()
            );
        }

        Optional<PermissionRuleMatch> deny = ruleMatcher.matchDeny(rules, extracted.targets());
        if (deny.isPresent()) {
            return PermissionEvaluation.deny(PermissionDecisionLayer.RULE_DENY, deny.get().reason(), List.of(deny.get()), rules.warnings());
        }
        Optional<PermissionRuleMatch> allow = ruleMatcher.matchAllow(rules, extracted.targets());
        if (allow.isPresent()) {
            return PermissionEvaluation.allow(PermissionDecisionLayer.RULE_ALLOW, allow.get().reason(), List.of(allow.get()), rules.warnings());
        }
        if (extracted.containsSensitivePath()) {
            return PermissionEvaluation.ask(
                    PermissionDecisionLayer.SENSITIVE_PATH,
                    "敏感路径需要确认",
                    suggestedAllowRule(toolUse, extracted.targets()),
                    rules.warnings()
            );
        }

        PermissionEvaluation.Decision decision = modePolicy.decide(request.permissionMode(), request.agentMode(), request.tool());
        return switch (decision) {
            case ALLOW -> PermissionEvaluation.allow(PermissionDecisionLayer.MODE_POLICY, "权限模式自动允许", List.of(), rules.warnings());
            case ASK -> PermissionEvaluation.ask(PermissionDecisionLayer.MODE_POLICY, "权限模式需要确认", suggestedAllowRule(toolUse, extracted.targets()), rules.warnings());
            case DENY -> PermissionEvaluation.deny(PermissionDecisionLayer.MODE_POLICY, "权限模式拒绝工具调用", List.of(), rules.warnings());
        };
    }

    private String suggestedAllowRule(ToolUse toolUse, List<PermissionTarget> targets) {
        if (toolUse == null || targets == null || targets.isEmpty()) {
            return null;
        }
        if ("Bash".equals(toolUse.name())) {
            String pattern = suggestedBashPattern(text(toolUse, "command"));
            if (pattern != null && !pattern.isBlank()) {
                return "Bash(" + pattern + ")";
            }
        }
        PermissionTarget preferred = targets.stream()
                .filter(target -> target.kind() == PermissionTargetKind.FILE_PATH || target.kind() == PermissionTargetKind.COMMAND_TEXT)
                .findFirst()
                .orElse(targets.get(0));
        return toolUse.name() + "(" + preferred.value() + ")";
    }

    private String suggestedBashPattern(String command) {
        String normalized = normalizeCommand(command);
        if (normalized.isBlank()) {
            return null;
        }
        String firstSegment = firstCommandSegment(normalized);
        String withoutRedirection = stripTrailingRedirection(firstSegment).strip();
        String base = withoutRedirection.isBlank() ? firstSegment.strip() : withoutRedirection;
        if (base.isBlank()) {
            return normalized;
        }
        boolean generalized = !base.equals(normalized);
        return generalized ? base + "*" : base;
    }

    private String normalizeCommand(String command) {
        return command == null ? "" : command.replaceAll("\\s+", " ").strip();
    }

    private String firstCommandSegment(String command) {
        char quote = 0;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                continue;
            }
            if (c == ';' || c == '|') {
                return command.substring(0, i).strip();
            }
            if (c == '&' && i + 1 < command.length() && command.charAt(i + 1) == '&') {
                return command.substring(0, i).strip();
            }
        }
        return command.strip();
    }

    private String stripTrailingRedirection(String segment) {
        String result = segment;
        result = result.replaceAll("\\s+\\d?>>?&?\\S+.*$", "");
        result = result.replaceAll("\\s+>>?\\S+.*$", "");
        return result;
    }

    private String text(ToolUse toolUse, String name) {
        if (toolUse == null || toolUse.input() == null || !toolUse.input().hasNonNull(name)) {
            return "";
        }
        return toolUse.input().path(name).asText();
    }
}
