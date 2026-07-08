package com.lunacode.coordinator;

import com.lunacode.config.FeatureGate;
import com.lunacode.config.FeatureGateService;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DefaultCoordinatorModeResolver implements CoordinatorModeResolver {
    public static final String ENV_NAME = "LUNACODE_COORDINATOR_MODE";
    public static final Set<String> ALLOWED_TOOLS = Set.of(
            "Agent",
            "SendMessage",
            "TaskCreate",
            "TaskGet",
            "TaskList",
            "TaskUpdate",
            "TeamCreate",
            "TeamDelete",
            "ReadFile",
            "Glob",
            "Grep",
            "Bash"
    );

    private final FeatureGateService featureGateService;
    private final Map<String, String> environment;
    private final CoordinatorPromptContributor promptContributor;

    public DefaultCoordinatorModeResolver(FeatureGateService featureGateService) {
        this(featureGateService, System.getenv(), new CoordinatorPromptContributor());
    }

    public DefaultCoordinatorModeResolver(
            FeatureGateService featureGateService,
            Map<String, String> environment,
            CoordinatorPromptContributor promptContributor
    ) {
        this.featureGateService = Objects.requireNonNull(featureGateService, "featureGateService");
        this.environment = environment == null ? Map.of() : Map.copyOf(environment);
        this.promptContributor = promptContributor == null ? new CoordinatorPromptContributor() : promptContributor;
    }

    @Override
    public CoordinatorModeState resolve() {
        if (!featureGateService.enabled(FeatureGate.COORDINATOR_MODE) || !truthy(environment.get(ENV_NAME))) {
            return CoordinatorModeState.disabled();
        }
        return new CoordinatorModeState(true, ALLOWED_TOOLS, promptContributor.prompt());
    }

    private boolean truthy(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return normalized.equals("1")
                || normalized.equals("true")
                || normalized.equals("yes")
                || normalized.equals("y")
                || normalized.equals("on");
    }
}
