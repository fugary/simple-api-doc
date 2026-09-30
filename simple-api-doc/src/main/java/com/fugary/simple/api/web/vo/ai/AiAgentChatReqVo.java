package com.fugary.simple.api.web.vo.ai;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * AI Agent 对话请求参数
 */
@Data
public class AiAgentChatReqVo {
    @NotBlank
    @Size(max = 2000)
    private String query;

    private Integer projectId;

    private Integer configId;

    private String model;

    private String shareId;
}
