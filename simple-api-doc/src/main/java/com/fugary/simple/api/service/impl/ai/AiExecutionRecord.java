package com.fugary.simple.api.service.impl.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fugary.simple.api.entity.api.AiCache;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.mapper.api.AiCacheMapper;
import com.fugary.simple.api.service.ai.provider.AiChatResponse;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.utils.servlet.HttpRequestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/** 单次问答的有界执行记录；回调与执行线程共享同一个终态。 */
@Slf4j
class AiExecutionRecord {
    static final String AGENT_CHAT = "agent_chat";
    private final AiCacheMapper mapper;
    private final AiCache record;
    private final long startedAt = System.currentTimeMillis();
    private final List<Map<String, Object>> steps = new ArrayList<>();
    private boolean finished;
    private boolean usageComplete = true;
    private int modelCalls;
    private int toolCalls;
    private boolean truncated;

    AiExecutionRecord(AiCacheMapper mapper, AiConfig config, ApiProjectShare share, Integer projectId, String query) {
        this(mapper, config, share, projectId, query, AGENT_CHAT);
    }

    AiExecutionRecord(AiCacheMapper mapper, AiConfig config, ApiProjectShare share, Integer projectId, String query, String cacheType) {
        this.mapper = mapper;
        record = create(config, share);
        record.setCacheKey(UUID.randomUUID().toString());
        record.setCacheType(cacheType);
        record.setProjectId(projectId == null ? null : String.valueOf(projectId));
        record.setPrompt(query);
        try {
            mapper.insert(record);
        } catch (Exception e) {
            log.warn("Cannot save AI execution record", e);
        }
    }

    static AiCache create(AiConfig config, ApiProjectShare share) {
        AiCache record = new AiCache();
        record.setConfigId(config.getId());
        record.setModelName(config.getDefaultModel());
        record.setProvider(config.getProvider());
        record.setBaseUrl(config.getBaseUrl());
        record.setStatus(0);
        record.setCacheValue("");
        record.setCreatedAt(new Date());
        record.setUpdatedAt(record.getCreatedAt());
        record.setUserName(share == null ? SecurityUtils.getLoginUserName() : share.getCreator());
        record.setShareRecordId(share == null ? null : share.getId());
        record.setClientIp(HttpRequestUtils.getClientIp());
        return record;
    }

    synchronized void model(AiChatResponse response, int round, long duration) {
        modelCalls++;
        usageComplete &= response != null && response.getPromptTokens() != null
                && response.getCompletionTokens() != null && response.getTotalTokens() != null;
        if (response != null) {
            // 仅保留最近一次原始响应，后续调用失败或未返回原文时不覆盖。
            if (StringUtils.isNotBlank(response.getRawResponse())) {
                record.setRawResponse(response.getRawResponse());
            }
            record.setPromptTokens(add(record.getPromptTokens(), response.getPromptTokens()));
            record.setCompletionTokens(add(record.getCompletionTokens(), response.getCompletionTokens()));
            record.setTotalTokens(add(record.getTotalTokens(), response.getTotalTokens()));
        }
        step("model", null, null, null, response == null ? "failed" : "success", round, duration);
    }

    private Integer add(Integer total, Integer value) {
        if (value == null) return total;
        return (int) Math.min(Integer.MAX_VALUE, (long) (total == null ? 0 : total) + value);
    }

    synchronized void step(String type, String tool, String args, String summary, String status, int round, long duration) {
        if (finished) return;
        if ("tool".equals(type) && !"skipped".equals(status)) toolCalls++;
        truncated |= steps.size() >= 128 || StringUtils.length(args) > 2000 || StringUtils.length(summary) > 500;
        if (steps.size() < 128) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("type", type);
            if (tool != null) step.put("tool", StringUtils.left(tool, 100));
            if (args != null) step.put("args", StringUtils.left(args, 2000));
            if (summary != null) step.put("summary", StringUtils.left(summary, 500));
            step.put("status", status);
            step.put("round", round);
            step.put("duration", duration);
            steps.add(step);
        }
        persist(0, null, null, null);
    }

    synchronized void finish(int status, String content, String reason, String error) {
        if (finished) return;
        finished = true;
        persist(status, content, reason, error);
    }

    private void persist(int status, String content, String reason, String error) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("version", 1);
        detail.put("steps", steps);
        detail.put("finishReason", reason);
        detail.put("usageComplete", modelCalls > 0 && usageComplete);
        detail.put("modelCalls", modelCalls);
        detail.put("toolCalls", toolCalls);
        detail.put("truncated", truncated);
        try {
            mapper.update(null, Wrappers.<AiCache>lambdaUpdate()
                    .eq(AiCache::getCacheKey, record.getCacheKey()).eq(AiCache::getStatus, 0)
                    .set(AiCache::getStatus, status)
                    .set(content != null, AiCache::getCacheValue, content)
                    .set(record.getRawResponse() != null, AiCache::getRawResponse, record.getRawResponse())
                    .set(AiCache::getErrorMessage, StringUtils.left(error, 2000))
                    .set(AiCache::getExecutionDetail, JsonUtils.toJson(detail))
                    .set(AiCache::getPromptTokens, record.getPromptTokens())
                    .set(AiCache::getCompletionTokens, record.getCompletionTokens())
                    .set(AiCache::getTotalTokens, record.getTotalTokens())
                    .set(AiCache::getCostTime, System.currentTimeMillis() - startedAt)
                    .set(AiCache::getUpdatedAt, new Date()));
        } catch (Exception e) {
            log.warn("Cannot update AI execution record", e);
        }
    }
}
