package com.fugary.simple.api.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.entity.api.AiCache;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.mapper.api.AiCacheMapper;
import com.fugary.simple.api.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Autowired
    private AiConfigProperties aiConfigProperties;

    @Autowired
    private AiCacheMapper aiCacheMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("taskScheduler")
    private Executor taskExecutor;

    private static final Pattern MARKDOWN_JSON_PATTERN = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");
    private static final Pattern MARKDOWN_GENERIC_PATTERN = Pattern.compile("```\\s*([\\s\\S]*?)\\s*```");

    @Override
    public String generateSampleBySchema(String schemaContent) {
        if (!aiConfigProperties.isEnabled() || StringUtils.isBlank(aiConfigProperties.getApiKey())) {
            throw new RuntimeException("AI 生成功能未开启或未配置 API Key");
        }

        String systemPrompt = "你是一个专门用于生成模拟数据的接口开发助手。请根据用户提供的 OpenAPI/JSON Schema 结构生成合理的示例 JSON 数据。规则：\n" +
                "1. 必须只返回合法的纯 JSON 数据。\n" +
                "2. 不要包含任何多余的解释文字、代码块标记（如 ```json）。\n" +
                "3. 根据 Schema 中的 type、description、example 或 format，生成逼真的模拟数据。\n" +
                "4. 必须全面覆盖所有定义的属性（包括所有的嵌套对象和数组，以及 $ref 引用的组件），不能随意遗漏字段或简化数据结构，数组建议生成1-2条数据。\n" +
                "5. 返回的结果必须是根据 `schema` 结构定义生成的实例数据对象。";

        String promptHash = DigestUtils.md5Hex(systemPrompt);
        String rawKey = aiConfigProperties.getModel() + ":" + promptHash + ":" + schemaContent;
        String cacheKey = DigestUtils.sha256Hex(rawKey);

        try {
            AiCache cache = aiCacheMapper.selectById(cacheKey);
            if (cache != null) {
                if (cache.getStatus() != null && cache.getStatus() == 1 && StringUtils.isNotBlank(cache.getCacheValue())) {
                    log.info("AI 样本生成命中缓存, key: {}", cacheKey);
                    return cache.getCacheValue();
                } else if (cache.getStatus() != null && cache.getStatus() == 0) {
                    throw new SimpleRuntimeException(202, "已加入请求队列，请稍后重试");
                }
            }
        } catch (SimpleRuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取 AI 缓存失败，将降级直接调用 AI", e);
        }
        try {
            AiCache aiCache = new AiCache();
            aiCache.setCacheKey(cacheKey);
            aiCache.setStatus(0);
            aiCache.setCacheValue("");
            aiCache.setModelName(aiConfigProperties.getModel());
            aiCache.setCreatedAt(new java.util.Date());
            aiCacheMapper.insert(aiCache);
        } catch (Exception e) {
            log.error("写入 AI 缓存状态失败", e);
        }
        long startTime = System.currentTimeMillis();
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
        RestTemplate restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(aiConfigProperties.getTimeout());
        requestFactory.setReadTimeout(aiConfigProperties.getTimeout());
        restTemplate.setRequestFactory(requestFactory);

        String url = aiConfigProperties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiConfigProperties.getApiKey());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", aiConfigProperties.getModel());
        requestBody.put("temperature", 0.3);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        messages.add(systemMessage);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", schemaContent);
        messages.add(userMessage);

        requestBody.put("messages", messages);

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode messageNode = choices.get(0).path("message").path("content");
                    if (!messageNode.isMissingNode()) {
                        String generatedSample = cleanGeneratedContent(messageNode.asText());
                        if (StringUtils.isNotBlank(generatedSample)) {
                            try {
                                AiCache aiCache = new AiCache();
                                aiCache.setCacheKey(cacheKey);
                                aiCache.setCacheValue(generatedSample);
                                aiCache.setStatus(1);
                                aiCache.setCostTime(System.currentTimeMillis() - startTime);
                                aiCacheMapper.updateById(aiCache);
                                log.info("AI 样本生成成功，写入缓存, key: {}", cacheKey);
                            } catch (Exception cacheEx) {
                                log.error("写入 AI 缓存失败", cacheEx);
                            }
                        }
                        return generatedSample;
                    }
                }
            }
            log.error("AI 接口返回格式异常或调用失败: {}", response.getBody());
            throw new RuntimeException("生成示例数据失败，AI 返回格式无法解析");
        } catch (Exception e) {
            log.error("调用 AI 接口失败", e);
            throw new RuntimeException(e);
        }
        }, taskExecutor).whenComplete((res, ex) -> {
            if (ex != null) {
                try {
                    AiCache aiCache = new AiCache();
                    aiCache.setCacheKey(cacheKey);
                    aiCache.setStatus(2);
                    aiCache.setCostTime(System.currentTimeMillis() - startTime);
                    aiCacheMapper.updateById(aiCache);
                } catch (Exception ignore) {}
            }
        });

        try {
            return future.get(1, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            throw new SimpleRuntimeException(202);
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
        }
    }

    private String cleanGeneratedContent(String content) {
        if (StringUtils.isBlank(content)) {
            return "{}";
        }
        content = content.trim();
        // 尝试去掉 ```json 和 ```
        Matcher jsonMatcher = MARKDOWN_JSON_PATTERN.matcher(content);
        if (jsonMatcher.find()) {
            return jsonMatcher.group(1).trim();
        }
        Matcher genericMatcher = MARKDOWN_GENERIC_PATTERN.matcher(content);
        if (genericMatcher.find()) {
            return genericMatcher.group(1).trim();
        }
        return content;
    }
}
