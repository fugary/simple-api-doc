package com.fugary.simple.api.service.ai.provider;

import com.fugary.simple.api.entity.api.AiConfig;

import java.util.Collections;
import java.util.List;

/**
 * Provider strategy interface for AI chats
 */
public interface AiChatProvider {

    /**
     * Gets the provider code (e.g., "OPENAI", "ANTHROPIC", "GEMINI")
     *
     * @return the provider code
     */
    String getProviderCode();

    /**
     * Execute chat completion
     *
     * @param config  the AI configuration
     * @param request the chat request
     * @return the chat response
     */
    AiChatResponse chat(AiConfig config, AiChatRequest request);

    /**
     * Load available models for this provider
     *
     * @param config the AI configuration
     * @return list of model ids, empty if not supported or failed
     */
    default List<String> loadModels(AiConfig config) {
        return Collections.emptyList();
    }
}
