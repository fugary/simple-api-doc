package com.fugary.simple.api.service.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fugary.simple.api.entity.api.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenAiChatProvider extends AbstractAiChatProvider {

    @Override
    public String getProviderCode() {
        return "OPENAI";
    }

    @Override
    public AiChatResponse chat(AiConfig config, AiChatRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getDefaultModel());
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", request.getSystemPrompt()),
                Map.of("role", "user", "content", request.getUserMessage())
        ));
        if (request.getTemperature() != null) {
            requestBody.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            requestBody.put("max_tokens", request.getMaxTokens());
        }

        try {
            String url = config.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
            String rawResponse = callApi(url, headers, requestBody);
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode messageNode = root.path("choices").path(0).path("message").path("content");
            if (messageNode.isMissingNode()) {
                throw new RuntimeException("生成内容失败，AI 返回格式无法解析");
            }
            AiChatResponse chatResponse = new AiChatResponse();
            chatResponse.setRawResponse(rawResponse);
            chatResponse.setContent(cleanGeneratedContent(messageNode.asText()));
            JsonNode usageNode = root.path("usage");
            if (!usageNode.isMissingNode()) {
                chatResponse.setPromptTokens(usageNode.path("prompt_tokens").asInt());
                chatResponse.setCompletionTokens(usageNode.path("completion_tokens").asInt());
                chatResponse.setTotalTokens(usageNode.path("total_tokens").asInt());
            }
            return chatResponse;
        } catch (Exception e) {
            log.error("调用 OPENAI 接口失败", e);
            throw new RuntimeException(e);
        }
    }
}
