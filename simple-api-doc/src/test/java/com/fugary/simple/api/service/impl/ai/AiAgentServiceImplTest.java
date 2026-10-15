package com.fugary.simple.api.service.impl.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.service.ai.AiConfigService;
import com.fugary.simple.api.service.ai.agent.AiProjectDocOverviewService;
import com.fugary.simple.api.service.ai.agent.tool.AgentContext;
import com.fugary.simple.api.service.ai.agent.tool.impl.GetDocDetailAgentTool;
import com.fugary.simple.api.service.ai.agent.tool.AgentTool;
import com.fugary.simple.api.service.ai.agent.tool.AiToolCall;
import com.fugary.simple.api.service.ai.agent.tool.AiToolDefinition;
import com.fugary.simple.api.service.ai.provider.AiChatProvider;
import com.fugary.simple.api.service.ai.provider.AiChatResponse;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.service.apidoc.ApiProjectAccessService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.web.vo.ai.AiAgentChatReqVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final AiConfigProperties aiConfigProperties = new AiConfigProperties();

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
        ReflectionTestUtils.setField(service, "aiConfigProperties", aiConfigProperties);
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

        when(searchTool.execute(any(), any())).thenReturn(
                "{\"total\":1,\"page\":1,\"hasMore\":false,\"docs\":[{\"docId\":101,\"docName\":\"申请退款\"}]}");

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
        assertThat(emittedEvents(emitter)).contains("申请退款").doesNotContain("本次返回");
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
            assertThat(messages.get(messages.size() - 1).get("role")).isEqualTo("user");
            assertThat(messages.get(messages.size() - 1).get("content").toString()).contains("不再调用工具", "证据不足");
            assertThat((List<?>) call.getArgument(2)).hasSize(2);
            return new AiChatResponse();
        });

        SseEmitter emitter = mock(SseEmitter.class);
        service.streamChat(req, emitter);

        verify(chatProvider, times(2)).chatWithTools(any(), any(), any(), eq("auto"));
        verify(chatProvider).chatWithTools(any(), any(), any(), eq("none"));
        String events = emittedEvents(emitter);
        assertThat(events).contains("[POST /login](doc://101)", "候选文档", "INCOMPLETE")
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

        assertThat(emittedEvents(emitter)).contains("[POST /login](doc://101)", "INCOMPLETE")
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
        assertThat(emittedEvents(emitter)).contains("暂未生成有效回答", "INCOMPLETE")
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

    @Test
    void continuesActualDocumentToolWithoutTriggeringDuplicateCallProtection() throws IOException {
        AiAgentChatReqVo req = prepareFallbackChat();
        req.setQuery("token 是请求字段还是返回字段？");
        ApiDoc doc = apiDocService.getById(101);
        doc.setDocType("md");
        doc.setDocName("登录说明");
        doc.setDocContent("前文".repeat(6000) + "\nresponse.token 是返回字段");
        GetDocDetailAgentTool detailTool = spy(new GetDocDetailAgentTool());
        ReflectionTestUtils.setField(detailTool, "aiConfigProperties", new AiConfigProperties());
        ReflectionTestUtils.setField(detailTool, "apiDocService", apiDocService);
        ReflectionTestUtils.setField(detailTool, "apiProjectAccessService", apiProjectAccessService);
        ReflectionTestUtils.setField(service, "agentTools", List.of(searchTool, detailTool));

        AtomicInteger round = new AtomicInteger();
        when(chatProvider.chatWithTools(any(), any(), any(), eq("auto"))).thenAnswer(call -> {
            int current = round.incrementAndGet();
            List<Map<String, Object>> messages = call.getArgument(1);
            AiChatResponse response = new AiChatResponse();
            if (current == 1) {
                response.setToolCalls(List.of(new AiToolCall("read_start", "get_doc", "{\"docId\":101}")));
            } else {
                JsonNode result = JsonUtils.getMapper()
                        .readTree(messages.get(messages.size() - 1).get("content").toString());
                if (current == 2) {
                    assertThat(result.path("hasMore").asBoolean()).isTrue();
                    assertThat(result.path("content").asText()).doesNotContain("response.token");
                    response.setToolCalls(List.of(new AiToolCall("read_next", "get_doc",
                            "{\"docId\":101,\"offset\":" + result.path("nextOffset").asInt() + "}")));
                } else {
                    assertThat(current).isEqualTo(3);
                    assertThat(result.path("hasMore").asBoolean()).isFalse();
                    assertThat(result.path("content").asText()).contains("response.token 是返回字段");
                    response.setContent("token 是返回字段，依据 [登录说明](doc://101)。");
                }
            }
            return response;
        });

        SseEmitter emitter = mock(SseEmitter.class);
        service.streamChat(req, emitter);

        ArgumentCaptor<AgentContext> contexts = ArgumentCaptor.forClass(AgentContext.class);
        verify(detailTool, times(2)).execute(anyString(), contexts.capture());
        assertThat(contexts.getValue().getInspectedDocIds()).containsExactly(101);
        verify(chatProvider, times(3)).chatWithTools(any(), any(), any(), eq("auto"));
        assertThat(emittedEvents(emitter)).contains("token 是返回字段", "doc://101", "SUCCESS",
                        "登录说明")
                .doesNotContain("event:error", "不要重复查询", "首次读取 ·", "（续读）", "已到文末");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deduplicatesDocumentPagesWithinAndAcrossRounds(boolean sameRound) throws IOException {
        AiAgentChatReqVo req = prepareFallbackChat();
        AiToolCall first = new AiToolCall("first", "get_doc", "{\"docId\":101}");
        List<AiToolCall> followingCalls = List.of(
                new AiToolCall("repeat_default", "get_doc", "{ \"docId\": 101, \"offset\": 0 }"),
                new AiToolCall("repeat_order", "default_api:get_doc", "{\"offset\":0,\"docId\":101}"),
                new AiToolCall("repeat_extra", "get_doc", "{\"docId\":101,\"unused\":true}"),
                new AiToolCall("next_page", "get_doc", "{\"docId\":101,\"offset\":12000}"),
                new AiToolCall("other_doc", "get_doc", "{\"docId\":102}"));
        AiChatResponse firstRound = new AiChatResponse();
        AiChatResponse nextRound = new AiChatResponse();
        nextRound.setToolCalls(followingCalls);
        List<AiToolCall> calls = new ArrayList<>();
        calls.add(first);
        if (sameRound) calls.addAll(followingCalls);
        firstRound.setToolCalls(calls);
        AiChatResponse answer = new AiChatResponse();
        answer.setContent("已完成查阅");
        when(getDocTool.execute(any(), any())).thenReturn("正文结果");
        when(chatProvider.chatWithTools(any(), any(), any(), eq("auto")))
                .thenReturn(firstRound, sameRound ? answer : nextRound, answer);
        SseEmitter emitter = mock(SseEmitter.class);

        service.streamChat(req, emitter);

        ArgumentCaptor<String> arguments = ArgumentCaptor.forClass(String.class);
        verify(getDocTool, times(3)).execute(arguments.capture(), any());
        assertThat(arguments.getAllValues()).containsExactly(
                "{\"docId\":101}", "{\"docId\":101,\"offset\":12000}", "{\"docId\":102}");
        ArgumentCaptor<List<Map<String, Object>>> messages = ArgumentCaptor.forClass(List.class);
        verify(chatProvider, times(sameRound ? 2 : 3)).chatWithTools(any(), messages.capture(), any(), eq("auto"));
        List<Map<String, Object>> results = messages.getValue().stream()
                .filter(message -> "tool".equals(message.get("role"))).collect(Collectors.toList());
        assertThat(results).hasSize(6);
        assertThat(results.stream().map(message -> message.get("tool_call_id")))
                .containsExactly("first", "repeat_default", "repeat_order", "repeat_extra", "next_page", "other_doc");
        assertThat(results.subList(1, 4)).allSatisfy(result ->
                assertThat(result.get("content").toString()).contains("不要重复查询", "nextOffset"));
        assertThat(emittedEvents(emitter)).contains("SUCCESS").doesNotContain("event:error");
        assertThat(emittedEvents(emitter).split("event:tool_start", -1)).hasSize(4);

        // 去重只针对本次问答，新问题仍可读取同一段正文。
        when(chatProvider.chatWithTools(any(), any(), any(), eq("auto"))).thenReturn(firstRound, answer);
        service.streamChat(req, mock(SseEmitter.class));
        verify(getDocTool, times(2)).execute(eq("{\"docId\":101}"), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void usesConfiguredToolRoundsThenExplicitlyRequestsFinalAnswer(boolean foundDocs) throws IOException {
        aiConfigProperties.setMaxToolRounds(8);
        AiAgentChatReqVo req = prepareFallbackChat();
        AgentTool tool = foundDocs ? getDocTool : searchTool;
        AtomicInteger round = new AtomicInteger();
        when(chatProvider.chatWithTools(any(), any(), any(), eq("auto"))).thenAnswer(call -> {
            int current = round.getAndIncrement();
            AiChatResponse response = new AiChatResponse();
            response.setToolCalls(List.of(new AiToolCall("read_" + current, tool.getName(), foundDocs
                    ? "{\"docId\":101,\"offset\":" + current * 12000 + "}"
                    : "{\"keywords\":\"登录\",\"page\":" + (current + 1) + "}")));
            return response;
        });
        when(tool.execute(any(), any())).thenAnswer(call -> {
            AgentContext context = call.getArgument(1);
            if (foundDocs) context.getInspectedDocIds().add(101);
            return foundDocs ? "PAGE_EVIDENCE " + call.getArgument(0) : "未找到匹配的文档";
        });
        when(chatProvider.chatWithTools(any(), any(), any(), eq("none"))).thenAnswer(call -> {
            List<Map<String, Object>> messages = call.getArgument(1);
            assertThat(messages.stream().filter(message -> "tool".equals(message.get("role"))))
                    .hasSize(8).allSatisfy(message -> assertThat(message.get("content").toString())
                            .contains(foundDocs ? "PAGE_EVIDENCE" : "未找到匹配的文档"));
            assertThat(messages.get(messages.size() - 1).get("content").toString()).contains("回答最初的问题", "证据不足");
            AiChatResponse response = new AiChatResponse();
            response.setContent(foundDocs ? "已确认的结论，依据 [登录接口](doc://101)。" : "本次未找到匹配文档，无法确认所需字段。");
            return response;
        });
        SseEmitter emitter = mock(SseEmitter.class);

        service.streamChat(req, emitter);

        verify(tool, times(8)).execute(any(), any());
        verify(chatProvider, times(8)).chatWithTools(any(), any(), any(), eq("auto"));
        verify(chatProvider).chatWithTools(any(), any(), any(), eq("none"));
        assertThat(emittedEvents(emitter)).contains(foundDocs ? "已确认的结论" : "无法确认所需字段", "SUCCESS")
                .doesNotContain("INCOMPLETE", "event:error");
    }

    @Test
    void repeatedSearchesWithDifferentJsonFormattingTriggerEarlySummary() throws IOException {
        AiAgentChatReqVo req = prepareFallbackChat();
        AtomicInteger round = new AtomicInteger();
        when(chatProvider.chatWithTools(any(), any(), any(), eq("auto"))).thenAnswer(call -> {
            int current = round.incrementAndGet();
            AiChatResponse response = new AiChatResponse();
            response.setToolCalls(List.of(new AiToolCall("search_" + current, "search_docs", current == 1
                    ? "{\"keywords\":\"登录\",\"page\":1}" : "{ \"page\": 1, \"keywords\": \"登录\" }")));
            return response;
        });
        when(searchTool.execute(any(), any())).thenAnswer(call -> {
            AgentContext context = call.getArgument(1);
            context.getDiscoveredDocIds().add(101);
            return "登录接口候选";
        });
        AiChatResponse answer = new AiChatResponse();
        answer.setContent("找到 [登录接口](doc://101)，参数尚未查阅。");
        when(chatProvider.chatWithTools(any(), any(), any(), eq("none"))).thenReturn(answer);
        SseEmitter emitter = mock(SseEmitter.class);

        service.streamChat(req, emitter);

        verify(searchTool).execute(any(), any());
        verify(chatProvider, times(3)).chatWithTools(any(), any(), any(), eq("auto"));
        verify(chatProvider).chatWithTools(any(), any(), any(), eq("none"));
        assertThat(emittedEvents(emitter)).contains("参数尚未查阅", "SUCCESS").doesNotContain("event:error");
    }

    @Test
    void summaryThatStillRequestsToolsIsNotTreatedAsAnAnswer() throws IOException {
        AiAgentChatReqVo req = prepareFallbackChat();
        req.setIncludeProjectOverview(true);
        when(projectDocOverviewService.build(any())).thenReturn(new AiProjectDocOverviewService.Overview("目录", 1, "已附加目录"));
        when(chatProvider.chatWithTools(any(), any(), any(), eq("auto"))).thenReturn(new AiChatResponse());
        AiChatResponse summary = new AiChatResponse();
        summary.setContent("我将继续搜索。");
        summary.setToolCalls(List.of(new AiToolCall("ignored", "search_docs", "{}")));
        when(chatProvider.chatWithTools(any(), any(), any(), eq("none"))).thenReturn(summary);
        SseEmitter emitter = mock(SseEmitter.class);

        service.streamChat(req, emitter);

        assertThat(emittedEvents(emitter)).contains("INCOMPLETE").doesNotContain("我将继续搜索", "SUCCESS", "event:error");
        verify(searchTool, never()).execute(any(), any());
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
