package com.fugary.simple.api.service.ai;

import com.fugary.simple.api.web.vo.AiGenerateSampleReq;

/**
 * AI 生成服务
 */
public interface AiService {

    /**
     * 根据 JSON Schema 生成示例数据
     *
     * @param req 请求参数
     * @return 生成的示例数据 (JSON格式)
     */
    String generateSampleBySchema(AiGenerateSampleReq req);

    /**
     * 是否开启
     *
     * @return
     */
    boolean isEnabled();
}
