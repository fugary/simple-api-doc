package com.fugary.simple.api.service.ai.provider;

import com.fugary.simple.api.entity.api.AiConfig;

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
}
