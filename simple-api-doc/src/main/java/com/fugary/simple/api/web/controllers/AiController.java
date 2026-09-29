package com.fugary.simple.api.web.controllers;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.service.ai.AiConfigService;
import com.fugary.simple.api.service.ai.AiService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.AiGenericTaskReq;
import com.fugary.simple.api.web.vo.AiStatusVo;
import com.fugary.simple.api.web.vo.SimpleResult;
import lombok.extern.slf4j.Slf4j;
import com.fugary.simple.api.service.ai.agent.AiAgentService;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.web.vo.ai.AiAgentChatReqVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * AI 相关接口
 */
@Slf4j
@RestController
@RequestMapping({"/admin/ai", "/shares/ai"})
public class AiController {

    @Autowired
    private AiService aiService;

    @Autowired
    private AiConfigService aiConfigService;

    @Autowired
    private AiAgentService aiAgentService;

    @Autowired
    @Qualifier("taskScheduler")
    private Executor taskExecutor;

    @GetMapping("/status")
    public SimpleResult<AiStatusVo> getAiStatus() {
        AiStatusVo vo = new AiStatusVo();
        boolean enabled = aiService.isEnabled();
        vo.setEnabled(enabled);
        List<AiConfig> configs = aiConfigService.list(Wrappers.<AiConfig>query()
                .eq("status", 1)
                .isNull("modify_from")
                .orderByDesc("id"));
        if (!configs.isEmpty()) {
            List<AiStatusVo.AiConfigVo> voList = configs.stream()
                    .map(c -> SimpleModelUtils.copy(c, AiStatusVo.AiConfigVo.class))
                    .collect(Collectors.toList());
            vo.setConfigs(voList);
            Integer defaultId = configs.stream()
                    .filter(c -> Objects.equals(c.getIsDefault(), 1))
                    .map(AiConfig::getId)
                    .findFirst()
                    .orElseGet(() -> configs.get(0).getId());
            vo.setDefaultConfigId(defaultId);
        } else {
            vo.setConfigs(Collections.emptyList());
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_0, vo);
    }

    private <T> SimpleResult<T> executeAiTask(Supplier<T> supplier, String errorLogMsg) {
        try {
            return SimpleResultUtils.createSimpleResult(supplier.get());
        } catch (SimpleRuntimeException e) {
            if (StringUtils.isNotBlank(e.getMessage())) {
                return SimpleResultUtils.createError(e.getCode() != null ? e.getCode() : SystemErrorConstants.CODE_500, e.getMessage());
            }
            return SimpleResultUtils.createSimpleResult(e.getCode() != null ? e.getCode() : SystemErrorConstants.CODE_500);
        } catch (Exception e) {
            log.error(errorLogMsg, e);
            return SimpleResultUtils.createError(e.getMessage());
        }
    }

    @PostMapping("/generate-sample")
    public SimpleResult<String> generateSample(@RequestBody AiGenericTaskReq req) {
        String schemaContent = req != null ? req.getSchemaContent() : null;
        if (StringUtils.isBlank(schemaContent)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2018);
        }
        return executeAiTask(() -> aiService.generateSample(req), "AI 生成示例数据失败");
    }

    @PostMapping({"/generate-descriptions", "/caches/generate-descriptions"})
    public SimpleResult<String> generateDescriptions(@RequestBody AiGenericTaskReq req) {
        return executeAiTask(() -> aiService.generateDescriptions(req), "智能补全 Schema 失败");
    }

    @PostMapping(value = "/agent/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter agentChat(@Valid @RequestBody AiAgentChatReqVo req, HttpServletRequest request) {
        if (request != null && request.getServletPath() != null && request.getServletPath().startsWith("/shares/")) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_403, "分享模式不支持 AI 助手提问");
        }
        if (StringUtils.isNotBlank(req.getShareId())) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_403, "分享模式不支持 AI 助手提问");
        }
        if (req.getProjectId() == null || req.getProjectId() <= 0) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_400, "请指定要咨询的 API 项目");
        }
        SseEmitter emitter = new SseEmitter(180_000L);
        AtomicBoolean isCompleted = new AtomicBoolean(false);
        emitter.onCompletion(() -> isCompleted.set(true));
        emitter.onTimeout(() -> {
            isCompleted.set(true);
            try {
                emitter.complete();
            } catch (Exception ignore) {}
        });
        emitter.onError(e -> {
            isCompleted.set(true);
            try {
                emitter.complete();
            } catch (Exception ignore) {}
        });

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        taskExecutor.execute(() -> {
            try {
                if (requestAttributes != null) {
                    RequestContextHolder.setRequestAttributes(requestAttributes, true);
                }
                aiAgentService.streamChat(req, emitter);
            } catch (Exception e) {
                log.error("AI Agent 对话处理异常", e);
                if (!isCompleted.get()) {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(JsonUtils.toJson(Map.of("message", e.getMessage() != null ? e.getMessage() : "AI 服务异常"))));
                    } catch (Exception ignore) {}
                    try {
                        emitter.complete();
                    } catch (Exception ignore) {}
                }
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        return emitter;
    }

    @PostMapping({"/generate-model", "/caches/generate-model"})
    public SimpleResult<String> generateModel(@RequestBody AiGenericTaskReq req) {
        return executeAiTask(() -> aiService.generateModel(req), "生成模型失败");
    }
}
