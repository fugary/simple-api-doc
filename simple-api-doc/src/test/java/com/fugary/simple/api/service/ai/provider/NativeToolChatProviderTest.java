package com.fugary.simple.api.service.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.service.ai.agent.tool.AiToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NativeToolChatProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AiConfig config = new AiConfig();
    private final List<AiToolDefinition> tools = List.of(new AiToolDefinition("search_docs", "搜索",
            Map.of("type", "object", "properties", Map.of("keywords", Map.of("type", "string")),
                    "required", List.of("keywords"))));

    NativeToolChatProviderTest() {
        config.setBaseUrl("https://example.invalid/v1");
        config.setApiKey("test-key");
        config.setDefaultModel("test-model");
    }

    private List<Map<String, Object>> messages() {
        return new ArrayList<>(List.of(Map.of("role", "system", "content", "查阅文档"),
                Map.of("role", "user", "content", "退款接口")));
    }

    private <T extends AbstractAiChatProvider> T stub(T instance, String... responses) {
        T provider = spy(instance);
        provider.objectMapper = mapper;
        AtomicInteger index = new AtomicInteger();
        doAnswer(call -> responses[Math.min(index.getAndIncrement(), responses.length - 1)])
                .when(provider).callApi(anyString(), any(), any());
        return provider;
    }

    private List<JsonNode> requests(AbstractAiChatProvider provider, int count) {
        ArgumentCaptor<Object> bodies = ArgumentCaptor.forClass(Object.class);
        verify(provider, times(count)).callApi(anyString(), any(), bodies.capture());
        List<JsonNode> values = new ArrayList<>();
        bodies.getAllValues().forEach(body -> values.add(mapper.valueToTree(body)));
        return values;
    }

    @Test
    void geminiPreservesSignedPartsAndPairsMultipleResultsIncludingMissingIds() throws Exception {
        String raw = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
                + "{\"text\":\"分析\",\"thought\":true,\"thoughtSignature\":\"text-sig\"},"
                + "{\"functionCall\":{\"name\":\"search_docs\",\"args\":{\"keywords\":\"退款\"}},\"thoughtSignature\":\"call-sig\"},"
                + "{\"functionCall\":{\"id\":\"native-id\",\"name\":\"get_doc\",\"args\":{\"docId\":42}}}]}}]}";
        String answer = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
                + "{\"text\":\"隐藏思考\",\"thought\":true},{\"text\":\"### 回答\\n\"},{\"text\":\"```json\\n{}\\n```\"}]}}],"
                + "\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":4,\"totalTokenCount\":14}}";
        GeminiChatProvider provider = stub(new GeminiChatProvider(), raw, answer);
        List<Map<String, Object>> history = messages();
        AiChatResponse first = provider.chatWithTools(config, history, tools);
        assertThat(first.getContent()).isEmpty();
        assertThat(first.getToolCalls()).hasSize(2);
        assertThat(first.getToolCalls().get(0).getId()).isNotBlank();
        history.add(first.getAssistantMessage());
        first.getToolCalls().forEach(call -> history.add(Map.of("role", "tool", "tool_call_id", call.getId(), "content", "结果")));
        AiChatResponse second = provider.chatWithTools(config, history, tools, "none");
        JsonNode request = requests(provider, 2).get(1);
        assertThat(request.path("contents").get(1)).isEqualTo(mapper.readTree(raw).path("candidates").get(0).path("content"));
        JsonNode results = request.path("contents").get(2).path("parts");
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get(0).path("functionResponse").path("name").asText()).isEqualTo("search_docs");
        assertThat(results.get(0).path("functionResponse").has("id")).isFalse();
        assertThat(results.get(1).path("functionResponse").path("id").asText()).isEqualTo("native-id");
        assertThat(request.path("toolConfig").path("functionCallingConfig").path("mode").asText()).isEqualTo("NONE");
        assertThat(second.getContent()).isEqualTo("### 回答\n```json\n{}\n```");
        assertThat(second.getTotalTokens()).isEqualTo(14);
    }

    @Test
    void anthropicReplaysThinkingAndGroupsAllToolResultsInOneUserMessage() throws Exception {
        String raw = "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"分析\",\"signature\":\"signed\"},"
                + "{\"type\":\"text\",\"text\":\"先检索\"},"
                + "{\"type\":\"tool_use\",\"id\":\"a\",\"name\":\"search_docs\",\"input\":{\"keywords\":\"退款\"}},"
                + "{\"type\":\"tool_use\",\"id\":\"b\",\"name\":\"get_doc\",\"input\":{\"docId\":42}}]}";
        AnthropicChatProvider provider = stub(new AnthropicChatProvider(), raw,
                "{\"content\":[{\"type\":\"text\",\"text\":\"### 回答\\n\"},{\"type\":\"text\",\"text\":\"```http\\nGET /a\\n```\"}],"
                        + "\"usage\":{\"input_tokens\":12,\"output_tokens\":5}}");
        List<Map<String, Object>> history = messages();
        AiChatResponse first = provider.chatWithTools(config, history, tools);
        assertThat(first.getToolCalls()).hasSize(2);
        history.add(first.getAssistantMessage());
        first.getToolCalls().forEach(call -> history.add(Map.of("role", "tool", "tool_call_id", call.getId(), "content", "结果")));
        AiChatResponse second = provider.chatWithTools(config, history, tools, "none");
        JsonNode request = requests(provider, 2).get(1);
        assertThat(request.path("messages").size()).isEqualTo(3);
        assertThat(request.path("messages").get(1).path("content")).isEqualTo(mapper.readTree(raw).path("content"));
        JsonNode results = request.path("messages").get(2);
        assertThat(results.path("role").asText()).isEqualTo("user");
        assertThat(results.path("content").size()).isEqualTo(2);
        assertThat(results.path("content").get(1).path("tool_use_id").asText()).isEqualTo("b");
        assertThat(request.path("tool_choice").path("type").asText()).isEqualTo("none");
        assertThat(second.getContent()).isEqualTo("### 回答\n```http\nGET /a\n```");
        assertThat(second.getTotalTokens()).isEqualTo(17);
    }

    @ParameterizedTest
    @CsvSource({"auto,AUTO,auto", "none,NONE,none", "required,ANY,any", "search_docs,ANY,tool"})
    void mapsToolChoiceAndSchemas(String choice, String geminiMode, String anthropicType) {
        GeminiChatProvider gemini = stub(new GeminiChatProvider(), "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"完成\"}]}}]}");
        AnthropicChatProvider anthropic = stub(new AnthropicChatProvider(), "{\"content\":[{\"type\":\"text\",\"text\":\"完成\"}]}");
        gemini.chatWithTools(config, messages(), tools, choice);
        anthropic.chatWithTools(config, messages(), tools, choice);
        JsonNode g = requests(gemini, 1).get(0);
        JsonNode a = requests(anthropic, 1).get(0);
        assertThat(g.path("toolConfig").path("functionCallingConfig").path("mode").asText()).isEqualTo(geminiMode);
        assertThat(a.path("tool_choice").path("type").asText()).isEqualTo(anthropicType);
        assertThat(g.path("tools").get(0).path("functionDeclarations").get(0).path("parameters"))
                .isEqualTo(a.path("tools").get(0).path("input_schema"));
        if ("search_docs".equals(choice)) {
            assertThat(g.path("toolConfig").path("functionCallingConfig").path("allowedFunctionNames").get(0).asText()).isEqualTo(choice);
            assertThat(a.path("tool_choice").path("name").asText()).isEqualTo(choice);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void keepsCompletedToolTurnsInOrderAcrossMultipleRounds(boolean gemini) {
        String response = gemini
                ? "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"search_docs\",\"args\":{}}}]}}]}"
                : "{\"content\":[{\"type\":\"tool_use\",\"id\":\"call\",\"name\":\"search_docs\",\"input\":{}}]}";
        AbstractAiChatProvider provider = gemini ? stub(new GeminiChatProvider(), response)
                : stub(new AnthropicChatProvider(), response);
        List<Map<String, Object>> history = messages();
        for (int round = 0; round < 3; round++) {
            AiChatResponse result = provider.chatWithTools(config, history, tools);
            history.add(result.getAssistantMessage());
            history.add(Map.of("role", "tool", "tool_call_id", result.getToolCalls().get(0).getId(),
                    "content", "结果" + round));
        }
        JsonNode request = requests(provider, 3).get(2);
        JsonNode turns = request.path(gemini ? "contents" : "messages");
        assertThat(turns.size()).isEqualTo(5);
        assertThat(turns.get(2).path("role").asText()).isEqualTo("user");
        assertThat(turns.get(4).path("role").asText()).isEqualTo("user");
        assertThat(turns.get(2).toString()).contains("结果0");
        assertThat(turns.get(4).toString()).contains("结果1");
        assertThat(turns.get(1).has("_toolCallIds")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ordinaryChatSharesTextParsingButStillExtractsGeneratedJson(boolean gemini) {
        String response = gemini
                ? "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
                    + "{\"text\":\"private reasoning\",\"thought\":true},"
                    + "{\"text\":\"```json\\n{\"},{\"text\":\"\\\"ok\\\":true}\\n```\"}]}}],"
                    + "\"usageMetadata\":{\"promptTokenCount\":12,\"candidatesTokenCount\":5,\"totalTokenCount\":17}}"
                : "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"private reasoning\",\"signature\":\"sig\"},"
                    + "{\"type\":\"text\",\"text\":\"```json\\n{\"},{\"type\":\"text\",\"text\":\"\\\"ok\\\":true}\\n```\"}],"
                    + "\"usage\":{\"input_tokens\":12,\"output_tokens\":5}}";
        AbstractAiChatProvider provider = gemini ? stub(new GeminiChatProvider(), response)
                : stub(new AnthropicChatProvider(), response);
        AiChatRequest request = new AiChatRequest();
        request.setSystemPrompt("生成 JSON");
        request.setUserMessage("生成示例");
        request.setTemperature(0.2);
        request.setMaxTokens(1024);
        AiChatResponse result = provider.chat(config, request);
        assertThat(result.getContent()).isEqualTo("{\"ok\":true}");
        assertThat(result.getTotalTokens()).isEqualTo(17);
        JsonNode body = requests(provider, 1).get(0);
        assertThat(body.has("tools")).isFalse();
        assertThat(gemini ? body.path("generationConfig").path("maxOutputTokens").asInt()
                : body.path("max_tokens").asInt()).isEqualTo(1024);
    }

    @Test
    void geminiUsesTheSameNormalizedUrlAndHeaderAuthenticationForBothChatModes() {
        config.setDefaultModel("models/test-model");
        GeminiChatProvider provider = stub(new GeminiChatProvider(),
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"完成\"}]}}]}");
        AiChatRequest request = new AiChatRequest();
        request.setSystemPrompt("系统提示");
        request.setUserMessage("用户问题");
        provider.chat(config, request);
        provider.chatWithTools(config, messages(), tools);
        verify(provider, times(2)).callApi(eq("https://example.invalid/v1/models/test-model:generateContent"),
                argThat(headers -> "test-key".equals(headers.getFirst("x-goog-api-key"))), any());
    }

    @Test
    void rejectsMalformedNativeResponses() {
        for (AbstractAiChatProvider provider : List.of(stub(new GeminiChatProvider(), "{}"),
                stub(new AnthropicChatProvider(), "{}"))) {
            assertThatThrownBy(() -> provider.chatWithTools(config, messages(), tools))
                    .isInstanceOf(RuntimeException.class).hasMessageContaining("无法解析");
        }
    }
}
