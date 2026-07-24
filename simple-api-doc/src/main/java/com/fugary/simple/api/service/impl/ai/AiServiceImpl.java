package com.fugary.simple.api.service.impl.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.AiCache;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.mapper.api.AiCacheMapper;
import com.fugary.simple.api.mapper.api.ApiProjectShareMapper;
import com.fugary.simple.api.service.ai.AiConfigService;
import com.fugary.simple.api.service.ai.AiService;
import com.fugary.simple.api.service.ai.provider.AiChatProvider;
import com.fugary.simple.api.service.ai.provider.AiChatRequest;
import com.fugary.simple.api.service.ai.provider.AiChatResponse;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.utils.servlet.HttpRequestUtils;
import com.fugary.simple.api.web.vo.AiGenerateSampleReq;
import com.fugary.simple.api.web.vo.AiGenericTaskReq;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    @Qualifier("taskScheduler")
    private Executor taskExecutor;

    @Autowired
    private List<AiChatProvider> chatProviders;

    private AiChatProvider getChatProvider(String providerCode) {
        String code = StringUtils.isBlank(providerCode) ? "OPENAI" : providerCode;
        return chatProviders.stream()
                .filter(p -> code.equalsIgnoreCase(p.getProviderCode()))
                .findFirst()
                .orElseGet(() -> chatProviders.stream()
                        .filter(p -> "OPENAI".equalsIgnoreCase(p.getProviderCode()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No suitable AI provider found")));
    }

    private AiConfig resolveAiConfig(Integer configId) {
        AiConfig config = configId != null ? aiConfigService.getAiConfig(configId) : null;
        if (config == null || !Integer.valueOf(1).equals(config.getStatus())) {
            config = aiConfigService.getDefaultAiConfig();
        }
        return config;
    }

    @Override
    public String executeGenericTask(AiGenericTaskReq req) {
        final AiConfig targetAiConfig = resolveAiConfig(req.getConfigId());
        if (targetAiConfig == null || StringUtils.isBlank(targetAiConfig.getApiKey())) {
            throw new RuntimeException("AI 生成功能未开启或未配置 API Key");
        }
        String systemPrompt = req.getSystemPrompt();
        String userMessageContent = req.getUserMessage();
        String cacheType = req.getCacheType();
        String projectId = req.getProjectId();
        String docId = req.getDocId();

        String promptHash = DigestUtils.md5Hex(systemPrompt);
        String rawKey = targetAiConfig.getDefaultModel() + ":" + promptHash + ":" + userMessageContent;
        String cacheKey = DigestUtils.sha256Hex(rawKey);

        boolean cacheExists = false;
        try {
            AiCache cache = aiCacheMapper.selectById(cacheKey);
            if (cache != null) {
                cacheExists = true;
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
            initAiCache(cacheKey, systemPrompt, userMessageContent, projectId, docId, cacheType, targetAiConfig, cacheExists);
        } catch (SimpleRuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("写入 AI 缓存状态失败", e);
        }
        long startTime = System.currentTimeMillis();
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            AiChatProvider provider = getChatProvider(targetAiConfig.getProvider());
            AiChatRequest chatRequest = new AiChatRequest();
            chatRequest.setSystemPrompt(systemPrompt);
            chatRequest.setUserMessage(userMessageContent);
            chatRequest.setTemperature(0.3); // Default temperature as per original logic

            AiChatResponse chatResponse = provider.chat(targetAiConfig, chatRequest);
            String generatedSample = chatResponse.getContent();

            if (StringUtils.isNotBlank(generatedSample)) {
                updateCacheOnSuccess(cacheKey, chatResponse, System.currentTimeMillis() - startTime);
                log.info("AI {} 成功，写入缓存, key: {}", cacheType, cacheKey);
            }
            return generatedSample;
        }, taskExecutor).whenComplete((res, ex) -> {
            if (ex != null) {
                updateCacheOnFailure(cacheKey, ex, System.currentTimeMillis() - startTime);
            }
        });

        try {
            return future.get(1, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            throw new SimpleRuntimeException(202);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException(getSimpleErrorMessage(cause), cause);
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
        genericReq.setConfigId(req.getConfigId());

        return executeGenericTask(genericReq);
    }

    private void initAiCache(String cacheKey, String systemPrompt, String userMessageContent,
                             String projectId, String docId, String cacheType,
                             AiConfig currentAiConfig, boolean cacheExists) {
        AiCache aiCache = new AiCache();
        aiCache.setCacheKey(cacheKey);
        aiCache.setStatus(0);
        aiCache.setCacheValue("");
        aiCache.setModelName(currentAiConfig.getDefaultModel());
        aiCache.setProvider(currentAiConfig.getProvider());
        aiCache.setBaseUrl(currentAiConfig.getBaseUrl());
        aiCache.setCreatedAt(new Date());
        aiCache.setPrompt(systemPrompt + "\n" + userMessageContent);
        aiCache.setProjectId(projectId);
        aiCache.setDocId(docId);
        String userName = SecurityUtils.getLoginUserName();
        if (StringUtils.isBlank(userName)) {
            String shareId = SecurityUtils.getLoginShareId();
            if (StringUtils.isNotBlank(shareId)) {
                ApiProjectShare share = apiProjectShareMapper.selectOne(
                        Wrappers.<ApiProjectShare>lambdaQuery().eq(ApiProjectShare::getShareId, shareId));
                userName = (share != null && StringUtils.isNotBlank(share.getCreator())) ? share.getCreator() : shareId;
            }
        }
        aiCache.setUserName(userName);
        aiCache.setClientIp(HttpRequestUtils.getClientIp());
        aiCache.setCacheType(cacheType);
        if (cacheExists) {
            aiCacheMapper.updateById(aiCache);
        } else {
            aiCacheMapper.insert(aiCache);
        }
    }

    private void updateCacheOnSuccess(String cacheKey, AiChatResponse chatResponse, long costTime) {
        try {
            aiCacheMapper.update(null, Wrappers.<AiCache>lambdaUpdate()
                    .set(AiCache::getCacheValue, chatResponse.getContent())
                    .set(AiCache::getStatus, 1)
                    .set(costTime > 0, AiCache::getCostTime, costTime)
                    .set(AiCache::getRawResponse, chatResponse.getRawResponse())
                    .set(AiCache::getUpdatedAt, new Date())
                    .set(chatResponse.getPromptTokens() != null, AiCache::getPromptTokens, chatResponse.getPromptTokens())
                    .set(chatResponse.getCompletionTokens() != null, AiCache::getCompletionTokens, chatResponse.getCompletionTokens())
                    .set(chatResponse.getTotalTokens() != null, AiCache::getTotalTokens, chatResponse.getTotalTokens())
                    .eq(AiCache::getCacheKey, cacheKey));
        } catch (Exception cacheEx) {
            log.error("写入 AI 缓存失败", cacheEx);
        }
    }

    private void updateCacheOnFailure(String cacheKey, Throwable ex, long costTime) {
        try {
            String errorMessage = ex.getMessage();
            if (errorMessage != null && errorMessage.length() > 1000) {
                errorMessage = errorMessage.substring(0, 1000);
            }
            aiCacheMapper.update(null, Wrappers.<AiCache>lambdaUpdate()
                    .set(AiCache::getStatus, 2)
                    .set(costTime > 0, AiCache::getCostTime, costTime)
                    .set(AiCache::getErrorMessage, errorMessage)
                    .set(AiCache::getUpdatedAt, new Date())
                    .eq(AiCache::getCacheKey, cacheKey));
        } catch (Exception ignore) {}
    }

    @Override
    public AiChatResponse testAiConfig(Integer configId, String prompt) {
        AiConfig aiConfig = aiConfigService.getById(configId);
        if (aiConfig == null) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_404, "指定的 AI 配置不存在");
        }

        AiChatProvider provider = getChatProvider(aiConfig.getProvider());
        AiChatRequest chatRequest = new AiChatRequest();
        chatRequest.setSystemPrompt("你是一个有用的 AI 助手。");
        chatRequest.setUserMessage(prompt);
        chatRequest.setTemperature(0.5);

        String cacheKey = UUID.randomUUID().toString();
        try {
            initAiCache(cacheKey, chatRequest.getSystemPrompt(), prompt, null, null, "test_config", aiConfig, false);
        } catch (Exception e) {
            log.error("写入 AI 缓存状态失败", e);
        }

        try {
            long startTime = System.currentTimeMillis();
            AiChatResponse chatResponse = provider.chat(aiConfig, chatRequest);
            long endTime = System.currentTimeMillis();
            chatResponse.setElapsedTime(endTime - startTime);

            if (StringUtils.isNotBlank(chatResponse.getContent())) {
                updateCacheOnSuccess(cacheKey, chatResponse, chatResponse.getElapsedTime());
                log.info("AI {} 成功，写入缓存, key: {}", "test_config", cacheKey);
            }
            return chatResponse;
        } catch (Exception e) {
            log.error("AI 测试连接失败", e);
            updateCacheOnFailure(cacheKey, e, 0L);
            throw new SimpleRuntimeException(500, "测试连接失败: " + getSimpleErrorMessage(e));
        }
    }

    private String getSimpleErrorMessage(Throwable e) {
        if (e instanceof org.springframework.web.client.HttpStatusCodeException) {
            org.springframework.web.client.HttpStatusCodeException httpEx = (org.springframework.web.client.HttpStatusCodeException) e;
            return httpEx.getRawStatusCode() + " " + httpEx.getStatusText();
        }
        String message = e.getMessage();
        if (StringUtils.isBlank(message)) {
            return e.getClass().getSimpleName();
        }
        return StringUtils.abbreviate(message, 100);
    }

    @Override
    public boolean isEnabled() {
        if (!aiConfigProperties.isEnabled()) {
            return false;
        }
        AiConfig config = aiConfigService.getDefaultAiConfig();
        return config != null && StringUtils.isNotBlank(config.getApiKey());
    }
}
