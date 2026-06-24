package com.fugary.simple.api.service;

/**
 * AI 生成服务
 */
public interface AiService {

    /**
     * 根据 JSON Schema 生成示例数据
     *
     * @param schemaContent JSON Schema 内容
     * @return 生成的示例数据 (JSON格式)
     */
    String generateSampleBySchema(String schemaContent);

}
