package com.lunacode.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.stream.StreamEvent;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class ProviderSummaryModelClient implements SummaryModelClient {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SummaryResponseParser parser = new SummaryResponseParser();

    @Override
    public SummaryModelResult summarize(SummaryModelRequest request) {
        if (request == null || request.provider() == null) {
            return SummaryModelResult.failure(SummaryModelResult.FailureType.OTHER, "摘要 Provider 不可用");
        }
        ArrayNode noTools = mapper.createArrayNode();
        String systemPrompt = "你是 LunaCode 的上下文压缩器。严禁调用工具，只输出请求的摘要文本。";
        StringBuilder text = new StringBuilder();
        try (Stream<StreamEvent> events = request.provider().streamChat(
                List.of(new ApiMessage("user", request.prompt())),
                request.providerConfig(),
                noTools,
                systemPrompt
        )) {
            for (StreamEvent event : (Iterable<StreamEvent>) events::iterator) {
                if (event instanceof StreamEvent.ContentDelta delta) {
                    text.append(delta.text());
                } else if (event instanceof StreamEvent.Error error) {
                    return classifyFailure(error.summary());
                }
            }
            return SummaryModelResult.success(parser.parseFinalSummary(text.toString()));
        } catch (Exception e) {
            return classifyFailure(e.getMessage());
        }
    }

    private SummaryModelResult classifyFailure(String message) {
        String safe = message == null ? "" : message;
        String lower = safe.toLowerCase(Locale.ROOT);
        if (lower.contains("prompt too long")
                || lower.contains("context length")
                || lower.contains("maximum context")
                || lower.contains("too many tokens")) {
            return SummaryModelResult.failure(SummaryModelResult.FailureType.PROMPT_TOO_LONG, safe);
        }
        return SummaryModelResult.failure(SummaryModelResult.FailureType.OTHER, safe);
    }
}
