package com.lunacode.hook;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public sealed interface HookAction permits HookAction.Command, HookAction.Prompt, HookAction.Http, HookAction.SubAgent {
    HookActionType type();

    record Command(String command) implements HookAction {
        @Override
        public HookActionType type() {
            return HookActionType.COMMAND;
        }
    }

    record Prompt(String prompt) implements HookAction {
        @Override
        public HookActionType type() {
            return HookActionType.PROMPT;
        }
    }

    record Http(
            URI url,
            String method,
            Map<String, String> headers,
            String body,
            Optional<Duration> timeout
    ) implements HookAction {
        public Http {
            method = method == null || method.isBlank() ? "POST" : method.strip().toUpperCase();
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            body = body == null ? "" : body;
            timeout = timeout == null ? Optional.empty() : timeout;
        }

        @Override
        public HookActionType type() {
            return HookActionType.HTTP;
        }
    }

    record SubAgent(String name, String prompt) implements HookAction {
        @Override
        public HookActionType type() {
            return HookActionType.SUB_AGENT;
        }
    }
}
