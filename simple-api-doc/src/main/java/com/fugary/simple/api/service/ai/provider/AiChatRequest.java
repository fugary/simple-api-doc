package com.fugary.simple.api.service.ai.provider;

import lombok.Data;

@Data
public class AiChatRequest {
    private String systemPrompt;
    private String userMessage;
    private Double temperature;
    private Integer maxTokens;
}
