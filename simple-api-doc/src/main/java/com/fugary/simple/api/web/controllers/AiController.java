package com.fugary.simple.api.web.controllers;

import com.fugary.simple.api.service.AiService;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.utils.SimpleResultUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 相关接口
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    @PostMapping("/generate-sample")
    public SimpleResult<String> generateSample(@RequestBody Map<String, String> params) {
        String schemaContent = params.get("schemaContent");
        if (StringUtils.isBlank(schemaContent)) {
            return SimpleResultUtils.<String>createSimpleResult(500).toBuilder().message("Schema内容不能为空").build();
        }
        try {
            String sample = aiService.generateSampleBySchema(schemaContent);
            return SimpleResultUtils.createSimpleResult(sample);
        } catch (Exception e) {
            return SimpleResultUtils.<String>createSimpleResult(500).toBuilder().message(e.getMessage()).build();
        }
    }
}
