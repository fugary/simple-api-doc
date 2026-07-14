package com.fugary.simple.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 相关配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "simple.api.ai")
public class AiConfigProperties {

    /**
     * 是否启用 AI 生成功能
     */
    private boolean enabled = true;

    /**
     * AI Provider (OPENAI, ANTHROPIC, GEMINI)
     */
    private String provider = "OPENAI";

    /**
     * AI 接口 Base URL
     */
    private String baseUrl = "https://api.openai.com/v1";

    /**
     * AI API Key
     */
    private String apiKey;

    /**
     * AI 模型名称，例如 gpt-3.5-turbo, gpt-4o, glm-4
     */
    private String model = "gpt-3.5-turbo";

    /**
     * 调用超时时间（毫秒）
     */
    private int timeout = 600000;

    /**
     * 最大排队和处理中的任务数量限制
     */
    private int maxPendingTasks = 10;
}
