package com.fugary.simple.api.service.ai.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 模型返回的工具调用指令
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiToolCall {
    private String id;
    private String name;
    private String arguments;
}
