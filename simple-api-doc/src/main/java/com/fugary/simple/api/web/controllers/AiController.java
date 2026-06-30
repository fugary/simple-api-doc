package com.fugary.simple.api.web.controllers;

import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.service.AiService;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.AiGenerateSampleReq;
import com.fugary.simple.api.web.vo.SimpleResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * AI 相关接口
 */
@RestController
@RequestMapping({"/api/ai", "/shares/ai"})
public class AiController {

    @Autowired
    private AiService aiService;

    @Autowired
    private AiConfigProperties aiConfigProperties;

    @GetMapping("/status")
    public SimpleResult<Boolean> getAiStatus() {
        boolean enabled = aiConfigProperties.isEnabled() && StringUtils.isNotBlank(aiConfigProperties.getApiKey());
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_0, enabled);
    }

    @PostMapping("/generate-sample")
    public SimpleResult<String> generateSample(@RequestBody AiGenerateSampleReq req) {
        String schemaContent = req.getSchemaContent();
        if (StringUtils.isBlank(schemaContent)) {
            return SimpleResultUtils.<String>createSimpleResult(500).toBuilder().message("Schema内容不能为空").build();
        }
        try {
            String sample = aiService.generateSampleBySchema(req);
            return SimpleResultUtils.createSimpleResult(sample);
        } catch (SimpleRuntimeException e) {
            return SimpleResultUtils.<String>createSimpleResult(e.getCode() != null ? e.getCode() : 500).toBuilder().message(e.getMessage()).build();
        } catch (Exception e) {
            return SimpleResultUtils.<String>createSimpleResult(500).toBuilder().message(e.getMessage()).build();
        }
    }
}
