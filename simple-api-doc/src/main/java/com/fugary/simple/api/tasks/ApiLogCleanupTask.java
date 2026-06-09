package com.fugary.simple.api.tasks;

import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import lombok.Getter;
import org.springframework.scheduling.config.CronTask;

/**
 * Cron task for API log cleanup.
 *
 * @author gary.fu
 */
@Getter
public class ApiLogCleanupTask extends CronTask implements SimpleAutoTask<String> {

    public static final String TASK_ID = "api-log-cleanup";

    public static final String TASK_NAME = "API log cleanup";

    public static final String TASK_CRON = "0 0 3 * * ?";

    private final SimpleTaskWrapper<String> taskWrapper;

    public ApiLogCleanupTask(SimpleTaskWrapper<String> taskWrapper, Runnable runnable) {
        super(() -> SimpleTaskUtils.executeTask(taskWrapper, runnable), TASK_CRON);
        this.taskWrapper = taskWrapper;
    }

    @Override
    public void triggerNow() {
        getRunnable().run();
    }
}
