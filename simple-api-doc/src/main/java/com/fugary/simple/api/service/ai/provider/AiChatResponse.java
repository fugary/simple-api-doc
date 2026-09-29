package com.fugary.simple.api.service.ai.provider;

import com.fugary.simple.api.service.ai.agent.tool.AiToolCall;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiChatResponse {
    private String content;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String rawResponse;
    private Long elapsedTime;
    private List<AiToolCall> toolCalls;
    private Map<String, Object> assistantMessage;
}
