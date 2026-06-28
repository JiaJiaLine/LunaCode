package com.lunacode.prompt;

public record PromptCachePolicy(boolean cacheStaticSystemPrompt, boolean cacheToolDeclarations) {
    public static PromptCachePolicy enabled() {
        return new PromptCachePolicy(true, true);
    }
}
