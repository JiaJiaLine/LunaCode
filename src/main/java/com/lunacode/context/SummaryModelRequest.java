package com.lunacode.context;

import com.lunacode.config.ProviderConfig;
import com.lunacode.provider.ChatProvider;

public record SummaryModelRequest(
        ChatProvider provider,
        ProviderConfig providerConfig,
        String prompt
) {}
