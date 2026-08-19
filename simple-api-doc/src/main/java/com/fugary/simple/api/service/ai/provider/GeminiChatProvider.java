package com.fugary.simple.api.service.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fugary.simple.api.entity.api.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeminiChatProvider extends AbstractAiChatProvider {

    @Override
    public String getProviderCode() {
        return "GEMINI";
    }

    @Override
    public AiChatResponse chat(AiConfig config, AiChatRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

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

        try {
            String url = config.getBaseUrl().replaceAll("/+$", "") + "/models/" + config.getDefaultModel()
                    + ":generateContent?key=" + config.getApiKey();
            String rawResponse = callApi(url, headers, requestBody);
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode()) {
                throw new RuntimeException("生成内容失败，AI 返回格式无法解析");
            }
            AiChatResponse chatResponse = new AiChatResponse();
            chatResponse.setRawResponse(rawResponse);
            chatResponse.setContent(cleanGeneratedContent(textNode.asText()));
            JsonNode usageNode = root.path("usageMetadata");
            if (!usageNode.isMissingNode()) {
                chatResponse.setPromptTokens(usageNode.path("promptTokenCount").asInt());
                chatResponse.setCompletionTokens(usageNode.path("candidatesTokenCount").asInt());
                chatResponse.setTotalTokens(usageNode.path("totalTokenCount").asInt());
            }
            return chatResponse;
        } catch (Exception e) {
            log.error("调用 GEMINI 接口失败", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> loadModels(AiConfig config) {
        String url = config.getBaseUrl().replaceAll("/+$", "") + "/models?key=" + config.getApiKey();
        try {
            String rawResponse = callApiGet(url, new HttpHeaders());
            JsonNode root = objectMapper.readTree(rawResponse);
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
        } catch (Exception e) {
            log.error("获取 Gemini 模型列表失败, url: {}", url, e);
            throw new RuntimeException("获取模型列表失败: " + e.getMessage(), e);
        }
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
