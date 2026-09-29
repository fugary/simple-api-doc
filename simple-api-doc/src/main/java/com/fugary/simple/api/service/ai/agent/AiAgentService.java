package com.fugary.simple.api.service.ai.agent;

import com.fugary.simple.api.web.vo.ai.AiAgentChatReqVo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 接口文档 Agent 服务
 */
public interface AiAgentService {

    /**
     * 流式执行 AI Agent 对话与多步工具检索
     *
     * @param req     请求参数
     * @param emitter SSE 发射器
     */
    void streamChat(AiAgentChatReqVo req, SseEmitter emitter);
}
