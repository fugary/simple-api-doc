package com.fugary.simple.api.service.ai.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.service.ai.agent.tool.AiToolCall;
import com.fugary.simple.api.service.ai.agent.tool.AiToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AnthropicChatProvider extends AbstractAiChatProvider {

    private static final int DEFAULT_MAX_TOKENS = 8192;
    /** Anthropic API version, see https://docs.anthropic.com/en/api/versioning */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private HttpHeaders createHeaders(AiConfig config) {
        HttpHeaders headers = createApiKeyJsonHeaders("x-api-key", config.getApiKey());
        headers.set("anthropic-version", ANTHROPIC_VERSION);
        return headers;
    }

    @Override
    public String getProviderCode() {
        return "ANTHROPIC";
    }

    @Override
    public AiChatResponse chat(AiConfig config, AiChatRequest request) {
        HttpHeaders headers = createHeaders(config);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getDefaultModel());
        requestBody.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : DEFAULT_MAX_TOKENS);
        requestBody.put("system", request.getSystemPrompt());
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", request.getUserMessage())
        ));
        if (request.getTemperature() != null) {
            requestBody.put("temperature", request.getTemperature());
        }

        String url = buildUrl(config.getBaseUrl(), "/messages");
        String rawResponse = callApi(url, headers, requestBody);
        AiChatResponse response = parseResponse(rawResponse);
        response.setContent(cleanGeneratedContent(response.getContent()));
        return response;
    }

    @Override
    public AiChatResponse chatWithTools(AiConfig config, List<Map<String, Object>> messages,
                                        List<AiToolDefinition> tools, String toolChoice) {
        HttpHeaders headers = createHeaders(config);
        Map<String, Object> body = new HashMap<>();
        body.put("model", config.getDefaultModel());
        body.put("max_tokens", DEFAULT_MAX_TOKENS);
        List<Map<String, Object>> converted = new ArrayList<>();
        List<Map<String, Object>> system = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            String role = (String) message.get("role");
            if ("system".equals(role)) {
                system.add(Map.of("type", "text", "text", message.get("content")));
            } else if ("tool".equals(role)) {
                results.add(Map.of("type", "tool_result", "tool_use_id", message.get("tool_call_id"),
                        "content", message.get("content")));
            } else {
                if (!results.isEmpty()) {
                    converted.add(Map.of("role", "user", "content", new ArrayList<>(results)));
                    results.clear();
                }
                // 保留原生 content 数组，包括 thinking/signature 和全部 tool_use 块。
                converted.add(Map.of("role", role, "content", message.get("content")));
            }
        }
        if (!results.isEmpty()) {
            converted.add(Map.of("role", "user", "content", results));
        }
        if (!system.isEmpty()) {
            body.put("system", system);
        }
        body.put("messages", converted);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", formatToolDefinitions(tools, "input_schema"));
            String choice = toolChoice == null || toolChoice.isBlank() ? "auto" : toolChoice;
            body.put("tool_choice", "required".equals(choice) ? Map.of("type", "any")
                    : List.of("auto", "none").contains(choice) ? Map.of("type", choice)
                    : Map.of("type", "tool", "name", choice));
        }
        long start = System.currentTimeMillis();
        String raw = callApi(buildUrl(config.getBaseUrl(), "/messages"), headers, body);
        AiChatResponse response = parseResponse(raw);
        response.setElapsedTime(System.currentTimeMillis() - start);
        return response;
    }

    private AiChatResponse parseResponse(String raw) {
        JsonNode root = parseJson(raw);
        if (!root.path("content").isArray()) {
            throw new RuntimeException("AI 请求返回格式无法解析");
        }
        AiChatResponse response = new AiChatResponse();
        response.setRawResponse(raw);
        response.setAssistantMessage(Map.of("role", "assistant", "content",
                objectMapper.convertValue(root.get("content"), new TypeReference<List<Map<String, Object>>>() {})));
        List<AiToolCall> calls = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (JsonNode block : root.path("content")) {
            if ("tool_use".equals(block.path("type").asText())) {
                calls.add(new AiToolCall(block.path("id").asText(), block.path("name").asText(),
                        block.path("input").toString()));
            } else if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        response.setToolCalls(calls);
        response.setContent(text.toString());
        JsonNode usage = root.path("usage");
        if (!usage.isMissingNode()) {
            int input = usage.path("input_tokens").asInt();
            int output = usage.path("output_tokens").asInt();
            response.setPromptTokens(input);
            response.setCompletionTokens(output);
            response.setTotalTokens(input + output);
        }
        return response;
    }

    @Override
    public List<String> loadModels(AiConfig config) {
        HttpHeaders headers = createHeaders(config);
        String url = buildUrl(config.getBaseUrl(), "/models");
        String rawResponse = callApiGet(url, headers);
        return extractModelIdsFromData(rawResponse);
    }
}
