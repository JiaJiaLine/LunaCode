package com.lunacode.context;

import java.time.Instant;

public record UsedSkillDefinition(
        String name,
        String definition,
        Instant usedAt
) {}
