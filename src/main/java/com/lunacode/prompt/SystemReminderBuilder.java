package com.lunacode.prompt;

import java.util.List;

public final class SystemReminderBuilder {
    private final PlanModeReminderPolicy planModeReminderPolicy;

    public SystemReminderBuilder() {
        this(new PlanModeReminderPolicy());
    }

    public SystemReminderBuilder(PlanModeReminderPolicy planModeReminderPolicy) {
        this.planModeReminderPolicy = planModeReminderPolicy;
    }

    public List<SystemReminder> build(ModeInjectionState state) {
        return planModeReminderPolicy.createReminder(state).stream().toList();
    }
}
