package com.lunacode.config;

import java.nio.file.Path;

public record ContextConfig(
        long contextWindowTokens,
        long summaryOutputReserveTokens,
        long autoCompactMarginTokens,
        long forceCompactExtraTokens,
        int singleToolResultCharLimit,
        int toolMessageCharLimit,
        int recentTokenBudget,
        int minimumRecentMessages,
        int restoredFileLimit,
        int restoredFileTokenLimit,
        int skillDefinitionTokenBudget,
        int maxAutoSummaryFailures,
        int promptTooLongGroupRetries,
        double promptTooLongDropFraction,
        Path sessionRoot
) {
    public ContextConfig {
        if (contextWindowTokens <= 0) {
            throw new IllegalArgumentException("contextWindowTokens 必须大于 0");
        }
        if (summaryOutputReserveTokens <= 0) {
            throw new IllegalArgumentException("summaryOutputReserveTokens 必须大于 0");
        }
        if (autoCompactMarginTokens <= 0) {
            throw new IllegalArgumentException("autoCompactMarginTokens 必须大于 0");
        }
        if (forceCompactExtraTokens <= 0) {
            throw new IllegalArgumentException("forceCompactExtraTokens 必须大于 0");
        }
        if (singleToolResultCharLimit <= 0 || toolMessageCharLimit <= 0) {
            throw new IllegalArgumentException("工具结果阈值必须大于 0");
        }
        if (recentTokenBudget <= 0 || minimumRecentMessages <= 0) {
            throw new IllegalArgumentException("近期原文保留预算必须大于 0");
        }
        if (restoredFileLimit < 0 || restoredFileTokenLimit <= 0) {
            throw new IllegalArgumentException("恢复文件配置无效");
        }
        if (skillDefinitionTokenBudget < 0) {
            throw new IllegalArgumentException("Skill 定义预算不能为负数");
        }
        if (maxAutoSummaryFailures <= 0 || promptTooLongGroupRetries < 0) {
            throw new IllegalArgumentException("重试和熔断配置无效");
        }
        if (promptTooLongDropFraction <= 0 || promptTooLongDropFraction >= 1) {
            throw new IllegalArgumentException("promptTooLongDropFraction 必须在 0 和 1 之间");
        }
        sessionRoot = sessionRoot == null ? Path.of(".lunacode", "tmp", "context") : sessionRoot;
        long effectiveWindow = contextWindowTokens - summaryOutputReserveTokens;
        long autoThreshold = effectiveWindow - autoCompactMarginTokens;
        long forceThreshold = autoThreshold + forceCompactExtraTokens;
        if (effectiveWindow <= 0 || autoThreshold <= 0 || forceThreshold <= autoThreshold) {
            throw new IllegalArgumentException("上下文压缩阈值配置无效");
        }
    }

    public static ContextConfig defaults() {
        return new ContextConfig(
                200_000,
                20_000,
                13_000,
                10_000,
                50_000,
                200_000,
                10_000,
                5,
                5,
                5_000,
                25_000,
                3,
                3,
                0.20,
                Path.of(".lunacode", "tmp", "context")
        );
    }

    public ContextBudget budget() {
        long effectiveWindow = contextWindowTokens - summaryOutputReserveTokens;
        long autoThreshold = effectiveWindow - autoCompactMarginTokens;
        long forceThreshold = autoThreshold + forceCompactExtraTokens;
        return new ContextBudget(
                contextWindowTokens,
                summaryOutputReserveTokens,
                effectiveWindow,
                autoThreshold,
                forceThreshold
        );
    }
}