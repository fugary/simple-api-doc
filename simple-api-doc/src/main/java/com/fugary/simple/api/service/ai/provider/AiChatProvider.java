package com.fugary.simple.api.service.ai.provider;

import com.fugary.simple.api.entity.api.AiConfig;

import com.fugary.simple.api.service.ai.agent.tool.AiToolDefinition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
     * Execute chat completion with tools (Function Calling)
     *
     * @param config   the AI configuration
     * @param messages conversation messages (system, user, assistant, tool)
     * @param tools    available tools
     * @return the chat response with content or tool calls
     */
    default AiChatResponse chatWithTools(AiConfig config, List<Map<String, Object>> messages, List<AiToolDefinition> tools) {
        return chatWithTools(config, messages, tools, "auto");
    }

    /**
     * Execute chat completion with tools (Function Calling) and explicit tool_choice
     *
     * @param config     the AI configuration
     * @param messages   conversation messages (system, user, assistant, tool)
     * @param tools      available tools
     * @param toolChoice tool choice strategy ("auto", "none", or specific function)
     * @return the chat response with content or tool calls
     */
    default AiChatResponse chatWithTools(AiConfig config, List<Map<String, Object>> messages, List<AiToolDefinition> tools, String toolChoice) {
        throw new UnsupportedOperationException("Provider " + getProviderCode() + " does not support chatWithTools");
    }

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
