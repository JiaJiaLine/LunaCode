package com.lunacode.conversation;

import com.lunacode.context.ExternalizedToolResultRef;

import java.util.List;
import java.util.Map;

public record ConversationMessageMetadata(
        boolean contextSummary,
        List<ExternalizedToolResultRef> externalizedToolResults,
        Map<String, String> attributes
) {
    public ConversationMessageMetadata {
        externalizedToolResults = externalizedToolResults == null ? List.of() : List.copyOf(externalizedToolResults);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static ConversationMessageMetadata empty() {
        return new ConversationMessageMetadata(false, List.of(), Map.of());
    }

    public ConversationMessageMetadata asContextSummary() {
        return new ConversationMessageMetadata(true, externalizedToolResults, attributes);
    }

    public ConversationMessageMetadata withExternalizedToolResult(ExternalizedToolResultRef ref) {
        if (ref == null) {
            return this;
        }
        java.util.ArrayList<ExternalizedToolResultRef> refs = new java.util.ArrayList<>(externalizedToolResults);
        refs.add(ref);
        return new ConversationMessageMetadata(contextSummary, refs, attributes);
    }
}
