package com.fugary.simple.api.service.impl.ai;

import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.service.ai.AiConfigService;
import com.fugary.simple.api.service.ai.agent.AiProjectDocOverviewService;
import com.fugary.simple.api.service.ai.agent.tool.AgentContext;
import com.fugary.simple.api.service.ai.agent.tool.AgentTool;
import com.fugary.simple.api.service.ai.agent.tool.AiToolCall;
import com.fugary.simple.api.service.ai.agent.tool.AiToolDefinition;
import com.fugary.simple.api.service.ai.provider.AiChatProvider;
import com.fugary.simple.api.service.ai.provider.AiChatResponse;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.service.apidoc.ApiProjectAccessService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.web.vo.ai.AiAgentChatReqVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiAgentServiceImplTest {

    private final AiConfigService aiConfigService = mock(AiConfigService.class);
    private final AiChatProvider chatProvider = mock(AiChatProvider.class);
    private final AgentTool searchTool = mock(AgentTool.class);
    private final AgentTool getDocTool = mock(AgentTool.class);
    private final ApiDocService apiDocService = mock(ApiDocService.class);
    private final ApiProjectService apiProjectService = mock(ApiProjectService.class);
    private final ApiFolderService apiFolderService = mock(ApiFolderService.class);
    private final ApiProjectAccessService apiProjectAccessService = mock(ApiProjectAccessService.class);

    private final AiProjectDocOverviewService projectDocOverviewService = mock(AiProjectDocOverviewService.class);
    private final AiAgentServiceImpl service = new AiAgentServiceImpl();

    @BeforeEach
    void setUp() {
        when(chatProvider.getProviderCode()).thenReturn("OPENAI");
        when(searchTool.getName()).thenReturn("search_docs");
        when(searchTool.getDescription()).thenReturn("Search docs");
        when(searchTool.getParameters()).thenReturn(Map.of("type", "object"));
        when(searchTool.toDefinition()).thenReturn(AiToolDefinition.builder().name("search_docs").build());

        when(getDocTool.getName()).thenReturn("get_doc");
        when(getDocTool.getDescription()).thenReturn("Get doc");
        when(getDocTool.getParameters()).thenReturn(Map.of("type", "object"));
        when(getDocTool.toDefinition()).thenReturn(AiToolDefinition.builder().name("get_doc").build());

        ReflectionTestUtils.setField(service, "projectDocOverviewService", projectDocOverviewService);
        ReflectionTestUtils.setField(service, "aiConfigService", aiConfigService);
        ReflectionTestUtils.setField(service, "chatProviders", List.of(chatProvider));
        ReflectionTestUtils.setField(service, "agentTools", List.of(searchTool, getDocTool));
        ReflectionTestUtils.setField(service, "apiDocService", apiDocService);
        ReflectionTestUtils.setField(service, "apiProjectService", apiProjectService);
        ReflectionTestUtils.setField(service, "apiFolderService", apiFolderService);
        ReflectionTestUtils.setField(service, "apiProjectAccessService", apiProjectAccessService);
    }

    @Test
    void testStreamChatSuccessfulLoopAndGrounding() throws IOException {
        AiConfig config = new AiConfig();
        config.setId(1);
        config.setStatus(1);
        config.setProvider("OPENAI");
        config.setDefaultModel("gpt-4o-mini");
        when(aiConfigService.getDefaultAiConfig()).thenReturn(config);

        // 第 1 轮返回 tool call: search_docs
        AiChatResponse respRound1 = new AiChatResponse();
        respRound1.setToolCalls(List.of(new AiToolCall("call_1", "search_docs", "{\"keywords\":\"退款\"}")));

        // 第 2 轮返回最终文本，引用了有效的 doc://101 和凭空捏造的 doc://999
        AiChatResponse respRound2 = new AiChatResponse();
        respRound2.setContent("退款请调用 [申请退款](doc://101)，不要调用 [编造接口](doc://999)。");

        when(chatProvider.chatWithTools(any(), any(), any(), any()))
                .thenReturn(respRound1)
                .thenReturn(respRound2);

        when(searchTool.execute(any(), any())).thenReturn("[{\"docId\":101,\"docName\":\"申请退款\"}]");

        ApiDoc validDoc = new ApiDoc();
        validDoc.setId(101);
        validDoc.setDocName("申请退款");
        validDoc.setUrl("/api/refund");
        validDoc.setMethod("POST");
        validDoc.setProjectId(10);
        validDoc.setFolderId(1);

        when(apiDocService.getById(101)).thenReturn(validDoc);
        when(apiDocService.getById(999)).thenReturn(null); // 捏造的 ID
        when(apiProjectAccessService.canAccessDoc(eq(validDoc), any())).thenReturn(true);
        when(apiDocService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(validDoc));

        ApiProject project = new ApiProject();
        project.setId(10);
        project.setProjectName("电商项目");
        project.setProjectCode("mall");
        when(apiProjectService.getById(10)).thenReturn(project);
        when(apiProjectAccessService.canAccessProject(eq(project), any())).thenReturn(true);
        when(apiProjectService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(project));
        when(apiFolderService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
        when(apiFolderService.calcFolderNameMap(any())).thenReturn(Map.of(1, "订单模块"));

        SseEmitter emitter = mock(SseEmitter.class);
        AiAgentChatReqVo req = new AiAgentChatReqVo();
        req.setProjectId(10);
        req.setQuery("如何发起退款？");
        req.setModel(" custom-model ");

        service.streamChat(req, emitter);

        // 验证工具调用了 1 次
        verify(searchTool, times(1)).execute(eq("{\"keywords\":\"退款\"}"), any());
        verify(chatProvider, times(2)).chatWithTools(argThat(actual -> actual != config
                && "custom-model".equals(actual.getDefaultModel())), any(), any(), any());
        assertThat(config.getDefaultModel()).isEqualTo("gpt-4o-mini");

        // 验证 SSE 收到 tool_start, tool_end, related_docs, delta, finish
        verify(emitter, atLeast(4)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, times(1)).complete();
    }

    @Test
    void testSanitizeMarkdownContent() {
        // 不根据标题内容猜测代码块边界，仅在末尾补齐。
        String unclosedHttp = "```http\n" +
                "POST /user/register\n" +
                "Content-Type: application/json\n\n" +
                "{\n" +
                "  \"username\": \"test\"\n" +
                "}\n\n" +
                "## 2. 参数说明\n" +
                "- **username**: 用户名";

        String sanitized = service.sanitizeMarkdownContent(unclosedHttp);
        assertThat(sanitized).isEqualTo(unclosedHttp + "\n```");

        // 测试末尾未闭合的代码块自动补齐
        String danglingJson = "### 示例\n```json\n{\"code\":200}";
        String sanitizedJson = service.sanitizeMarkdownContent(danglingJson);
        assertThat(sanitizedJson).endsWith("\n```");

        // 测试正常闭合的代码块不被篡改
        String normal = "```http\nGET /users\n```\n## 2. 说明\n正常内容";
        assertThat(service.sanitizeMarkdownContent(normal)).isEqualTo(normal);

        // 代码注释和 Markdown 示例中的标题均不得触发内容改写。
        String comments = "```python\n# Parameters\n# 中文注释\nprint('ok')\n```";
        assertThat(service.sanitizeMarkdownContent(comments)).isEqualTo(comments);
        String nestedFences = "````markdown\n# 示例\n```json\n{}\n```\n````";
        assertThat(service.sanitizeMarkdownContent(nestedFences)).isEqualTo(nestedFences);
        assertThat(service.sanitizeMarkdownContent("~~~text\n## 标题")).isEqualTo("~~~text\n## 标题\n~~~");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyAnswerRetriesWithoutToolsAndFallsBackToAccessibleDocs(boolean inspected) throws IOException {
        AiAgentChatReqVo req = prepareFallbackChat();
        AgentTool tool = inspected ? getDocTool : searchTool;
        AiChatResponse toolResponse = new AiChatResponse();
        toolResponse.setToolCalls(List.of(new AiToolCall("call_1", tool.getName(), "{}")));
        when(tool.execute(any(), any())).thenAnswer(call -> {
            AgentContext context = call.getArgument(1);
            (inspected ? context.getInspectedDocIds() : context.getDiscoveredDocIds()).addAll(List.of(101, 999));
            return "已找到候选文档";
        });
        when(chatProvider.chatWithTools(any(), any(), any(), eq("auto")))
                .thenReturn(toolResponse, new AiChatResponse());
        when(chatProvider.chatWithTools(any(), any(), any(), eq("none"))).thenAnswer(call -> {
            List<Map<String, Object>> messages = call.getArgument(1);
            assertThat(messages.get(messages.size() - 1).get("role")).isEqualTo("tool");
            assertThat((List<?>) call.getArgument(2)).hasSize(2);
            return new AiChatResponse();
        });

        SseEmitter emitter = mock(SseEmitter.class);
        service.streamChat(req, emitter);

        verify(chatProvider, times(2)).chatWithTools(any(), any(), any(), eq("auto"));
        verify(chatProvider).chatWithTools(any(), any(), any(), eq("none"));
        String events = emittedEvents(emitter);
        assertThat(events).contains("[POST /login](doc://101)", "候选文档", "SUCCESS")
                .doesNotContain("doc://999", "未能检索到", "event:error");
        verify(emitter).complete();
    }

    @Test
    void failedSummaryStillReturnsDiscoveredDocs() throws IOException {
        AiAgentChatReqVo req = prepareFallbackChat();
        AiChatResponse toolResponse = new AiChatResponse();
        toolResponse.setToolCalls(List.of(new AiToolCall("call_1", "search_docs", "{}")));
        when(searchTool.execute(any(), any())).thenAnswer(call -> {
            AgentContext context = call.getArgument(1);
            context.getDiscoveredDocIds().add(101);
            return "已找到候选文档";
        });
        when(chatProvider.chatWithTools(any(), any(), any(), eq("auto")))
                .thenReturn(toolResponse, new AiChatResponse());
        when(chatProvider.chatWithTools(any(), any(), any(), eq("none")))
                .thenThrow(new IllegalStateException("summary unavailable"));
        SseEmitter emitter = mock(SseEmitter.class);

        service.streamChat(req, emitter);

        assertThat(emittedEvents(emitter)).contains("[POST /login](doc://101)", "SUCCESS")
                .doesNotContain("event:error");
    }

    @Test
    void overviewCanAnswerWithoutSearchingAndOnlyCitedDocumentsBecomeRelated() throws IOException {
        AiAgentChatReqVo req = prepareFallbackChat();
        req.setIncludeProjectOverview(true);
        when(projectDocOverviewService.build(any())).thenReturn(new AiProjectDocOverviewService.Overview(
                "完整目录 OVERVIEW_MARKER", 2, "已附加全部 2 份文档"));
        when(chatProvider.chatWithTools(any(), any(), any(), eq("auto"))).thenAnswer(call -> {
            List<Map<String, Object>> messages = call.getArgument(1);
            assertThat(messages.get(1).get("content").toString())
                    .contains("OVERVIEW_MARKER", "登录接口有哪些");
            assertThat(messages.get(0).get("content").toString()).doesNotContain("OVERVIEW_MARKER");
            AiChatResponse response = new AiChatResponse();
            response.setContent("[POST /login](doc://101) [越权文档](doc://999)");
            return response;
        });
        SseEmitter emitter = mock(SseEmitter.class);
        service.streamChat(req, emitter);
        verify(searchTool, never()).execute(any(), any());
        verify(getDocTool, never()).execute(any(), any());
        verify(chatProvider).chatWithTools(any(), any(), any(), eq("auto"));
        verify(apiDocService, never()).getById(102);
        assertThat(emittedEvents(emitter)).contains("全部 2 份", "doc://101", "SUCCESS")
                .doesNotContain("doc://999", "doc://102", "event:error");
    }

    @Test
    void defaultModeDoesNotReadOverview() throws IOException {
        AiAgentChatReqVo req = prepareFallbackChat();
        AiChatResponse response = new AiChatResponse();
        response.setContent("请补充查询条件");
        when(chatProvider.chatWithTools(any(), any(), any(), any())).thenReturn(response);
        service.streamChat(req, mock(SseEmitter.class));
        verifyNoInteractions(projectDocOverviewService);
    }

    @Test
    void emptyOverviewAnswerRetriesButDoesNotRecommendTheEntireDirectory() throws IOException {
        AiAgentChatReqVo req = prepareFallbackChat();
        req.setIncludeProjectOverview(true);
        when(projectDocOverviewService.build(any())).thenReturn(new AiProjectDocOverviewService.Overview(
                "完整目录", 2, "已附加全部 2 份文档"));
        when(chatProvider.chatWithTools(any(), any(), any(), any())).thenReturn(new AiChatResponse());
        SseEmitter emitter = mock(SseEmitter.class);
        service.streamChat(req, emitter);
        verify(chatProvider).chatWithTools(any(), any(), any(), eq("none"));
        assertThat(emittedEvents(emitter)).contains("暂未生成有效回答", "SUCCESS")
                .doesNotContain("doc://101", "doc://102", "未能检索到", "event:error");
    }

    @Test
    void unavailableOverviewKeepsSearchAvailableAndReportsTheFallback() throws IOException {
        AiAgentChatReqVo req = prepareFallbackChat();
        req.setIncludeProjectOverview(true);
        when(projectDocOverviewService.build(any())).thenReturn(new AiProjectDocOverviewService.Overview(
                "", 0, "概览超限，本次未附加，改用按需搜索"));
        AiChatResponse toolResponse = new AiChatResponse();
        toolResponse.setToolCalls(List.of(new AiToolCall("search", "search_docs", "{}")));
        AiChatResponse answer = new AiChatResponse();
        answer.setContent("[POST /login](doc://101)");
        when(searchTool.execute(any(), any())).thenReturn("docId=101");
        when(chatProvider.chatWithTools(any(), any(), any(), eq("auto")))
                .thenReturn(toolResponse, answer);
        SseEmitter emitter = mock(SseEmitter.class);
        service.streamChat(req, emitter);
        verify(searchTool).execute(eq("{}"), any());
        assertThat(emittedEvents(emitter)).contains("本次未附加", "doc://101", "SUCCESS")
                .doesNotContain("event:error");
    }

    private AiAgentChatReqVo prepareFallbackChat() {
        AiConfig config = new AiConfig();
        config.setStatus(1);
        config.setProvider("OPENAI");
        when(aiConfigService.getDefaultAiConfig()).thenReturn(config);
        ApiProject project = new ApiProject();
        project.setId(10);
        when(apiProjectService.getById(10)).thenReturn(project);
        when(apiProjectAccessService.canAccessProject(eq(project), any())).thenReturn(true);
        when(apiProjectService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(project));
        ApiDoc doc = new ApiDoc();
        doc.setId(101);
        doc.setProjectId(10);
        doc.setDocType("api");
        doc.setMethod("POST");
        doc.setUrl("/login");
        when(apiDocService.getById(101)).thenReturn(doc);
        when(apiProjectAccessService.canAccessDoc(eq(doc), any())).thenReturn(true);
        when(apiDocService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(doc));
        ApiDoc outsideProject = new ApiDoc();
        outsideProject.setId(999);
        outsideProject.setProjectId(20);
        when(apiDocService.getById(999)).thenReturn(outsideProject);
        AiAgentChatReqVo req = new AiAgentChatReqVo();
        req.setProjectId(10);
        req.setQuery("登录接口有哪些？");
        return req;
    }

    private String emittedEvents(SseEmitter emitter) throws IOException {
        ArgumentCaptor<SseEmitter.SseEventBuilder> events = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter, atLeastOnce()).send(events.capture());
        return events.getAllValues().stream().flatMap(event -> event.build().stream())
                .map(part -> String.valueOf(part.getData())).collect(Collectors.joining("\n"));
    }

    @Test
    void testToolWithNamespacePrefixAndAssistantMessagePreserved() throws IOException {
        AiConfig config = new AiConfig();
        config.setId(1);
        config.setStatus(1);
        config.setProvider("OPENAI");
        when(aiConfigService.getDefaultAiConfig()).thenReturn(config);

        // 模拟 Gemini 返回带 namespace 的工具名 default_api:search_docs 以及原生 assistantMessage
        AiChatResponse respRound1 = new AiChatResponse();
        respRound1.setToolCalls(List.of(new AiToolCall("call_gemini_1", "default_api:search_docs", "{\"keywords\":\"用户\"}")));
        Map<String, Object> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("thought_signature", "crypto_sig_abc123");
        respRound1.setAssistantMessage(assistantMsg);

        AiChatResponse respRound2 = new AiChatResponse();
        respRound2.setContent("已完成查询");

        when(chatProvider.chatWithTools(any(), any(), any(), any()))
                .thenReturn(respRound1)
                .thenReturn(respRound2);

        when(searchTool.execute(any(), any())).thenReturn("[]");

        ApiProject project = new ApiProject();
        project.setId(10);
        when(apiProjectService.getById(10)).thenReturn(project);
        when(apiProjectAccessService.canAccessProject(eq(project), any())).thenReturn(true);

        SseEmitter emitter = mock(SseEmitter.class);
        AiAgentChatReqVo req = new AiAgentChatReqVo();
        req.setProjectId(10);
        req.setQuery("查询用户接口");

        service.streamChat(req, emitter);

        // 验证 default_api:search_docs 成功剥离前缀并路由到 searchTool 执行
        verify(searchTool, times(1)).execute(eq("{\"keywords\":\"用户\"}"), any());
        verify(emitter, times(1)).complete();
    }
}
