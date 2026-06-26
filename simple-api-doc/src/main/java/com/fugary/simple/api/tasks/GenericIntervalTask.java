package com.fugary.simple.api.tasks;

import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import lombok.Getter;
import org.springframework.scheduling.config.FixedRateTask;

/**
 * 通用定频执行任务
 */
@Getter
public class GenericIntervalTask<T> extends FixedRateTask implements SimpleAutoTask<T> {

    private final SimpleTaskWrapper<T> taskWrapper;

    public GenericIntervalTask(SimpleTaskWrapper<T> taskWrapper, Runnable runnable, long interval, long initialDelay) {
        super(() -> SimpleTaskUtils.executeTask(taskWrapper, runnable), interval, initialDelay);
        this.taskWrapper = taskWrapper;
    }

    public GenericIntervalTask(SimpleTaskWrapper<T> taskWrapper, Runnable runnable, long interval) {
        this(taskWrapper, runnable, interval, interval);
    }

    @Override
    public void triggerNow() {
        getRunnable().run();
    }
}
