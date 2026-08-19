package com.fugary.simple.api.web.vo;

import com.fugary.simple.api.entity.api.AiConfig;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 通用任务请求参数
 */
@Data
public class AiGenericTaskReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * System prompt defining the AI's role and rules
     */
    private String systemPrompt;

    /**
     * User's message or context content (e.g. schema content)
     */
    private String userMessage;

    /**
     * User prompt text (e.g. business requirement or custom instructions)
     */
    private String prompt;

    /**
     * Schema content (JSON Schema string)
     */
    private String schemaContent;

    /**
     * Language preference (e.g. "zh-CN", "en-US")
     */
    private String lang;

    /**
     * Execution mode (e.g. "all", "missing")
     */
    private String mode;

    /**
     * Whether to generate examples
     */
    private Boolean withExample;

    /**
     * The type of the cache / task (e.g., mock_data, generate_desc, generate_model, test_config)
     */
    private String cacheType;

    /**
     * Associated project ID
     */
    private String projectId;

    /**
     * Associated document ID
     */
    private String docId;

    /**
     * Selected AI Config ID
     */
    private Integer configId;

    /**
     * Override model for testing (optional)
     */
    private String model;

    /**
     * Optional custom AI config (for testing unsaved configs)
     */
    private AiConfig config;
}
