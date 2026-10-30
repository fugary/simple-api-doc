package com.fugary.simple.api.service.ai.provider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSseSupportTest {
    private final AiSseSupport support = new AiSseSupport();
    private final ThreadPoolTaskScheduler scheduler = mock(ThreadPoolTaskScheduler.class);
    private final ScheduledFuture<?> heartbeat = mock(ScheduledFuture.class);
    private final AtomicReference<Runnable> worker = new AtomicReference<>();

    AiSseSupportTest() {
        ReflectionTestUtils.setField(support, "scheduler", scheduler);
        doReturn(heartbeat).when(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(5000L));
        doAnswer(call -> { worker.set(call.getArgument(0)); return null; }).when(scheduler).execute(any(Runnable.class));
    }

    @AfterEach
    void clearContext() { RequestContextHolder.resetRequestAttributes(); }

    @Test
    void timeoutAbortsUpstreamOnceAndCleansHeartbeatAndRequestContext() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletRequestAttributes attributes = new ServletRequestAttributes(new MockHttpServletRequest(), response);
        RequestContextHolder.setRequestAttributes(attributes);
        AtomicInteger aborted = new AtomicInteger();
        AtomicReference<AiStreamSession> session = new AtomicReference<>();
        support.start((emitter, stream) -> {
            session.set(stream);
            assertThat(RequestContextHolder.getRequestAttributes()).isSameAs(attributes);
            stream.bind(aborted::incrementAndGet);
            ((Runnable) ReflectionTestUtils.getField(emitter, "timeoutCallback")).run();
            stream.check();
        });
        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
        RequestContextHolder.resetRequestAttributes();
        worker.get().run();
        assertThat(session.get().isCancelled()).isTrue();
        assertThat(aborted).hasValue(1);
        assertThat(RequestContextHolder.getRequestAttributes()).isNull();
        verify(heartbeat, atLeastOnce()).cancel(false);
    }

    @Test
    void serviceFailureProducesErrorAndReleasesResources() {
        SseEmitter emitter = support.start((target, stream) -> { throw new IllegalStateException("upstream failed"); });
        worker.get().run();
        @SuppressWarnings("unchecked")
        Set<ResponseBodyEmitter.DataWithMediaType> data = (Set<ResponseBodyEmitter.DataWithMediaType>)
                ReflectionTestUtils.getField(emitter, "earlySendAttempts");
        assertThat(data.stream().map(item -> String.valueOf(item.getData())).collect(Collectors.joining()))
                .contains("event:error", "upstream failed").doesNotContain("event:finish");
        verify(heartbeat, atLeastOnce()).cancel(false);
        assertThat(RequestContextHolder.getRequestAttributes()).isNull();
    }

    @Test
    void failedBrowserWriteCancelsUpstreamAndRetainsPartialAnswer() {
        AtomicInteger aborted = new AtomicInteger();
        AiStreamSession stream = new AiStreamSession(text -> { throw new CancellationException("closed"); });
        stream.bind(aborted::incrementAndGet);
        assertThatThrownBy(() -> stream.text("partial")).isInstanceOf(CancellationException.class);
        assertThat(stream.isCancelled()).isTrue();
        assertThat(stream.getText()).isEqualTo("partial");
        stream.cancel();
        assertThat(aborted).hasValue(1);
    }

    @Test
    void cancelledBeforeRequestBindingNeverStartsUpstream() {
        AiStreamSession stream = new AiStreamSession(text -> {});
        AtomicInteger aborted = new AtomicInteger();
        stream.cancel();
        assertThatThrownBy(() -> stream.bind(aborted::incrementAndGet)).isInstanceOf(CancellationException.class);
        assertThat(aborted).hasValue(1);
    }
}
