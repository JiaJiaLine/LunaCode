package com.lunacode.prompt;

public final class SystemPromptBuilder {
    public String build(SystemPromptConfig config) {
        return new StaticSystemPromptBuilder().build().render();
    }
}
