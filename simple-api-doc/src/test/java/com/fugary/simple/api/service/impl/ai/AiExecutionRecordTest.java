package com.fugary.simple.api.service.impl.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fugary.simple.api.entity.api.AiCache;
import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.mapper.api.AiCacheMapper;
import com.fugary.simple.api.service.ai.provider.AiChatResponse;
import com.fugary.simple.api.service.ai.provider.AiChatProvider;
import com.fugary.simple.api.service.ai.provider.AiStreamSession;
import com.fugary.simple.api.service.ai.AiConfigService;
import com.fugary.simple.api.web.vo.AiGenericTaskReq;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fugary.simple.api.utils.JsonUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiExecutionRecordTest {
    private final AiCacheMapper mapper = mock(AiCacheMapper.class);
    private final List<Map<String, Object>> writes = new ArrayList<>();

    private AiServiceImpl testService(AiChatProvider provider, AiConfig config) {
        AiServiceImpl service = new AiServiceImpl();
        AiConfigService configs = mock(AiConfigService.class);
        when(configs.getById(12)).thenReturn(config);
        when(provider.getProviderCode()).thenReturn("OPENAI");
        ReflectionTestUtils.setField(service, "aiCacheMapper", mapper);
        ReflectionTestUtils.setField(service, "aiConfigService", configs);
        ReflectionTestUtils.setField(service, "chatProviders", List.of(provider));
        ReflectionTestUtils.setField(service, "aiConfigProperties", new AiConfigProperties());
        ReflectionTestUtils.setField(service, "taskExecutor", (Executor) Runnable::run);
        return service;
    }

    private AiGenericTaskReq request() {
        AiConfig config = new AiConfig();
        config.setId(12);
        config.setStatus(1);
        config.setProvider("OPENAI");
        config.setBaseUrl("https://example.invalid");
        config.setApiKey("test");
        config.setDefaultModel("original-model");
        AiGenericTaskReq req = new AiGenericTaskReq();
        req.setConfig(config);
        req.setUserMessage("test question");
        req.setModel("selected-model");
        return req;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void streamingTestPersistsOneRecordForSavedOrUnsavedConfiguration(boolean saved) throws Exception {
        AiChatProvider provider = mock(AiChatProvider.class);
        AiGenericTaskReq req = request();
        AiServiceImpl service = testService(provider, req.getConfig());
        when(provider.chatStream(any(), any(), any())).thenAnswer(call -> {
            assertThat(((AiConfig) call.getArgument(0)).getDefaultModel()).isEqualTo("selected-model");
            AiStreamSession stream = call.getArgument(2);
            stream.text("```json\n");
            stream.text("{}\n```");
            AiChatResponse response = new AiChatResponse();
            response.setContent(stream.getText());
            response.setPromptTokens(10);
            response.setCompletionTokens(5);
            response.setTotalTokens(15);
            response.setRawResponse("{\"private\":\"not sent to browser\"}");
            return response;
        });
        SseEmitter emitter = mock(SseEmitter.class);
        service.streamTestAiConfig(saved ? 12 : null, req, emitter, new AiStreamSession(text -> {}));
        assertThat(req.getConfig().getDefaultModel()).isEqualTo("original-model");
        verify(mapper, times(1)).insert(any(AiCache.class));
        assertThat(writes.get(writes.size() - 1)).containsEntry("status", 1)
                .containsEntry("cache_value", "```json\n{}\n```").containsEntry("total_tokens", 15);
        ArgumentCaptor<SseEmitter.SseEventBuilder> event = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(event.capture());
        assertThat(event.getValue().build().stream().map(part -> String.valueOf(part.getData()))
                .collect(java.util.stream.Collectors.joining())).contains("SUCCESS").doesNotContain("not sent to browser");
        verify(provider, never()).chat(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void streamingTestKeepsPartialContentAndFailureOrInterruptedStatus(boolean cancelled) {
        AiChatProvider provider = mock(AiChatProvider.class);
        AiGenericTaskReq req = request();
        AiServiceImpl service = testService(provider, req.getConfig());
        when(provider.chatStream(any(), any(), any())).thenAnswer(call -> {
            AiStreamSession stream = call.getArgument(2);
            stream.text("partial answer");
            if (cancelled) { stream.cancel(); stream.check(); }
            throw new IllegalStateException("upstream error");
        });
        assertThatThrownBy(() -> service.streamTestAiConfig(null, req, mock(SseEmitter.class), new AiStreamSession(text -> {})))
                .isInstanceOf(RuntimeException.class);
        assertThat(writes.get(writes.size() - 1)).containsEntry("status", cancelled ? 4 : 2)
                .containsEntry("cache_value", "partial answer");
    }

    @Test
    void emptyStreamingResultIsIncomplete() {
        AiChatProvider provider = mock(AiChatProvider.class);
        AiGenericTaskReq req = request();
        AiServiceImpl service = testService(provider, req.getConfig());
        AiChatResponse result = new AiChatResponse();
        result.setContent("");
        when(provider.chatStream(any(), any(), any())).thenReturn(result);
        service.streamTestAiConfig(null, req, mock(SseEmitter.class), new AiStreamSession(text -> {}));
        assertThat(writes.get(writes.size() - 1)).containsEntry("status", 3).containsEntry("cache_value", "");
    }

    @Test
    void oldSynchronousTestStillUsesSynchronousProvider() {
        AiChatProvider provider = mock(AiChatProvider.class);
        AiGenericTaskReq req = request();
        AiServiceImpl service = testService(provider, req.getConfig());
        AiChatResponse result = new AiChatResponse();
        result.setContent("{}");
        when(provider.chat(any(), any())).thenReturn(result);
        assertThat(service.testAiConfig(12, req).getContent()).isEqualTo("{}");
        verify(provider, never()).chatStream(any(), any(), any());
        assertThat(writes.get(writes.size() - 1)).containsKey("execution_detail");
    }

    @ParameterizedTest
    @ValueSource(strings = {"mock_data", "generate_desc", "generate_model"})
    void generationRecordsOneModelCallAndCacheHitDoesNotAddSteps(String type) throws Exception {
        AiChatProvider provider = mock(AiChatProvider.class);
        AiGenericTaskReq req = request();
        req.setConfigId(12);
        req.setSystemPrompt("system prompt");
        req.setCacheType(type);
        AiServiceImpl service = testService(provider, req.getConfig());
        AiChatResponse response = new AiChatResponse();
        response.setContent("{}");
        response.setPromptTokens(10);
        response.setCompletionTokens(5);
        response.setTotalTokens(15);
        when(provider.chat(any(), any())).thenReturn(response);
        assertThat(service.executeGenericTask(req)).isEqualTo("{}");
        ArgumentCaptor<AiCache> inserted = ArgumentCaptor.forClass(AiCache.class);
        verify(mapper).insert(inserted.capture());
        AiCache cache = inserted.getValue();
        assertThat(cache.getCacheType()).isEqualTo(type);
        assertThat(cache.getModelName()).isEqualTo("selected-model");
        Map<String, Object> terminal = writes.get(writes.size() - 1);
        assertThat(terminal).containsEntry("status", 1).containsEntry("total_tokens", 15);
        com.fasterxml.jackson.databind.JsonNode detail = JsonUtils.getMapper().readTree((String) terminal.get("execution_detail"));
        assertThat(detail.path("modelCalls").asInt()).isEqualTo(1);
        assertThat(detail.path("steps").size()).isEqualTo(1);
        assertThat(detail.path("steps").get(0).path("status").asText()).isEqualTo("success");
        cache.setStatus(1);
        cache.setCacheValue("{}");
        when(mapper.selectById(cache.getCacheKey())).thenReturn(cache);
        int writeCount = writes.size();
        assertThat(service.executeGenericTask(req)).isEqualTo("{}");
        assertThat(writes).hasSize(writeCount);
        verify(provider, times(1)).chat(any(), any());
        verify(mapper, times(1)).insert(any(AiCache.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void synchronousEmptyAnswersAreIncompleteInsteadOfPending(boolean generation) throws Exception {
        AiChatProvider provider = mock(AiChatProvider.class);
        AiGenericTaskReq req = request();
        req.setConfigId(12);
        req.setSystemPrompt("system");
        req.setCacheType("mock_data");
        AiServiceImpl service = testService(provider, req.getConfig());
        when(provider.chat(any(), any())).thenReturn(new AiChatResponse());
        if (generation) service.executeGenericTask(req);
        else service.testAiConfig(12, req);
        Map<String, Object> terminal = writes.get(writes.size() - 1);
        assertThat(terminal).containsEntry("status", 3);
        com.fasterxml.jackson.databind.JsonNode detail = JsonUtils.getMapper().readTree((String) terminal.get("execution_detail"));
        assertThat(detail.path("finishReason").asText()).isEqualTo("incomplete");
        assertThat(detail.path("steps").get(0).path("status").asText()).isEqualTo("incomplete");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void synchronousFailuresRecordOneFailedCall(boolean generation) throws Exception {
        AiChatProvider provider = mock(AiChatProvider.class);
        AiGenericTaskReq req = request();
        req.setConfigId(12);
        req.setSystemPrompt("system");
        req.setCacheType("generate_model");
        AiServiceImpl service = testService(provider, req.getConfig());
        when(provider.chat(any(), any())).thenThrow(new IllegalStateException("upstream error"));
        assertThatThrownBy(() -> {
            if (generation) service.executeGenericTask(req);
            else service.testAiConfig(12, req);
        }).isInstanceOf(RuntimeException.class);
        Map<String, Object> terminal = writes.get(writes.size() - 1);
        assertThat(terminal).containsEntry("status", 2).containsEntry("error_message", "upstream error");
        com.fasterxml.jackson.databind.JsonNode detail = JsonUtils.getMapper().readTree((String) terminal.get("execution_detail"));
        assertThat(detail.path("modelCalls").asInt()).isEqualTo(1);
        assertThat(detail.path("steps").get(0).path("status").asText()).isEqualTo("failed");
    }

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), AiCache.class);
        when(mapper.update(isNull(), any())).thenAnswer(invocation -> {
            LambdaUpdateWrapper<AiCache> wrapper = invocation.getArgument(1);
            writes.add(updateValues(wrapper));
            return 1;
        });
    }

    private Map<String, Object> updateValues(LambdaUpdateWrapper<AiCache> wrapper) {
        Map<String, Object> values = new HashMap<>();
        Matcher matcher = Pattern.compile("(\\w+)=#\\{ew.paramNameValuePairs.(\\w+)}").matcher(wrapper.getSqlSet());
        while (matcher.find()) values.put(matcher.group(1), wrapper.getParamNameValuePairs().get(matcher.group(2)));
        return values;
    }

    @Test
    void cancellationBeforeProviderCallDoesNotInventModelStep() throws Exception {
        AiChatProvider provider = mock(AiChatProvider.class);
        AiGenericTaskReq req = request();
        AiServiceImpl service = testService(provider, req.getConfig());
        AiStreamSession stream = new AiStreamSession(text -> {});
        stream.cancel();
        assertThatThrownBy(() -> service.streamTestAiConfig(null, req, mock(SseEmitter.class), stream))
                .isInstanceOf(java.util.concurrent.CancellationException.class);
        verify(provider, never()).chatStream(any(), any(), any());
        Map<String, Object> terminal = writes.get(writes.size() - 1);
        assertThat(terminal).containsEntry("status", 4);
        com.fasterxml.jackson.databind.JsonNode detail = JsonUtils.getMapper().readTree((String) terminal.get("execution_detail"));
        assertThat(detail.path("modelCalls").asInt()).isZero();
        assertThat(detail.path("steps").size()).isZero();
    }

    @Test
    void generationSetupFailureDoesNotInventModelStep() throws Exception {
        AiChatProvider provider = mock(AiChatProvider.class);
        AiGenericTaskReq req = request();
        req.setConfigId(12);
        req.setSystemPrompt("system");
        req.setCacheType("mock_data");
        AiServiceImpl service = testService(provider, req.getConfig());
        ReflectionTestUtils.setField(service, "chatProviders", Collections.emptyList());
        assertThatThrownBy(() -> service.executeGenericTask(req)).isInstanceOf(RuntimeException.class);
        Map<String, Object> terminal = writes.get(writes.size() - 1);
        assertThat(terminal).containsEntry("status", 2);
        com.fasterxml.jackson.databind.JsonNode detail = JsonUtils.getMapper().readTree((String) terminal.get("execution_detail"));
        assertThat(detail.path("modelCalls").asInt()).isZero();
        assertThat(detail.path("steps").size()).isZero();
    }

    @Test
    void generationRetryResetsOldExecutionAndReusesCacheKey() throws Exception {
        AiChatProvider provider = mock(AiChatProvider.class);
        AiGenericTaskReq req = request();
        req.setConfigId(12);
        req.setSystemPrompt("system");
        req.setCacheType("mock_data");
        AiServiceImpl service = testService(provider, req.getConfig());
        AiChatResponse response = new AiChatResponse();
        response.setContent("{}");
        when(provider.chat(any(), any())).thenThrow(new IllegalStateException("first failure")).thenReturn(response);
        assertThatThrownBy(() -> service.executeGenericTask(req)).isInstanceOf(RuntimeException.class);
        ArgumentCaptor<AiCache> inserted = ArgumentCaptor.forClass(AiCache.class);
        verify(mapper).insert(inserted.capture());
        AiCache previous = inserted.getValue();
        previous.setStatus(2);
        previous.setExecutionDetail((String) writes.get(writes.size() - 1).get("execution_detail"));
        when(mapper.selectById(previous.getCacheKey())).thenReturn(previous);
        doAnswer(call -> {
            AiCache reset = call.getArgument(0);
            LambdaUpdateWrapper<AiCache> wrapper = call.getArgument(1);
            assertThat(reset.getCacheKey()).isEqualTo(previous.getCacheKey());
            assertThat(reset.getStatus()).isZero();
            assertThat(updateValues(wrapper)).containsEntry("execution_detail", null).containsEntry("raw_response", null)
                    .containsEntry("error_message", null).containsEntry("total_tokens", null);
            return 1;
        }).when(mapper).update(isNotNull(), any());
        assertThat(service.executeGenericTask(req)).isEqualTo("{}");
        verify(mapper, times(1)).update(isNotNull(), any());
        verify(mapper, times(1)).insert(any(AiCache.class));
        Map<String, Object> terminal = writes.get(writes.size() - 1);
        assertThat(terminal).containsEntry("status", 1);
        com.fasterxml.jackson.databind.JsonNode detail = JsonUtils.getMapper().readTree((String) terminal.get("execution_detail"));
        assertThat(detail.path("modelCalls").asInt()).isEqualTo(1);
        assertThat(detail.path("steps").size()).isEqualTo(1);
        assertThat(detail.path("steps").get(0).path("status").asText()).isEqualTo("success");
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
