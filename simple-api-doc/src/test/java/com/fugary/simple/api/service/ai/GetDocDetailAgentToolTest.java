package com.fugary.simple.api.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.exports.ApiDocViewGenerator;
import com.fugary.simple.api.exports.md.MdViewContext;
import com.fugary.simple.api.service.ai.agent.tool.AgentContext;
import com.fugary.simple.api.service.ai.agent.tool.impl.GetDocDetailAgentTool;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GetDocDetailAgentToolTest {
    private final ApiDocService docs = mock(ApiDocService.class);
    private final ApiProjectInfoDetailService schemas = mock(ApiProjectInfoDetailService.class);
    private final ApiProjectInfoService infos = mock(ApiProjectInfoService.class);
    private final ApiProjectService projects = mock(ApiProjectService.class);
    private final ApiProjectAccessService access = mock(ApiProjectAccessService.class);
    private final ApiDocViewGenerator generator = mock(ApiDocViewGenerator.class);
    private final AiConfigProperties properties = new AiConfigProperties();
    private final GetDocDetailAgentTool tool = new GetDocDetailAgentTool();
    private final AgentContext context = AgentContext.builder().defaultProjectId(10).build();
    private final ApiDoc doc = new ApiDoc();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tool, "aiConfigProperties", properties);
        ReflectionTestUtils.setField(tool, "apiDocService", docs);
        ReflectionTestUtils.setField(tool, "apiDocSchemaService", schemas);
        ReflectionTestUtils.setField(tool, "apiProjectInfoService", infos);
        ReflectionTestUtils.setField(tool, "apiProjectService", projects);
        ReflectionTestUtils.setField(tool, "apiProjectAccessService", access);
        ReflectionTestUtils.setField(tool, "apiDocViewGenerator", generator);
        doc.setId(101);
        doc.setProjectId(10);
        doc.setDocName("登录说明");
        doc.setDocType("md");
        doc.setStatus(1);
        doc.setDocContent("登录规则");
        when(docs.getById(101)).thenReturn(doc);
        when(access.canAccessDoc(eq(doc), any())).thenReturn(true);
    }

    @Test
    void defaultPageKeepsEvidenceBeyondTheOldCutoffAndReportsDocumentStatus() throws Exception {
        doc.setDocContent("说明".repeat(2000) + "\ntenantId 为必填字段");
        doc.setStatus(0); // 普通项目阅读与搜索一致，允许读取禁用文档并明确状态。
        doc.setDeprecated(true);
        JsonNode result = read(0);
        assertThat(result.path("content").asText()).isEqualTo(doc.getDocContent());
        assertThat(result.path("totalLength").asInt()).isEqualTo(doc.getDocContent().length());
        assertThat(result.path("hasMore").asBoolean()).isFalse();
        assertThat(result.has("nextOffset")).isFalse();
        assertThat(result.path("status").asInt()).isZero();
        assertThat(result.path("deprecated").asBoolean()).isTrue();
        assertThat(context.getInspectedDocIds()).containsExactly(101);
        verifyNoInteractions(schemas, generator);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successivePagesReconstructMarkdownAndApiDetailsWithoutLoss(boolean api) throws Exception {
        String content = "  开头\n" + "字段说明 ".repeat(5000) + "\nresponse.token 是返回值\n ";
        if (api) {
            prepareApi(content);
        } else {
            doc.setDocContent(content);
        }
        StringBuilder reconstructed = new StringBuilder();
        int offset = 0;
        int pages = 0;
        JsonNode result;
        do {
            result = read(offset);
            assertThat(result.path("offset").asInt()).isEqualTo(offset);
            assertThat(result.path("totalLength").asInt()).isEqualTo(content.length());
            reconstructed.append(result.path("content").asText());
            if (result.path("hasMore").asBoolean()) {
                int next = result.path("nextOffset").asInt();
                assertThat(next).isGreaterThan(offset);
                offset = next;
            }
            assertThat(++pages).isLessThan(5);
        } while (result.path("hasMore").asBoolean());
        assertThat(pages).isEqualTo(3);
        assertThat(reconstructed.toString()).isEqualTo(content);
        assertThat(result.path("content").asText()).contains("response.token 是返回值");
        assertThat(context.getInspectedDocIds()).containsExactly(101);
        verify(access, times(pages)).canAccessDoc(eq(doc), any());
    }

    @Test
    void configuredPageLengthPreservesUnicodeAtTheBoundary() throws Exception {
        properties.setDocDetailPageChars(5);
        doc.setDocContent("abcd\uD83D\uDE00tail");
        JsonNode first = read(0);
        assertThat(first.path("content").asText()).isEqualTo("abcd\uD83D\uDE00");
        JsonNode last = read(first.path("nextOffset").asInt());
        assertThat(first.path("content").asText() + last.path("content").asText()).isEqualTo(doc.getDocContent());
        assertThat(last.path("hasMore").asBoolean()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{}", "{\"docId\":0}", "{\"docId\":1.5}",
            "{\"docId\":\"101\"}", "{\"docId\":2147483648}", "{\"docId\":101,\"offset\":-1}",
            "{\"docId\":101,\"offset\":1.5}", "{\"docId\":101,\"offset\":\"0\"}",
            "{\"docId\":101,\"offset\":2147483648}"})
    void rejectsInvalidArgumentsBeforeReadingDocs(String arguments) {
        assertThat(tool.execute(arguments, context)).doesNotStartWith("{");
        assertThat(context.getInspectedDocIds()).isEmpty();
        verifyNoInteractions(docs, schemas, generator);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5, Integer.MAX_VALUE})
    void rejectsOffsetsBeyondContentWithoutRecordingEvidence(int offset) {
        doc.setDocContent("abcd");
        assertThat(tool.execute("{\"docId\":101,\"offset\":" + offset + "}", context)).contains("offset");
        assertThat(context.getInspectedDocIds()).isEmpty();
    }

    @Test
    void rejectsOffsetInsideUnicodeCharacter() {
        doc.setDocContent("a\uD83D\uDE00b");
        assertThat(tool.execute("{\"docId\":101,\"offset\":2}", context)).contains("有效字符边界");
        assertThat(context.getInspectedDocIds()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"other_project", "history", "schema", "unknown_type", "missing"})
    void rejectsDocumentsOutsideTheCurrentScopeBeforeGeneratingDetails(String scenario) {
        if ("other_project".equals(scenario)) doc.setProjectId(20);
        if ("history".equals(scenario)) doc.setModifyFrom(42);
        if ("schema".equals(scenario)) doc.setDocType("schema");
        if ("unknown_type".equals(scenario)) doc.setDocType(null);
        if ("missing".equals(scenario)) when(docs.getById(101)).thenReturn(null);
        assertThat(tool.execute("{\"docId\":101}", context)).doesNotContain("登录规则");
        assertThat(context.getInspectedDocIds()).isEmpty();
        verifyNoInteractions(schemas, generator);
    }

    @Test
    void requiresContextAndProjectPermission() {
        assertThat(tool.execute("{\"docId\":101}", null)).contains("访问上下文");
        verifyNoInteractions(docs);
        when(access.canAccessDoc(eq(doc), any())).thenReturn(false);
        assertThat(tool.execute("{\"docId\":101}", context)).contains("无权访问");
        assertThat(context.getInspectedDocIds()).isEmpty();
        verifyNoInteractions(schemas, generator);
    }

    @Test
    void rechecksPermissionWhenContinuing() throws Exception {
        properties.setDocDetailPageChars(2);
        JsonNode first = read(0);
        when(access.canAccessDoc(eq(doc), any())).thenReturn(false);
        assertThat(tool.execute("{\"docId\":101,\"offset\":" + first.path("nextOffset").asInt() + "}", context))
                .contains("无权访问").doesNotContain("规则");
    }

    @ParameterizedTest
    @ValueSource(strings = {"throw", "null", "empty"})
    void failedOrEmptyGenerationDoesNotBecomeReadEvidence(String scenario) {
        prepareApi("initial");
        if ("throw".equals(scenario)) when(generator.generate(any())).thenThrow(new IllegalStateException("render failed"));
        if ("null".equals(scenario)) when(generator.generate(any())).thenReturn(null);
        if ("empty".equals(scenario)) when(generator.generate(any())).thenReturn(" \n");
        assertThat(tool.execute("{\"docId\":101}", context)).doesNotStartWith("{");
        assertThat(context.getInspectedDocIds()).isEmpty();
    }

    @Test
    void emptyMarkdownDoesNotBecomeReadEvidence() {
        doc.setDocContent(null);
        assertThat(tool.execute("{\"docId\":101}", context)).contains("暂无可读取的正文");
        assertThat(context.getInspectedDocIds()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not_shared", "disabled_doc", "disabled_project", "missing_project", "history"})
    void shareCannotReadMoreThanSearchWouldAllow(String scenario) {
        ApiProjectShare share = prepareShare();
        if ("not_shared".equals(scenario)) share.setShareDocs("[102]");
        if ("disabled_doc".equals(scenario)) doc.setStatus(0);
        if ("disabled_project".equals(scenario)) {
            ApiProject project = new ApiProject();
            project.setStatus(0);
            when(projects.getById(10)).thenReturn(project);
        }
        if ("missing_project".equals(scenario)) when(projects.getById(10)).thenReturn(null);
        if ("history".equals(scenario)) doc.setModifyFrom(42);
        assertThat(tool.execute("{\"docId\":101}", context)).doesNotContain("登录规则");
        assertThat(context.getInspectedDocIds()).isEmpty();
        verifyNoInteractions(schemas, generator);
    }

    @Test
    void enabledShareCanReadExplicitAndUnrestrictedDocuments() throws Exception {
        ApiProjectShare share = prepareShare();
        assertThat(read(0).path("content").asText()).isEqualTo("登录规则");
        share.setShareDocs("[]");
        assertThat(read(0).path("content").asText()).isEqualTo("登录规则");
        verifyNoInteractions(access);
    }

    @Test
    void apiGenerationRetainsProjectAndReferencedSchemaContext() throws Exception {
        ApiDocDetailVo detail = prepareApi("request.customerId 必填\nresponse.token");
        ApiProjectInfo info = new ApiProjectInfo();
        ApiProjectInfoDetailVo infoDetail = new ApiProjectInfoDetailVo();
        ApiProject project = new ApiProject();
        project.setProjectCode("demo");
        when(infos.getById(1)).thenReturn(info);
        when(schemas.parseInfoDetailVo(info, detail)).thenReturn(infoDetail);
        when(projects.getById(10)).thenReturn(project);
        JsonNode result = read(0);
        ArgumentCaptor<MdViewContext> rendered = ArgumentCaptor.forClass(MdViewContext.class);
        verify(generator).generate(rendered.capture());
        assertThat(rendered.getValue().getApiDocDetail().getProjectInfoDetail()).isSameAs(infoDetail);
        assertThat(infoDetail.getProjectCode()).isEqualTo("demo");
        assertThat(result.path("method").asText()).isEqualTo("POST");
        assertThat(result.path("url").asText()).isEqualTo("/login");
        assertThat(result.path("content").asText()).contains("request.customerId 必填", "response.token");
    }

    private ApiDocDetailVo prepareApi(String content) {
        doc.setDocType("api");
        doc.setMethod("POST");
        doc.setUrl("/login");
        ApiDocDetailVo detail = new ApiDocDetailVo();
        detail.setInfoId(1);
        detail.setProjectId(10);
        when(schemas.loadDetailVo(doc)).thenReturn(detail);
        when(generator.generate(any())).thenReturn(content);
        return detail;
    }

    private ApiProjectShare prepareShare() {
        ApiProjectShare share = new ApiProjectShare();
        share.setProjectId(10);
        share.setShareDocs("[101]");
        context.setShare(share);
        ApiProject project = new ApiProject();
        project.setStatus(1);
        when(projects.getById(10)).thenReturn(project);
        return share;
    }

    private JsonNode read(int offset) throws Exception {
        return JsonUtils.getMapper().readTree(tool.execute(
                "{\"docId\":101,\"offset\":" + offset + "}", context));
    }
}
