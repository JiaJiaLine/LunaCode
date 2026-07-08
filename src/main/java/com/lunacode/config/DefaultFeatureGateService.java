package com.lunacode.config;

import java.util.Objects;

public final class DefaultFeatureGateService implements FeatureGateService {
    private final FeatureConfig config;

    public DefaultFeatureGateService(ProviderConfig providerConfig) {
        this(providerConfig == null ? FeatureConfig.disabled() : providerConfig.features());
    }

    public DefaultFeatureGateService(FeatureConfig config) {
        this.config = Objects.requireNonNullElse(config, FeatureConfig.disabled());
    }

    @Override
    public boolean enabled(FeatureGate gate) {
        return config.enabled(gate);
    }
}
