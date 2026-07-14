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
public class AnthropicChatProvider extends AbstractAiChatProvider {

    private static final int DEFAULT_MAX_TOKENS = 8192;
    /** Anthropic API version, see https://docs.anthropic.com/en/api/versioning */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    public String getProviderCode() {
        return "ANTHROPIC";
    }

    @Override
    public AiChatResponse chat(AiConfig config, AiChatRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("x-api-key", config.getApiKey());
        headers.add("anthropic-version", ANTHROPIC_VERSION);

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

        try {
            String url = config.getBaseUrl().replaceAll("/+$", "") + "/messages";
            String rawResponse = callApi(url, headers, requestBody);
            JsonNode root = objectMapper.readTree(rawResponse);

            // Anthropic extended thinking 模式下 content 数组可能包含 "thinking" 类型块，需取第一个 type=text 的块
            JsonNode contentArr = root.path("content");
            String text = null;
            if (contentArr.isArray()) {
                for (JsonNode block : contentArr) {
                    if ("text".equals(block.path("type").asText())) {
                        text = block.path("text").asText();
                        break;
                    }
                }
            }
            if (text == null) {
                throw new RuntimeException("生成内容失败，AI 返回格式无法解析");
            }
            AiChatResponse chatResponse = new AiChatResponse();
            chatResponse.setRawResponse(rawResponse);
            chatResponse.setContent(cleanGeneratedContent(text));
            JsonNode usageNode = root.path("usage");
            if (!usageNode.isMissingNode()) {
                int promptTokens = usageNode.path("input_tokens").asInt();
                int completionTokens = usageNode.path("output_tokens").asInt();
                chatResponse.setPromptTokens(promptTokens);
                chatResponse.setCompletionTokens(completionTokens);
                chatResponse.setTotalTokens(promptTokens + completionTokens);
            }
            return chatResponse;
        } catch (Exception e) {
            log.error("调用 ANTHROPIC 接口失败", e);
            throw new RuntimeException(e);
        }
    }
}
