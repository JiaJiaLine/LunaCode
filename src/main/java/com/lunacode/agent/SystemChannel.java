package com.lunacode.agent;

import java.util.Objects;

public record SystemChannel(StaticSystemPrompt staticPrompt, EnvironmentContext environmentContext) {
    public SystemChannel {
        staticPrompt = Objects.requireNonNull(staticPrompt, "staticPrompt");
        environmentContext = Objects.requireNonNull(environmentContext, "environmentContext");
    }
}
