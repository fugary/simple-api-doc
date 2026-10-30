package com.fugary.simple.api.service.impl.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.mapper.api.AiConfigMapper;
import com.fugary.simple.api.service.ai.AiService;
import com.fugary.simple.api.service.ai.provider.AiChatResponse;
import com.fugary.simple.api.service.ai.provider.AiSseSupport;
import com.fugary.simple.api.web.controllers.admin.AiConfigController;
import com.fugary.simple.api.web.vo.AiGenericTaskReq;
import com.fugary.simple.api.web.vo.query.SimpleQueryVo;
import com.fugary.simple.api.web.vo.query.ai.AiConfigQueryVo;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-config-access;DB_CLOSE_DELAY=-1",
        "dbs.h2.data-dir=target/ai-config-access-test",
        "logging.file.name=target/ai-config-access-test/test.log"
})
class AiConfigAccessTest {
    @Autowired AiConfigMapper mapper;
    @Autowired AiConfigController controller;
    @MockBean AiService aiService;
    @MockBean AiSseSupport sseSupport;
    AiConfig config;

    @BeforeEach
    void setup() {
        mapper.delete(new QueryWrapper<>());
        login("alice");
        config = new AiConfig();
        config.setConfigName("test-config");
        config.setProvider("OPENAI");
        config.setBaseUrl("https://example.com/v1");
        config.setApiKey("test-secret");
        config.setDefaultModel("test-model");
        config.setStatus(1);
        config.setIsDefault(0);
        config.setCreator("admin");
        config.setCreateDate(new Date());
        mapper.insert(config);
    }

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void userReadsWithoutKeyAndDatabaseRetainsKey() {
        assertThat(controller.search(new AiConfigQueryVo()).getResultData())
                .singleElement().satisfies(row -> {
                    assertThat(row.getApiKey()).isNull();
                    assertThat(row.getConfigName()).isEqualTo("test-config");
                });
        assertThat(controller.get(config.getId()).getResultData().getApiKey()).isNull();
        assertThat(mapper.selectById(config.getId()).getApiKey()).isEqualTo("test-secret");
    }

    @Test
    void userCannotManageOrReadHistoryThroughAnyRoute() {
        forbidden(() -> controller.save(config));
        forbidden(() -> controller.save(new AiConfig()));
        forbidden(() -> controller.delete(config.getId()));
        forbidden(() -> controller.recoverFromHistory(new SimpleQueryVo()));
        forbidden(() -> controller.histories(new AiConfigQueryVo(), config.getId()));
        forbidden(() -> controller.loadHistoryDiff(new SimpleQueryVo()));
        AiConfigQueryVo query = new AiConfigQueryVo();
        query.setModifyFrom(config.getId());
        forbidden(() -> controller.search(query));
        config.setModifyFrom(100);
        mapper.updateById(config);
        forbidden(() -> controller.get(config.getId()));
        assertThat(mapper.selectById(config.getId())).isNotNull();
    }

    @Test
    void userCanTestAndLoadModelsBySavedConfigId() {
        AiGenericTaskReq req = request();
        when(aiService.testAiConfig(eq(config.getId()), same(req))).thenReturn(new AiChatResponse());
        when(aiService.loadModels(config.getId())).thenReturn(Collections.singletonList("test-model"));
        assertThat(controller.test(config.getId(), req).isSuccess()).isTrue();
        req.setConfigId(config.getId());
        assertThat(controller.test(null, req).isSuccess()).isTrue();
        assertThat(controller.loadModels(config.getId()).getResultData()).containsExactly("test-model");
        controller.streamTest(null, req);
        verify(sseSupport).start(any());
    }

    @Test
    void userCannotSubmitCustomConfigOrUseDisabledMissingOrHistoricalConfig() {
        AiGenericTaskReq req = request();
        req.setConfig(config);
        forbidden(() -> controller.test(null, req));
        forbidden(() -> controller.test(config.getId(), req));
        forbidden(() -> controller.streamTest(config.getId(), req));
        forbidden(() -> controller.loadModels(config));
        req.setConfig(null);
        forbidden(() -> controller.test(null, req));
        forbidden(() -> controller.loadModels(-1));
        config.setStatus(0);
        mapper.updateById(config);
        assertThat(controller.get(config.getId()).getResultData().getApiKey()).isNull();
        assertUnusable(req);
        config.setStatus(1);
        config.setModifyFrom(100);
        mapper.updateById(config);
        assertUnusable(req);
        verifyNoInteractions(aiService, sseSupport);
    }

    @Test
    void adminRetainsSecretsAndManagementAndCustomTesting() {
        login("admin");
        assertThat(controller.search(new AiConfigQueryVo()).getResultData())
                .singleElement().satisfies(row -> assertThat(row.getApiKey()).isEqualTo("test-secret"));
        assertThat(controller.get(config.getId()).getResultData().getApiKey()).isEqualTo("test-secret");
        config.setConfigName("updated");
        assertThat(controller.save(config).isSuccess()).isTrue();
        assertThat(controller.histories(new AiConfigQueryVo(), config.getId()).getResultData()).hasSize(1);
        AiConfig history = controller.histories(new AiConfigQueryVo(), config.getId()).getResultData().get(0);
        SimpleQueryVo query = new SimpleQueryVo();
        query.setQueryId(history.getId());
        assertThat(controller.loadHistoryDiff(query).isSuccess()).isTrue();
        assertThat(controller.recoverFromHistory(query).isSuccess()).isTrue();
        assertThat(mapper.selectById(config.getId()).getConfigName()).isEqualTo("test-config");
        controller.loadModels(config);
        AiGenericTaskReq req = request();
        req.setConfig(config);
        controller.test(null, req);
        verify(aiService).testAiConfig(config, req);
        controller.streamTest(null, req);
        verify(sseSupport).start(any());
        assertThat(controller.delete(config.getId()).isSuccess()).isTrue();
        assertThat(mapper.selectById(config.getId())).isNull();
    }

    @Test
    void adminCanCreateAndToggleDefaultAndStatusWithoutLosingKey() {
        login("admin");
        AiConfig added = new AiConfig();
        added.setConfigName("new-config");
        added.setBaseUrl("https://example.com/v1");
        added.setProvider("OPENAI");
        added.setDefaultModel("test-model");
        added.setApiKey("new-secret");
        added.setIsDefault(1);
        added.setStatus(1);
        assertThat(controller.save(added).isSuccess()).isTrue();
        AiConfig listed = controller.search(new AiConfigQueryVo()).getResultData().stream()
                .filter(row -> row.getId().equals(config.getId())).findFirst().orElseThrow();
        listed.setIsDefault(1);
        assertThat(controller.save(listed).isSuccess()).isTrue();
        assertThat(mapper.selectById(added.getId()).getIsDefault()).isZero();
        AiConfig updated = mapper.selectById(config.getId());
        assertThat(updated.getIsDefault()).isEqualTo(1);
        updated.setStatus(0);
        assertThat(controller.save(updated).isSuccess()).isTrue();
        assertThat(mapper.selectById(config.getId()).getStatus()).isZero();
        assertThat(mapper.selectById(config.getId()).getApiKey()).isEqualTo("test-secret");
    }

    private void assertUnusable(AiGenericTaskReq req) {
        forbidden(() -> controller.test(config.getId(), req));
        forbidden(() -> controller.streamTest(config.getId(), req));
        forbidden(() -> controller.loadModels(config.getId()));
    }

    private void forbidden(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(SimpleRuntimeException.class,
                error -> assertThat(error.getCode()).isEqualTo(SystemErrorConstants.CODE_403));
    }

    private AiGenericTaskReq request() {
        AiGenericTaskReq req = new AiGenericTaskReq();
        req.setUserMessage("hello");
        return req;
    }

    private void login(String name) {
        ApiUserVo user = new ApiUserVo();
        user.setUserName(name);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiDocConstants.API_USER_KEY, user);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
