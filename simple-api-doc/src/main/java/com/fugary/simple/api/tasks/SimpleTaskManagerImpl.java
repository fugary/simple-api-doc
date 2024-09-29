package com.fugary.simple.api.tasks;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Component
public class SimpleTaskManagerImpl implements SimpleTaskManager {

    @Autowired
    private ScheduledTaskRegistrar scheduledTaskRegistrar;

    @Override
    public SimpleAutoTask<?> getAutoTask(String taskId) {
        List<IntervalTask> fixedTasks = scheduledTaskRegistrar.getFixedRateTaskList();
        List<CronTask> cronTasks = scheduledTaskRegistrar.getCronTaskList();
        SimpleAutoTask<?> autoTask = getSimpleTask(fixedTasks, taskId);
        if (autoTask == null) {
            autoTask = getSimpleTask(cronTasks, taskId);
        }
        return autoTask;
    }

    @Override
    public ScheduledTask getScheduledTask(String taskId) {
        Set<ScheduledTask> scheduledTasks = scheduledTaskRegistrar.getScheduledTasks();
        return getScheduledTask(scheduledTasks, taskId);
    }

    @Override
    public ScheduledTask startAutoTask(String taskId) {
        Set<ScheduledTask> scheduledTasks = scheduledTaskRegistrar.getScheduledTasks();
        ScheduledTask scheduledTask = getScheduledTask(scheduledTasks, taskId);
        if (scheduledTask != null) {
            return addOrUpdateAutoTask((SimpleAutoTask<?>) scheduledTask.getTask());
        }
        return null;
    }

    @Override
    public ScheduledTask stopAutoTask(String taskId) {
        Set<ScheduledTask> scheduledTasks = scheduledTaskRegistrar.getScheduledTasks();
        ScheduledTask scheduledTask = getScheduledTask(scheduledTasks, taskId);
        if (scheduledTask != null) {
            scheduledTask.cancel();
        }
        return scheduledTask;
    }

    @Override
    public ScheduledTask addOrUpdateAutoTask(SimpleAutoTask<?> newTask) {
        removeAutoTask(newTask.getTaskId());
        if (newTask instanceof FixedRateTask) {
            return scheduledTaskRegistrar.scheduleFixedRateTask((FixedRateTask) newTask);
        } else if (newTask instanceof CronTask) {
            return scheduledTaskRegistrar.scheduleCronTask((CronTask) newTask);
        }
        return null;
    }

    protected ScheduledTask getScheduledTask(Set<ScheduledTask> scheduledTasks, String taskId) {
        return scheduledTasks.stream().filter(task -> task.getTask() instanceof SimpleAutoTask
                        && StringUtils.equals(((SimpleAutoTask) task.getTask()).getTaskId(), taskId))
                .findFirst().orElseGet(null);
    }

    protected SimpleAutoTask<?> getSimpleTask(List<? extends Task> tasks, String taskId) {
        return (SimpleAutoTask<?>) tasks.stream().filter(task -> task instanceof SimpleAutoTask
                        && StringUtils.equals(((SimpleAutoTask) task).getTaskId(), taskId))
                .findFirst().orElseGet(null);
    }

    protected List<SimpleAutoTask<?>> getSimpleTasks(List<? extends Task> tasks) {
        return tasks.stream().filter(task -> task instanceof SimpleAutoTask).map(task -> (SimpleAutoTask<?>) task)
                .collect(Collectors.toList());
    }

    @Override
    public void removeAutoTask(String taskId) {
        Set<ScheduledTask> scheduledTasks = scheduledTaskRegistrar.getScheduledTasks();
        ScheduledTask scheduledTask = getScheduledTask(scheduledTasks, taskId);
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTasks.remove(scheduledTask);
        }
        List<IntervalTask> fixedTasks = scheduledTaskRegistrar.getFixedRateTaskList();
        List<CronTask> cronTasks = scheduledTaskRegistrar.getCronTaskList();
        SimpleAutoTask<?> autoTask = getSimpleTask(fixedTasks, taskId);
        if (autoTask != null) {
            fixedTasks.remove(autoTask);
        }
        autoTask = getSimpleTask(cronTasks, taskId);
        if (autoTask != null) {
            cronTasks.remove(autoTask);
        }
    }

    @Override
    public List<SimpleAutoTask<?>> loadAutoTasks() {
        List<IntervalTask> fixedTasks = scheduledTaskRegistrar.getFixedRateTaskList();
        List<CronTask> cronTasks = scheduledTaskRegistrar.getCronTaskList();
        List<SimpleAutoTask<?>> simpleTasks = getSimpleTasks(fixedTasks);
        simpleTasks.addAll(getSimpleTasks(cronTasks));
        return simpleTasks;
    }
}
