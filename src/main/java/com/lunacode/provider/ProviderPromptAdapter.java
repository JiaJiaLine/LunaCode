package com.lunacode.provider;

import com.lunacode.prompt.PromptBundle;
import com.lunacode.config.ProviderConfig;

public interface ProviderPromptAdapter {
    String buildRequestBody(PromptBundle promptBundle, ProviderConfig config) throws Exception;
}
