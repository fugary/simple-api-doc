package com.fugary.simple.api.service.ai.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AI 工具定义模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiToolDefinition {
    private String name;
    private String description;
    private Map<String, Object> parameters;
}
