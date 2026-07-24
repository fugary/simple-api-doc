package com.fugary.simple.api.web.vo;

import lombok.Data;

@Data
public class AiGenericTaskReq {
    /**
     * System prompt defining the AI's role and rules
     */
    private String systemPrompt;

    /**
     * User's message or context content (e.g. schema content)
     */
    private String userMessage;

    /**
     * The type of the cache / task (e.g., mock_data, generate_desc)
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
}
