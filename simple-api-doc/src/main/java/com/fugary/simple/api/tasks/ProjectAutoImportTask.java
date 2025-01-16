package com.fugary.simple.api.tasks;

import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.config.FixedRateTask;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Slf4j
@Setter
@Getter
public class ProjectAutoImportTask extends FixedRateTask implements SimpleAutoTask<ApiProjectTask> {

    private SimpleTaskWrapper<ApiProjectTask> projectTaskWrapper;

    public ProjectAutoImportTask(SimpleTaskWrapper<ApiProjectTask> projectTaskWrapper, Runnable runnable, long delay) {
        super(() -> SimpleTaskUtils.executeTask(projectTaskWrapper, runnable), getScheduleRate(projectTaskWrapper), delay < 0 ? getScheduleRate(projectTaskWrapper) : delay);
        this.projectTaskWrapper = projectTaskWrapper;
    }

    private static int getScheduleRate(SimpleTaskWrapper<ApiProjectTask> projectTaskWrapper) {
        return projectTaskWrapper.getData().getScheduleRate() * 1000;
    }

    @Override
    public SimpleTaskWrapper<ApiProjectTask> getTaskWrapper() {
        return projectTaskWrapper;
    }

    @Override
    public void triggerNow() {
        getRunnable().run();
    }
}
