package com.fugary.simple.api.web.controllers;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.service.ai.AiConfigService;
import com.fugary.simple.api.service.ai.AiService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.AiGenerateSampleReq;
import com.fugary.simple.api.web.vo.AiStatusVo;
import com.fugary.simple.api.web.vo.SimpleResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 相关接口
 */
@RestController
@RequestMapping({"/admin/ai", "/shares/ai"})
public class AiController {

    @Autowired
    private AiService aiService;

    @Autowired
    private AiConfigService aiConfigService;

    @GetMapping("/status")
    public SimpleResult<AiStatusVo> getAiStatus() {
        AiStatusVo vo = new AiStatusVo();
        boolean enabled = aiService.isEnabled();
        vo.setEnabled(enabled);
        List<AiConfig> configs = aiConfigService.list(Wrappers.<AiConfig>query()
                .eq("status", 1)
                .isNull("modify_from")
                .orderByDesc("is_default")
                .orderByDesc("id"));
        if (!configs.isEmpty()) {
            List<AiStatusVo.AiConfigVo> voList = configs.stream()
                    .map(c -> SimpleModelUtils.copy(c, AiStatusVo.AiConfigVo.class))
                    .collect(Collectors.toList());
            vo.setConfigs(voList);
            vo.setDefaultConfigId(configs.get(0).getId());
        } else {
            vo.setConfigs(Collections.emptyList());
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_0, vo);
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
