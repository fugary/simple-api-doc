package com.fugary.simple.api.service.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.service.ai.agent.tool.AiToolDefinition;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class AiStreamingProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String ANSWER = "**你好😀**\n```json\n{\"ok\":true}\n```";

    private AbstractAiChatProvider provider(String name) {
        AbstractAiChatProvider provider = "openai".equals(name) ? new OpenAiChatProvider()
                : "anthropic".equals(name) ? new AnthropicChatProvider() : new GeminiChatProvider();
        provider.objectMapper = mapper;
        provider.aiConfigProperties = new AiConfigProperties();
        provider.aiConfigProperties.setTimeout(5000);
        return provider;
    }

    private AiConfig config(int port) {
        AiConfig config = new AiConfig();
        config.setBaseUrl("http://127.0.0.1:" + port);
        config.setApiKey("test-key");
        config.setDefaultModel("test-model");
        return config;
    }

    private String fixture(String name) throws IOException {
        try (var in = getClass().getResourceAsStream("/ai-stream/" + name + ".sse")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "anthropic", "gemini"})
    void emitsBeforeUpstreamCompletesAndPreservesNativeToolsSignaturesAndUsage(String name) throws Exception {
        String fixture = fixture(name);
        int split = fixture.indexOf("\n\n", fixture.indexOf("你好")) + 2;
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch firstText = new CountDownLatch(1);
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                requestBody.set(mapper.readTree(exchange.getRequestBody()));
                requestPath.set(exchange.getRequestURI().toString());
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
                exchange.sendResponseHeaders(200, 0);
                // 每个字节单独写入，覆盖中文/emoji 跨网络分片。
                for (byte value : fixture.substring(0, split).getBytes(StandardCharsets.UTF_8)) {
                    exchange.getResponseBody().write(value);
                    exchange.getResponseBody().flush();
                }
                if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("No incremental callback");
                exchange.getResponseBody().write(fixture.substring(split).getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        });
        ExecutorService worker = Executors.newSingleThreadExecutor();
        server.start();
        try {
            StringBuilder emitted = new StringBuilder();
            AiStreamSession stream = new AiStreamSession(text -> { emitted.append(text); firstText.countDown(); });
            Future<AiChatResponse> future = worker.submit(() -> provider(name).chatWithToolsStream(
                    config(server.getAddress().getPort()), List.of(Map.of("role", "user", "content", "查询")),
                    List.of(new AiToolDefinition("search_docs", "搜索", Map.of("type", "object"))), "auto", stream));
            assertThat(firstText.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(future.isDone()).isFalse();
            release.countDown();
            AiChatResponse response = future.get(5, TimeUnit.SECONDS);
            assertThat(emitted.toString()).isEqualTo(ANSWER).doesNotContain("private", "sig");
            assertThat(response.getContent()).isEqualTo(ANSWER);
            assertThat(response.getToolCalls()).hasSize(2);
            assertThat(mapper.readTree(response.getToolCalls().get(0).getArguments()).path("keywords").asText()).isEqualTo("退款");
            assertThat(mapper.readTree(response.getToolCalls().get(1).getArguments()).path("docId").asInt()).isEqualTo(42);
            assertThat(mapper.writeValueAsString(response.getAssistantMessage())).contains("sig", "private");
            assertThat(response.getPromptTokens()).isEqualTo(10);
            assertThat(response.getCompletionTokens()).isEqualTo(4);
            assertThat(response.getTotalTokens()).isEqualTo(14);
            if ("gemini".equals(name)) assertThat(requestPath.get()).endsWith(":streamGenerateContent?alt=sse");
            else assertThat(requestBody.get().path("stream").asBoolean()).isTrue();
        } finally {
            release.countDown();
            server.stop(0);
            worker.shutdownNow();
        }
    }

    @Test
    void abortsUpstreamWhileWaitingForFirstToken() throws Exception {
        CountDownLatch requested = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requested.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AiStreamSession stream = new AiStreamSession(text -> fail("Unexpected text"));
        try {
            AiChatRequest request = new AiChatRequest();
            request.setSystemPrompt("系统");
            request.setUserMessage("问题");
            Future<?> future = worker.submit(() -> provider("openai").chatStream(config(server.getAddress().getPort()), request, stream));
            assertThat(requested.await(5, TimeUnit.SECONDS)).isTrue();
            stream.cancel();
            assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(CancellationException.class);
        } finally {
            release.countDown();
            server.stop(0);
            worker.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "anthropic", "gemini"})
    void rejectsTruncatedAndErrorStreams(String name) throws Exception {
        AiStreamAccumulator accumulator = new AiStreamAccumulator(name.toUpperCase(), mapper, new AiStreamSession(text -> {}));
        assertThatThrownBy(accumulator::result).isInstanceOf(IOException.class).hasMessageContaining("中断");
        assertThatThrownBy(() -> accumulator.accept("{\"error\":{\"message\":\"upstream failed\"}}"))
                .isInstanceOf(IOException.class).hasMessageContaining("upstream failed");
        assertThatThrownBy(() -> accumulator.accept("{broken" )).isInstanceOf(IOException.class);
    }
}
