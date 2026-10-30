package com.fugary.simple.api.service.ai.provider;

import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.servlet.HttpRequestUtils;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicReference;

/** 两个 AI 流式入口共用线程上下文、心跳和连接清理。 */
@Component
public class AiSseSupport {
    @Autowired
    @Qualifier("taskScheduler")
    private ThreadPoolTaskScheduler scheduler;

    public SseEmitter start(BiConsumer<SseEmitter, AiStreamSession> action) {
        HttpServletResponse response = HttpRequestUtils.getCurrentResponse();
        if (response != null) {
            response.setHeader("Cache-Control", "no-cache, no-transform");
            response.setHeader("X-Accel-Buffering", "no");
        }
        SseEmitter emitter = new SseEmitter(180_000L);
        AtomicBoolean closed = new AtomicBoolean();
        AtomicReference<ScheduledFuture<?>> heartbeatRef = new AtomicReference<>();
        AiStreamSession stream = new AiStreamSession(text -> send(emitter, "delta", Map.of("text", text)));
        Runnable cleanup = () -> {
            closed.set(true);
            stream.cancel();
            ScheduledFuture<?> task = heartbeatRef.get();
            if (task != null) task.cancel(false);
        };
        Runnable close = () -> {
            cleanup.run();
            emitter.complete();
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(close);
        emitter.onError(error -> close.run());
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        // 首字生成期间也发送心跳，以识别浏览器断开，并避免代理空闲超时。
        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
            if (closed.get()) return;
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (Exception e) {
                cleanup.run();
                emitter.completeWithError(e);
            }
        }, 5000);
        heartbeatRef.set(heartbeat);
        if (closed.get()) heartbeat.cancel(false);
        try {
            scheduler.execute(() -> {
                try {
                    if (closed.get()) return;
                    if (attributes != null) RequestContextHolder.setRequestAttributes(attributes);
                    action.accept(emitter, stream);
                } catch (Exception e) {
                    if (!closed.get() && !stream.isCancelled()) {
                        try { send(emitter, "error", Map.of("message", e.getMessage() == null ? "AI 服务异常" : e.getMessage())); }
                        catch (Exception ignored) { }
                    }
                } finally {
                    heartbeat.cancel(false);
                    RequestContextHolder.resetRequestAttributes();
                    close.run();
                }
            });
        } catch (RuntimeException e) {
            heartbeat.cancel(false);
            close.run();
            throw e;
        }
        return emitter;
    }

    public static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(JsonUtils.toJson(data)));
        } catch (Exception e) {
            throw new CancellationException("AI 连接已断开");
        }
    }
}
