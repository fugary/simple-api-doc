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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class GeminiChatProvider extends AbstractAiChatProvider {

    private static final String TOOL_CALL_IDS_KEY = "_toolCallIds";

    private String buildGenerateContentUrl(AiConfig config) {
        String model = config.getDefaultModel().replaceFirst("^models/", "");
        return buildUrl(config.getBaseUrl(), "/models/" + model + ":generateContent");
    }

    @Override
    public String getProviderCode() {
        return "GEMINI";
    }

    @Override
    public AiChatResponse chatWithTools(AiConfig config, List<Map<String, Object>> messages, List<AiToolDefinition> tools, String toolChoice) {
        HttpHeaders headers = createApiKeyJsonHeaders("x-goog-api-key", config.getApiKey());
        Map<String, Object> body = new HashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();
        List<Map<String, Object>> systemParts = new ArrayList<>();
        Map<String, JsonNode> callsById = new HashMap<>();
        List<Map<String, Object>> pendingResults = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            String role = (String) message.get("role");
            if ("system".equals(role)) {
                systemParts.add(Map.of("text", message.get("content")));
            } else if ("tool".equals(role)) {
                JsonNode call = callsById.get(message.get("tool_call_id"));
                if (call == null) {
                    throw new IllegalArgumentException("未找到 Gemini 工具调用上下文");
                }
                Map<String, Object> result = new HashMap<>();
                result.put("name", call.path("name").asText());
                if (call.hasNonNull("id")) {
                    result.put("id", call.path("id").asText());
                }
                result.put("response", Map.of("result", message.get("content")));
                pendingResults.add(Map.of("functionResponse", result));
            } else {
                if (!pendingResults.isEmpty()) {
                    contents.add(Map.of("role", "user", "parts", new ArrayList<>(pendingResults)));
                    pendingResults.clear();
                }
                if (message.containsKey("parts")) {
                    // 原样回传全部 parts，保留 thoughtSignature 及其位置，不重新构造模型响应。
                    contents.add(Map.of("role", "model", "parts", message.get("parts")));
                    JsonNode parts = objectMapper.valueToTree(message.get("parts"));
                    JsonNode ids = objectMapper.valueToTree(message.get(TOOL_CALL_IDS_KEY));
                    int index = 0;
                    for (JsonNode part : parts) {
                        if (part.has("functionCall")) {
                            callsById.put(ids.path(index++).asText(), part.get("functionCall"));
                        }
                    }
                } else {
                    contents.add(Map.of("role", "assistant".equals(role) ? "model" : "user",
                            "parts", List.of(Map.of("text", message.get("content")))));
                }
            }
        }
        if (!pendingResults.isEmpty()) {
            contents.add(Map.of("role", "user", "parts", pendingResults));
        }
        if (!systemParts.isEmpty()) {
            body.put("systemInstruction", Map.of("parts", systemParts));
        }
        body.put("contents", contents);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", List.of(Map.of("functionDeclarations", formatToolDefinitions(tools, "parameters"))));
            Map<String, Object> calling = new HashMap<>();
            String choice = toolChoice == null || toolChoice.isBlank() ? "auto" : toolChoice;
            calling.put("mode", "none".equals(choice) ? "NONE" : "auto".equals(choice) ? "AUTO" : "ANY");
            if (!List.of("auto", "none", "required").contains(choice)) {
                calling.put("allowedFunctionNames", List.of(choice));
            }
            body.put("toolConfig", Map.of("functionCallingConfig", calling));
        }
        long start = System.currentTimeMillis();
        String raw = callApi(buildGenerateContentUrl(config), headers, body);
        AiChatResponse response = parseResponse(raw);
        response.setElapsedTime(System.currentTimeMillis() - start);
        return response;
    }

    private AiChatResponse parseResponse(String raw) {
        JsonNode root = parseJson(raw);
        JsonNode content = root.path("candidates").path(0).path("content");
        if (!content.path("parts").isArray()) {
            throw new RuntimeException("AI 请求返回格式无法解析");
        }
        AiChatResponse response = new AiChatResponse();
        response.setRawResponse(raw);
        List<AiToolCall> calls = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (JsonNode part : content.path("parts")) {
            if (part.has("functionCall")) {
                JsonNode call = part.get("functionCall");
                String id = call.path("id").asText("");
                if (id.isBlank()) {
                    id = UUID.randomUUID().toString();
                }
                ids.add(id);
                calls.add(new AiToolCall(id, call.path("name").asText(),
                        call.has("args") ? call.get("args").toString() : "{}"));
            } else if (part.has("text") && !part.path("thought").asBoolean()) {
                text.append(part.path("text").asText());
            }
        }
        Map<String, Object> assistant = objectMapper.convertValue(content, new TypeReference<Map<String, Object>>() {});
        assistant.put(TOOL_CALL_IDS_KEY, ids);
        response.setAssistantMessage(assistant);
        response.setToolCalls(calls);
        response.setContent(text.toString());
        JsonNode usage = root.path("usageMetadata");
        if (!usage.isMissingNode()) {
            response.setPromptTokens(usage.path("promptTokenCount").asInt());
            response.setCompletionTokens(usage.path("candidatesTokenCount").asInt());
            response.setTotalTokens(usage.path("totalTokenCount").asInt());
        }
        return response;
    }

    @Override
    public AiChatResponse chat(AiConfig config, AiChatRequest request) {
        HttpHeaders headers = createApiKeyJsonHeaders("x-goog-api-key", config.getApiKey());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("systemInstruction", Map.of("parts", List.of(Map.of("text", request.getSystemPrompt()))));
        requestBody.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", request.getUserMessage()))
        )));

        Map<String, Object> generationConfig = new HashMap<>();
        if (request.getTemperature() != null) {
            generationConfig.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            generationConfig.put("maxOutputTokens", request.getMaxTokens());
        }
        if (!generationConfig.isEmpty()) {
            requestBody.put("generationConfig", generationConfig);
        }

        String url = buildGenerateContentUrl(config);
        String rawResponse = callApi(url, headers, requestBody);
        AiChatResponse response = parseResponse(rawResponse);
        response.setContent(cleanGeneratedContent(response.getContent()));
        return response;
    }

    @Override
    public List<String> loadModels(AiConfig config) {
        String url = buildUrl(config.getBaseUrl(), "/models");
        String rawResponse = callApiGet(url, createApiKeyJsonHeaders("x-goog-api-key", config.getApiKey()));
        JsonNode root = parseJson(rawResponse);
        JsonNode modelsNode = root.path("models");
        List<String> models = new ArrayList<>();
        if (modelsNode.isArray()) {
            for (JsonNode item : modelsNode) {
                if (!supportsGenerateContent(item)) {
                    continue;
                }
                String name = item.path("name").asText();
                if (name != null && !name.isBlank()) {
                    models.add(name.startsWith("models/") ? name.substring(7) : name);
                }
            }
        }
        Collections.sort(models);
        return models;
    }

    private boolean supportsGenerateContent(JsonNode item) {
        JsonNode methodsNode = item.path("supportedGenerationMethods");
        if (!methodsNode.isArray()) {
            return true;
        }
        for (JsonNode m : methodsNode) {
            if ("generateContent".equals(m.asText())) {
                return true;
            }
        }
        return false;
    }
}
