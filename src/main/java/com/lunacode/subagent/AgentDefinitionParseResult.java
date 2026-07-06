package com.lunacode.subagent;

public sealed interface AgentDefinitionParseResult permits AgentDefinitionParseResult.Success, AgentDefinitionParseResult.Failure {
    record Success(AgentDefinition definition) implements AgentDefinitionParseResult {}

    record Failure(AgentDefinitionCandidate candidate, String reason) implements AgentDefinitionParseResult {
        public Failure {
            reason = reason == null ? "" : reason;
        }
    }
}
