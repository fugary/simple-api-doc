package com.fugary.simple.api.service.impl.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fugary.simple.api.entity.api.AiCache;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.mapper.api.AiCacheMapper;
import com.fugary.simple.api.service.ai.provider.AiChatResponse;
import com.fugary.simple.api.utils.JsonUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiExecutionRecordTest {
    private final AiCacheMapper mapper = mock(AiCacheMapper.class);
    private final List<Map<String, Object>> writes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), AiCache.class);
        when(mapper.update(isNull(), any())).thenAnswer(invocation -> {
            LambdaUpdateWrapper<AiCache> wrapper = invocation.getArgument(1);
            Map<String, Object> values = new HashMap<>();
            Matcher matcher = Pattern.compile("(\\w+)=#\\{ew.paramNameValuePairs.(\\w+)}").matcher(wrapper.getSqlSet());
            while (matcher.find()) values.put(matcher.group(1), wrapper.getParamNameValuePairs().get(matcher.group(2)));
            writes.add(values);
            return 1;
        });
    }

    @Test
    void recordsShareIdentityAndAggregatesProviderTotalsWithLatestRawResponse() throws Exception {
        AiConfig config = new AiConfig();
        config.setId(12);
        ApiProjectShare share = new ApiProjectShare();
        share.setId(34);
        share.setShareId("secret-access-token");
        share.setCreator("owner");
        AiExecutionRecord record = new AiExecutionRecord(mapper, config, share, 56, "question");
        AiChatResponse response = new AiChatResponse();
        response.setPromptTokens(10);
        response.setCompletionTokens(5);
        response.setTotalTokens(20);
        response.setRawResponse("{\"id\":\"first\"}");
        record.model(response, 1, 100);
        response.setRawResponse("{\"id\":\"last\"}");
        record.model(response, 2, 100);
        record.finish(1, "answer", "success", null);
        record.finish(4, null, "interrupted", null);
        ArgumentCaptor<AiCache> inserted = ArgumentCaptor.forClass(AiCache.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getConfigId()).isEqualTo(12);
        assertThat(inserted.getValue().getShareRecordId()).isEqualTo(34);
        assertThat(inserted.getValue().getUserName()).isEqualTo("owner");
        assertThat(JsonUtils.toJson(inserted.getValue())).doesNotContain("secret-access-token");
        assertThat(writes).hasSize(3);
        Map<String, Object> terminal = writes.get(2);
        assertThat(terminal).containsEntry("status", 1).containsEntry("total_tokens", 40)
                .containsEntry("prompt_tokens", 20).containsEntry("cache_value", "answer");
        assertThat(writes.get(0)).containsEntry("raw_response", "{\"id\":\"first\"}");
        assertThat(terminal).containsEntry("raw_response", "{\"id\":\"last\"}");
        assertThat((String) terminal.get("execution_detail")).doesNotContain("\"id\"");
        assertThat(JsonUtils.getMapper().readTree((String) terminal.get("execution_detail")).path("usageComplete").asBoolean()).isTrue();
    }

    @Test
    void preservesLatestRawResponseWhenLaterCallsFailOrOmitRawResponse() {
        AiExecutionRecord record = new AiExecutionRecord(mapper, new AiConfig(), null, 1, "question");
        AiChatResponse response = new AiChatResponse();
        response.setRawResponse("{\"id\":\"last\"}");
        record.model(response, 1, 10);
        record.model(new AiChatResponse(), 2, 10);
        record.model(null, 3, 10);
        record.finish(2, null, "failed", "error");
        assertThat(writes).allSatisfy(write ->
                assertThat(write).containsEntry("raw_response", "{\"id\":\"last\"}"));
    }

    @Test
    void missingUsageStaysUnknownAndExecutionHistoryIsBounded() throws Exception {
        AiExecutionRecord record = new AiExecutionRecord(mapper, new AiConfig(), null, 1, "question");
        record.model(new AiChatResponse(), 1, 10);
        for (int index = 0; index < 140; index++) {
            record.step("tool", "get_doc", "x".repeat(3000), "summary", "failed", 1, 10);
        }
        record.finish(3, "fallback", "max_rounds", null);
        Map<String, Object> terminal = writes.get(writes.size() - 1);
        assertThat(terminal).containsEntry("prompt_tokens", null).containsEntry("completion_tokens", null).containsEntry("total_tokens", null);
        com.fasterxml.jackson.databind.JsonNode detail = JsonUtils.getMapper().readTree((String) terminal.get("execution_detail"));
        assertThat(detail.path("usageComplete").asBoolean()).isFalse();
        assertThat(detail.path("truncated").asBoolean()).isTrue();
        assertThat(detail.path("steps").size()).isEqualTo(128);
        assertThat(detail.path("toolCalls").asInt()).isEqualTo(140);
    }
}
