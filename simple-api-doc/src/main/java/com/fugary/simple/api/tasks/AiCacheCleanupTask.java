package com.fugary.simple.api.tasks;

import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import lombok.Getter;
import org.springframework.scheduling.config.CronTask;

/**
 * Cron task for AI cache cleanup.
 */
@Getter
public class AiCacheCleanupTask extends CronTask implements SimpleAutoTask<String> {

    public static final String TASK_ID = "ai-cache-cleanup";

    public static final String TASK_NAME = "AI cache cleanup";

    public static final String TASK_CRON = "0 0 4 * * ?";

    private final SimpleTaskWrapper<String> taskWrapper;

    public AiCacheCleanupTask(SimpleTaskWrapper<String> taskWrapper, Runnable runnable) {
        super(() -> SimpleTaskUtils.executeTask(taskWrapper, runnable), TASK_CRON);
        this.taskWrapper = taskWrapper;
    }

    @Override
    public void triggerNow() {
        getRunnable().run();
    }
}
