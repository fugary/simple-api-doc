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
     * 是否自动导入环境中的 AI 配置；不控制数据库配置的运行时可用性。
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

    /** 项目概览的字符预算；超限时去掉摘要，仍超限则退回按需搜索。 */
    private int projectOverviewMaxChars = 100000;

    /** 文档详情单次返回的字符数，剩余内容通过 get_doc 的 offset 续读。 */
    private int docDetailPageChars = 12000;

    /** 单次问答最多调用模型检索/查阅的轮数，不含必要时的最终回答。 */
    private int maxToolRounds = 12;
}
