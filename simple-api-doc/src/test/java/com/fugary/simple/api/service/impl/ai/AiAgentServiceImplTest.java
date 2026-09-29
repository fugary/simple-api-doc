package com.fugary.simple.api.service.impl.ai;

import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.service.ai.AiConfigService;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        service.streamChat(req, emitter);

        // 验证工具调用了 1 次
        verify(searchTool, times(1)).execute(eq("{\"keywords\":\"退款\"}"), any());

        // 验证 SSE 收到 tool_start, tool_end, related_docs, delta, finish
        verify(emitter, atLeast(4)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, times(1)).complete();
    }

    @Test
    void testSanitizeMarkdownContent() {
        // 测试未闭合的 http 代码块吞噬后续的 ## 标题
        String unclosedHttp = "```http\n" +
                "POST /user/register\n" +
                "Content-Type: application/json\n\n" +
                "{\n" +
                "  \"username\": \"test\"\n" +
                "}\n\n" +
                "## 2. 参数说明\n" +
                "- **username**: 用户名";

        String sanitized = service.sanitizeMarkdownContent(unclosedHttp);
        assertThat(sanitized).contains("}\n```\n\n## 2. 参数说明");

        // 测试末尾未闭合的代码块自动补齐
        String danglingJson = "### 示例\n```json\n{\"code\":200}";
        String sanitizedJson = service.sanitizeMarkdownContent(danglingJson);
        assertThat(sanitizedJson).endsWith("\n```");

        // 测试正常闭合的代码块不被篡改
        String normal = "```http\nGET /users\n```\n## 2. 说明\n正常内容";
        assertThat(service.sanitizeMarkdownContent(normal)).isEqualTo(normal);
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
