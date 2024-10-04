package com.fugary.simple.api.tasks;

import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import org.springframework.scheduling.config.FixedRateTask;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
public class SimpleTestTask extends FixedRateTask implements SimpleAutoTask<String> {

    private SimpleTaskWrapper<String> taskWrapper;

    /**
     * Create a new {@code FixedRateTask}.
     *
     * @param runnable     the underlying task to execute
     */
    public SimpleTestTask(SimpleTaskWrapper<String> taskWrapper, Runnable runnable) {
        super(() -> SimpleTaskUtils.executeTask(taskWrapper, runnable), 60000, 2000);
        this.taskWrapper = taskWrapper;
    }

    @Override
    public SimpleTaskWrapper<String> getTaskWrapper() {
        return taskWrapper;
    }

    @Override
    public void triggerNow() {
        getRunnable().run();
    }
}
