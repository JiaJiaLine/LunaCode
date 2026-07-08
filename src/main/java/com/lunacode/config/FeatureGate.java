package com.lunacode.config;

public enum FeatureGate {
    FORK_SUBAGENT("FORK_SUBAGENT"),
    COORDINATOR_MODE("COORDINATOR_MODE");

    private final String configKey;

    FeatureGate(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }
}
