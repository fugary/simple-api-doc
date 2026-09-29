package com.fugary.simple.api.service.ai.agent.tool;

import java.util.Map;

/**
 * 供 AI 调用的工具接口
 */
public interface AgentTool {

    /**
     * 工具唯一标识名（如 search_docs）
     */
    String getName();

    /**
     * 工具功能说明
     */
    String getDescription();

    /**
     * 参数定义 (JSON Schema Map)
     */
    Map<String, Object> getParameters();

    /**
     * 执行工具
     *
     * @param argumentsJson 模型传入的实参 JSON 字符串
     * @param context       当前执行上下文
     * @return 工具执行结果（通常为精简的 JSON 或格式化文本）
     */
    String execute(String argumentsJson, AgentContext context);

    /**
     * 转换为 Provider 注册所需的工具定义对象
     */
    default AiToolDefinition toDefinition() {
        return AiToolDefinition.builder()
                .name(getName())
                .description(getDescription())
                .parameters(getParameters())
                .build();
    }
}
