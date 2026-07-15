package com.fugary.simple.api.service.ai;

import com.fugary.simple.api.web.vo.AiGenerateSampleReq;
import com.fugary.simple.api.web.vo.AiGenericTaskReq;


import com.fugary.simple.api.service.ai.provider.AiChatResponse;

/**
 * AI 生成服务
 */
public interface AiService {

    /**
     * 执行通用 AI 任务 (例如生成缺失描述、Mock数据等)
     * @param req 通用请求参数
     * @return AI 生成的 JSON 内容
     */
    String executeGenericTask(AiGenericTaskReq req);

    /**
     * 根据 JSON Schema 生成示例数据
     *
     * @param req 请求参数
     * @return 生成的示例数据 (JSON格式)
     */
    String generateSampleBySchema(AiGenerateSampleReq req);

    /**
     * 测试指定的 AI 配置
     *
     * @param configId AI 配置 ID
     * @param prompt 测试提示词
     * @return AI 返回的内容及耗时/Token使用情况
     */
    AiChatResponse testAiConfig(Integer configId, String prompt);

    /**
     * 是否开启
     *
     * @return
     */
    boolean isEnabled();
}
