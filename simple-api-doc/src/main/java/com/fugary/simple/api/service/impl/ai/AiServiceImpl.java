package com.fugary.simple.api.service.impl.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.entity.api.AiCache;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.mapper.api.AiCacheMapper;
import com.fugary.simple.api.mapper.api.ApiProjectShareMapper;
import com.fugary.simple.api.service.ai.AiService;
import com.fugary.simple.api.service.ai.AiConfigService;
import com.fugary.simple.api.entity.api.AiConfig;
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
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.utils.servlet.HttpRequestUtils;
import com.fugary.simple.api.web.vo.AiGenerateSampleReq;
import com.fugary.simple.api.web.vo.AiGenericTaskReq;

import java.util.*;
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
    private AiConfigService aiConfigService;

    @Autowired
    private AiCacheMapper aiCacheMapper;

    @Autowired
    private ApiProjectShareMapper apiProjectShareMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("taskScheduler")
    private Executor taskExecutor;

    private static final Pattern MARKDOWN_JSON_PATTERN = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");
    private static final Pattern MARKDOWN_GENERIC_PATTERN = Pattern.compile("```\\s*([\\s\\S]*?)\\s*```");

    @Override
    public String executeGenericTask(AiGenericTaskReq req) {
        AiConfig currentAiConfig = aiConfigService.getDefaultAiConfig();
        if (currentAiConfig == null || StringUtils.isBlank(currentAiConfig.getApiKey())) {
            throw new RuntimeException("AI 生成功能未开启或未配置 API Key");
        }
        String systemPrompt = req.getSystemPrompt();
        String userMessageContent = req.getUserMessage();
        String cacheType = req.getCacheType();
        String projectId = req.getProjectId();
        String docId = req.getDocId();

        String promptHash = DigestUtils.md5Hex(systemPrompt);
        String rawKey = currentAiConfig.getDefaultModel() + ":" + promptHash + ":" + userMessageContent;
        String cacheKey = DigestUtils.sha256Hex(rawKey);

        try {
            AiCache cache = aiCacheMapper.selectById(cacheKey);
            if (cache != null) {
                if (cache.getStatus() != null && cache.getStatus() == 1 && StringUtils.isNotBlank(cache.getCacheValue())) {
                    log.info("AI {} 命中缓存, key: {}", cacheType, cacheKey);
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
            Long pendingCount = aiCacheMapper.selectCount(Wrappers.<AiCache>lambdaQuery().eq(AiCache::getStatus, 0));
            if (pendingCount != null && pendingCount >= aiConfigProperties.getMaxPendingTasks()) {
                throw new SimpleRuntimeException(429, "当前排队中的 AI 请求过多，请稍后再试");
            }
            AiCache aiCache = new AiCache();
            aiCache.setCacheKey(cacheKey);
            aiCache.setStatus(0);
            aiCache.setCacheValue("");
            aiCache.setModelName(currentAiConfig.getDefaultModel());
            aiCache.setCreatedAt(new Date());
            aiCache.setPrompt(systemPrompt + "\n" + userMessageContent);
            aiCache.setProjectId(projectId);
            aiCache.setDocId(docId);
            String userName = SecurityUtils.getLoginUserName();
            if (StringUtils.isBlank(userName)) {
                String shareId = SecurityUtils.getLoginShareId();
                if (StringUtils.isNotBlank(shareId)) {
                    ApiProjectShare share = apiProjectShareMapper.selectOne(Wrappers.<ApiProjectShare>lambdaQuery().eq(ApiProjectShare::getShareId, shareId));
                    if (share != null && StringUtils.isNotBlank(share.getCreator())) {
                        userName = share.getCreator();
                    } else {
                        userName = shareId;
                    }
                }
            }
            aiCache.setUserName(userName);
            aiCache.setClientIp(HttpRequestUtils.getClientIp());
            aiCache.setCacheType(cacheType);
            aiCacheMapper.insert(aiCache);
        } catch (SimpleRuntimeException e) {
            throw e;
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

        String url = currentAiConfig.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(currentAiConfig.getApiKey());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", currentAiConfig.getDefaultModel());
        requestBody.put("temperature", 0.3);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMessageObj = new HashMap<>();
        systemMessageObj.put("role", "system");
        systemMessageObj.put("content", systemPrompt);
        messages.add(systemMessageObj);

        Map<String, String> userMessageObj = new HashMap<>();
        userMessageObj.put("role", "user");
        userMessageObj.put("content", userMessageContent);
        messages.add(userMessageObj);

        requestBody.put("messages", messages);

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String rawResponse = response.getBody();
                JsonNode root = objectMapper.readTree(rawResponse);
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode messageNode = choices.get(0).path("message").path("content");
                    if (!messageNode.isMissingNode()) {
                        String generatedSample = cleanGeneratedContent(messageNode.asText());
                        if (StringUtils.isNotBlank(generatedSample)) {
                            try {
                                JsonNode usageNode = root.path("usage");
                                Integer promptTokens = null, completionTokens = null, totalTokens = null;
                                if (!usageNode.isMissingNode()) {
                                    promptTokens = usageNode.path("prompt_tokens").asInt();
                                    completionTokens = usageNode.path("completion_tokens").asInt();
                                    totalTokens = usageNode.path("total_tokens").asInt();
                                }
                                aiCacheMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<AiCache>lambdaUpdate()
                                        .set(AiCache::getCacheValue, generatedSample)
                                        .set(AiCache::getStatus, 1)
                                        .set(AiCache::getCostTime, System.currentTimeMillis() - startTime)
                                        .set(AiCache::getRawResponse, rawResponse)
                                        .set(AiCache::getUpdatedAt, new java.util.Date())
                                        .set(promptTokens != null, AiCache::getPromptTokens, promptTokens)
                                        .set(completionTokens != null, AiCache::getCompletionTokens, completionTokens)
                                        .set(totalTokens != null, AiCache::getTotalTokens, totalTokens)
                                        .eq(AiCache::getCacheKey, cacheKey));
                                log.info("AI {} 成功，写入缓存, key: {}", cacheType, cacheKey);
                            } catch (Exception cacheEx) {
                                log.error("写入 AI 缓存失败", cacheEx);
                            }
                        }
                        return generatedSample;
                    }
                }
            }
            log.error("AI 接口返回格式异常或调用失败: {}", response.getBody());
            throw new RuntimeException("生成内容失败，AI 返回格式无法解析");
        } catch (Exception e) {
            log.error("调用 AI 接口失败", e);
            throw new RuntimeException(e);
        }
        }, taskExecutor).whenComplete((res, ex) -> {
            if (ex != null) {
                try {
                    String errorMessage = ex.getMessage();
                    if (errorMessage != null && errorMessage.length() > 1000) {
                        errorMessage = errorMessage.substring(0, 1000);
                    }
                    aiCacheMapper.update(null, Wrappers.<AiCache>lambdaUpdate()
                            .set(AiCache::getStatus, 2)
                            .set(AiCache::getCostTime, System.currentTimeMillis() - startTime)
                            .set(AiCache::getUpdatedAt, new java.util.Date())
                            .set(AiCache::getErrorMessage, errorMessage)
                            .eq(AiCache::getCacheKey, cacheKey));
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

    @Override
    public String generateSampleBySchema(AiGenerateSampleReq req) {
        String systemPrompt = "你是一个专门用于生成模拟数据的接口开发助手。请根据用户提供的 OpenAPI/JSON Schema 结构生成合理的示例 JSON 数据。规则：\n" +
                "1. 必须只返回合法的纯 JSON 数据。\n" +
                "2. 不要包含任何多余的解释文字、代码块标记（如 ```json）。\n" +
                "3. 根据 Schema 中的 type、description、example 或 format，生成逼真的模拟数据。\n" +
                "4. 必须全面覆盖所有定义的属性（包括所有的嵌套对象和数组，以及 $ref 引用的组件），不能随意遗漏字段或简化数据结构，数组建议生成1-2条数据。\n" +
                "5. 返回的结果必须是根据 `schema` 结构定义生成的实例数据对象。";
                
        AiGenericTaskReq genericReq = new AiGenericTaskReq();
        genericReq.setSystemPrompt(systemPrompt);
        genericReq.setUserMessage(req.getSchemaContent());
        genericReq.setCacheType("mock_data");
        genericReq.setProjectId(req.getProjectId());
        genericReq.setDocId(req.getDocId());
        
        return executeGenericTask(genericReq);
    }

    @Override
    public boolean isEnabled() {
        if (!aiConfigProperties.isEnabled()) {
            return false;
        }
        AiConfig config = aiConfigService.getDefaultAiConfig();
        return config != null && StringUtils.isNotBlank(config.getApiKey());
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
