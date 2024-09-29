package com.fugary.simple.api.tasks;

import org.springframework.scheduling.config.ScheduledTask;

import java.util.List;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
public interface SimpleTaskManager {

    SimpleAutoTask<?> getAutoTask(String taskId);

    ScheduledTask getScheduledTask(String taskId);

    ScheduledTask startAutoTask(String taskId);

    ScheduledTask stopAutoTask(String taskId);

    ScheduledTask addOrUpdateAutoTask(SimpleAutoTask<?> task);

    void removeAutoTask(String taskId);

    List<SimpleAutoTask<?>> loadAutoTasks();
}
