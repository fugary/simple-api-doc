package com.fugary.simple.api.tasks;

import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import lombok.Getter;
import org.springframework.scheduling.config.CronTask;

/**
 * 通用 Cron 定时任务
 */
@Getter
public class GenericCronTask<T> extends CronTask implements SimpleAutoTask<T> {

    private final SimpleTaskWrapper<T> taskWrapper;

    public GenericCronTask(SimpleTaskWrapper<T> taskWrapper, Runnable runnable, String expression) {
        super(() -> SimpleTaskUtils.executeTask(taskWrapper, runnable), expression);
        this.taskWrapper = taskWrapper;
    }

    @Override
    public void triggerNow() {
        getRunnable().run();
    }
}
