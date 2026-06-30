package com.fugary.simple.api.web.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 生成示例数据请求参数
 */
@Data
public class AiGenerateSampleReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Schema 内容
     */
    private String schemaContent;

    /**
     * 项目 ID
     */
    private String projectId;

    /**
     * 文档 ID
     */
    private String docId;
}
