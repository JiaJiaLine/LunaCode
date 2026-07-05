package com.lunacode.skill;

public sealed interface SkillParseResult permits SkillParseResult.Success, SkillParseResult.Failure {
    record Success(SkillDefinition definition) implements SkillParseResult {
        public Success {
            if (definition == null) {
                throw new IllegalArgumentException("definition is required");
            }
        }
    }

    record Failure(SkillOrigin origin, String reason) implements SkillParseResult {
        public Failure {
            if (origin == null) {
                throw new IllegalArgumentException("origin is required");
            }
            reason = reason == null ? "" : reason;
        }
    }
}
