package com.fugary.simple.api.service.ai;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.service.impl.apidoc.ApiProjectShareServiceImpl;
import com.fugary.simple.api.service.impl.ai.AiConfigServiceImpl;
import com.fugary.simple.api.service.impl.ai.AiServiceImpl;
import com.fugary.simple.api.web.controllers.AiController;
import com.fugary.simple.api.web.vo.AiGenericTaskReq;
import com.fugary.simple.api.web.vo.AiStatusVo;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShareAiTest {
    private final ApiProjectShareServiceImpl shares = spy(new ApiProjectShareServiceImpl());
    private final ApiProjectService projects = mock(ApiProjectService.class);
    private final ApiDocService docs = mock(ApiDocService.class);
    private final AiConfigServiceImpl configs = spy(new AiConfigServiceImpl());
    private final AiService ai = mock(AiService.class);
    private final AiController controller = new AiController();
    private final ApiProjectShare share = new ApiProjectShare();
    private final AiConfig config = new AiConfig();
    private final ApiDoc doc = new ApiDoc();
    private final ApiProject project = new ApiProject();

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiDocConstants.AUTHORIZED_SHARED_KEY, "public-docs");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        share.setShareId("public-docs");
        share.setProjectId(10);
        share.setShareDocs("[1]");
        share.setAiSampleEnabled(true);
        share.setAiSearchEnabled(false);
        project.setId(10);
        project.setStatus(1);
        config.setId(7);
        config.setStatus(1);
        config.setApiKey("test-key");
        config.setDefaultModel("default-model");
        doc.setId(1);
        doc.setProjectId(10);
        doc.setDocType("api");
        doc.setStatus(1);
        doReturn(share).when(shares).loadByShareId("public-docs");
        when(projects.getById(10)).thenReturn(project);
        when(docs.getById(1)).thenReturn(doc);
        doReturn(config).when(configs).getDefaultAiConfig();
        ReflectionTestUtils.setField(controller, "shareService", shares);
        ReflectionTestUtils.setField(shares, "projectService", projects);
        ReflectionTestUtils.setField(shares, "docService", docs);
        ReflectionTestUtils.setField(shares, "aiConfigService", configs);
        ReflectionTestUtils.setField(controller, "aiConfigService", configs);
        ReflectionTestUtils.setField(controller, "aiService", ai);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void statusOnlyExposesDefaultModelAvailability() {
        AiStatusVo status = controller.getAiStatus().getResultData();
        assertThat(status.isEnabled()).isTrue();
        assertThat(status.getConfigs()).isEmpty();
        assertThat(status.getDefaultConfigId()).isNull();
        config.setDefaultModel(" ");
        assertThat(controller.getAiStatus().getResultData().isEnabled()).isFalse();
        assertThatThrownBy(() -> shares.requireAiConfig(share, false)).isInstanceOf(SimpleRuntimeException.class);
    }

    @Test
    void legacyEnvironmentSwitchDoesNotDisableDatabaseConfiguration() {
        AiConfigProperties properties = new AiConfigProperties();
        properties.setEnabled(false);
        properties.setApiKey("environment-key");
        ReflectionTestUtils.setField(configs, "aiConfigProperties", properties);
        AiServiceImpl service = new AiServiceImpl();
        ReflectionTestUtils.setField(service, "aiConfigProperties", properties);
        ReflectionTestUtils.setField(service, "aiConfigService", configs);

        configs.autoMigrateFromYaml(); // 旧开关关闭时不访问 mapper、不导入环境配置。
        assertThat(service.isEnabled()).isTrue();
        assertThat(controller.getAiStatus().getResultData().isEnabled()).isTrue();
        assertThat(shares.requireAiConfig(share, false)).isSameAs(config);

        config.setApiKey(" ");
        assertThat(service.isEnabled()).isFalse();
        assertThat(controller.getAiStatus().getResultData().isEnabled()).isFalse();
        doReturn(null).when(configs).getDefaultAiConfig();
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void capabilitiesAreIndependentAndOldSharesDefaultToDenied() {
        assertThat(shares.requireAiConfig(share, false)).isSameAs(config);
        assertThatThrownBy(() -> shares.requireAiConfig(share, true)).isInstanceOf(SimpleRuntimeException.class);
        share.setAiSampleEnabled(null);
        assertThatThrownBy(() -> shares.requireAiConfig(share, false)).isInstanceOf(SimpleRuntimeException.class);
    }

    @Test
    void sampleUsesDefaultConfigAndChecksDocumentBeforeCallingAi() {
        AiGenericTaskReq req = new AiGenericTaskReq();
        req.setSchemaContent("{}");
        req.setDocId("1");
        req.setProjectId("999");
        req.setConfigId(999);
        req.setModel("visitor-model");
        req.setConfig(new AiConfig());
        when(ai.generateSample(req)).thenReturn("{}");
        assertThat(controller.generateSample(req).isSuccess()).isTrue();
        assertThat(req.getConfigId()).isEqualTo(7);
        assertThat(req.getProjectId()).isEqualTo("10");
        assertThat(req.getModel()).isNull();
        assertThat(req.getConfig()).isNull();
        clearInvocations(ai);
        req.setDocId("2");
        assertThatThrownBy(() -> controller.generateSample(req)).isInstanceOf(SimpleRuntimeException.class);
        verifyNoInteractions(ai);
    }

    @Test
    void deniesExpiredSharesDisabledProjectsAndEditingEndpoints() {
        share.setExpireDate(new Date(0));
        assertThatThrownBy(shares::loadCurrentShare).isInstanceOf(SimpleRuntimeException.class);
        share.setExpireDate(null);
        project.setStatus(0);
        assertThatThrownBy(() -> shares.requireDoc("1", share)).isInstanceOf(SimpleRuntimeException.class);
        assertThatThrownBy(() -> controller.generateModel(new AiGenericTaskReq())).isInstanceOf(SimpleRuntimeException.class);
        assertThatThrownBy(() -> controller.generateDescriptions(new AiGenericTaskReq())).isInstanceOf(SimpleRuntimeException.class);
        verifyNoInteractions(ai);
    }

    @Test
    void deniesUnsharedDisabledHistoricalAndOtherProjectDocuments() {
        assertThat(shares.canReadDoc(doc, share)).isTrue();
        doc.setId(2);
        assertThat(shares.canReadDoc(doc, share)).isFalse();
        doc.setId(1);
        doc.setStatus(0);
        assertThat(shares.canReadDoc(doc, share)).isFalse();
        doc.setStatus(1);
        doc.setModifyFrom(9);
        assertThat(shares.canReadDoc(doc, share)).isFalse();
        doc.setModifyFrom(null);
        doc.setProjectId(20);
        assertThat(shares.canReadDoc(doc, share)).isFalse();
    }
}
