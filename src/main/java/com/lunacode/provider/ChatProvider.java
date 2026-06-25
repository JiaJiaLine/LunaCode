package com.lunacode.provider;

import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.stream.StreamEvent;

import java.util.List;
import java.util.stream.Stream;

public interface ChatProvider {
    Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config);
}