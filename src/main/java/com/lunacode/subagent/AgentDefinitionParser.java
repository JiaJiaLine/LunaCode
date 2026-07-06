package com.lunacode.subagent;

public interface AgentDefinitionParser {
    AgentDefinitionParseResult parse(AgentDefinitionCandidate candidate);
}
