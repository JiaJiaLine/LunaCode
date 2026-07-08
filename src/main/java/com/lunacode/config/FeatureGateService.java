package com.lunacode.config;

public interface FeatureGateService {
    boolean enabled(FeatureGate gate);
}
