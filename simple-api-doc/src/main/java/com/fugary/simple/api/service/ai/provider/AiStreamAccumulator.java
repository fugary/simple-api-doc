package com.fugary.simple.api.service.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** 将各协议增量还原为原生响应，复用现有工具调用解析；只向页面输出正文。 */
class AiStreamAccumulator {
    private final String provider;
    private final ObjectMapper mapper;
    private final AiStreamSession stream;
    private final ObjectNode root;
    private final Map<Integer, StringBuilder> toolInputs = new HashMap<>();
    private boolean completed;
    private String finishReason;

    AiStreamAccumulator(String provider, ObjectMapper mapper, AiStreamSession stream) {
        this.provider = provider;
        this.mapper = mapper;
        this.stream = stream;
        root = mapper.createObjectNode();
        if ("OPENAI".equals(provider)) {
            root.putArray("choices").addObject().putObject("message").put("role", "assistant").put("content", "");
        } else if ("ANTHROPIC".equals(provider)) {
            root.put("role", "assistant").putArray("content");
        } else {
            root.putArray("candidates").addObject().putObject("content").put("role", "model").putArray("parts");
        }
    }

    void accept(String data) throws IOException {
        stream.check();
        if ("[DONE]".equals(data)) {
            completed = true;
            return;
        }
        JsonNode event = mapper.readTree(data);
        if (event == null || !event.isObject()) throw new IOException("AI 流式响应格式无效");
        if (event.hasNonNull("error")) {
            throw new IOException(AbstractAiChatProvider.extractJsonErrorMessage(event));
        }
        if ("OPENAI".equals(provider)) openAi(event);
        else if ("ANTHROPIC".equals(provider)) anthropic(event);
        else gemini(event);
    }

    private void openAi(JsonNode event) {
        if (event.hasNonNull("usage")) root.set("usage", event.get("usage"));
        for (String field : new String[]{"id", "model", "created"}) {
            if (event.has(field)) root.set(field, event.get(field));
        }
        for (JsonNode choice : event.path("choices")) {
            if (choice.path("index").asInt() != 0) continue;
            ObjectNode target = (ObjectNode) root.path("choices").get(0);
            JsonNode delta = choice.path("delta");
            if (delta.isObject()) {
                mergeDelta((ObjectNode) target.get("message"), delta);
                if (delta.path("content").isTextual()) stream.text(delta.get("content").asText());
            }
            if (choice.hasNonNull("finish_reason")) {
                finishReason = choice.get("finish_reason").asText();
                target.set("finish_reason", choice.get("finish_reason"));
            }
        }
    }

    private void anthropic(JsonNode event) throws IOException {
        String type = event.path("type").asText();
        if ("message_start".equals(type)) {
            root.setAll((ObjectNode) event.path("message"));
        } else if ("content_block_start".equals(type)) {
            int index = event.path("index").asInt();
            ObjectNode block = slot((ArrayNode) root.get("content"), index);
            block.setAll((ObjectNode) event.path("content_block"));
            if ("text".equals(block.path("type").asText())) stream.text(block.path("text").asText(""));
        } else if ("content_block_delta".equals(type)) {
            int index = event.path("index").asInt();
            ObjectNode block = slot((ArrayNode) root.get("content"), index);
            JsonNode delta = event.path("delta");
            switch (delta.path("type").asText()) {
                case "text_delta":
                    append(block, "text", delta.path("text").asText());
                    stream.text(delta.path("text").asText());
                    break;
                case "input_json_delta":
                    toolInputs.computeIfAbsent(index, key -> new StringBuilder()).append(delta.path("partial_json").asText());
                    break;
                case "thinking_delta": append(block, "thinking", delta.path("thinking").asText()); break;
                case "signature_delta": append(block, "signature", delta.path("signature").asText()); break;
                case "citations_delta": block.withArray("citations").add(delta.path("citation")); break;
                default: break;
            }
        } else if ("content_block_stop".equals(type)) {
            int index = event.path("index").asInt();
            StringBuilder input = toolInputs.remove(index);
            if (input != null) slot((ArrayNode) root.get("content"), index).set("input", mapper.readTree(input.toString()));
        } else if ("message_delta".equals(type)) {
            root.setAll((ObjectNode) event.path("delta"));
            if (event.path("usage").isObject()) root.with("usage").setAll((ObjectNode) event.get("usage"));
            finishReason = event.path("delta").path("stop_reason").asText(null);
        } else if ("message_stop".equals(type)) {
            completed = true;
        }
    }

    private void gemini(JsonNode event) {
        if (event.has("usageMetadata")) root.set("usageMetadata", event.get("usageMetadata"));
        if (event.has("promptFeedback")) root.set("promptFeedback", event.get("promptFeedback"));
        for (JsonNode candidate : event.path("candidates")) {
            if (candidate.path("index").asInt() != 0) continue;
            ObjectNode target = (ObjectNode) root.path("candidates").get(0);
            ArrayNode parts = (ArrayNode) target.path("content").get("parts");
            for (JsonNode part : candidate.path("content").path("parts")) {
                // 每个 part 连同签名保留原位置，不能把思考文本混入回答。
                parts.add(part.deepCopy());
                if (part.has("text") && !part.path("thought").asBoolean()) stream.text(part.get("text").asText());
            }
            if (candidate.hasNonNull("finishReason")) {
                finishReason = candidate.get("finishReason").asText();
                target.set("finishReason", candidate.get("finishReason"));
                completed = true;
            }
        }
    }

    private ObjectNode slot(ArrayNode array, int index) {
        if (index < 0 || index > 1024) throw new IllegalArgumentException("AI 响应块索引无效");
        while (array.size() <= index) array.addObject();
        return (ObjectNode) array.get(index);
    }

    private void append(ObjectNode target, String field, String text) {
        target.put(field, target.path(field).asText("") + text);
    }

    private void mergeDelta(ObjectNode target, JsonNode delta) {
        delta.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isNull() || "index".equals(key)) return;
            if (value.isObject()) mergeDelta(target.with(key), value);
            else if (value.isArray()) {
                ArrayNode array = target.withArray(key);
                for (JsonNode item : value) {
                    if (item.has("index")) mergeDelta(slot(array, item.get("index").asInt()), item);
                    else array.add(item.deepCopy());
                }
            } else if (value.isTextual() && !"role".equals(key) && !"type".equals(key)) {
                append(target, key, value.asText());
            } else target.set(key, value);
        });
    }

    boolean isTerminal() { return completed && !"GEMINI".equals(provider); }

    String result() throws IOException {
        stream.check();
        if (!completed || !toolInputs.isEmpty()) throw new IOException("AI 流式响应中断，未收到完整结束事件");
        if (finishReason != null && !java.util.List.of("stop", "tool_calls", "end_turn", "tool_use", "stop_sequence", "STOP").contains(finishReason)) {
            throw new IOException("AI 未完整生成回答: " + finishReason);
        }
        return root.toString();
    }
}
