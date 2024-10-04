package com.fugary.simple.api.tasks;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.*;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Slf4j
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
    public ScheduledTask addOrUpdateAutoTask(SimpleAutoTask<?> newTask) {
        removeAutoTask(newTask.getTaskId());
        ScheduledTask scheduledTask = null;
        if (newTask instanceof FixedRateTask) {
            scheduledTask = scheduledTaskRegistrar.scheduleFixedRateTask((FixedRateTask) newTask);
        } else if (newTask instanceof CronTask) {
            scheduledTask = scheduledTaskRegistrar.scheduleCronTask((CronTask) newTask);
        }
        if (scheduledTask != null) {
            log.info("开始定时任务:{}, {}", newTask.getTaskId(), scheduledTask);
            addScheduledTask(scheduledTask);
        }
        return scheduledTask;
    }

    protected void removeScheduledTask(ScheduledTask scheduledTask) {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            Field scheduleTasksField = FieldUtils.getField(ScheduledTaskRegistrar.class, "scheduledTasks", true);
            Field fixedRateTasksField = FieldUtils.getField(ScheduledTaskRegistrar.class, "fixedRateTasks", true);
            Field cronTasksField = FieldUtils.getField(ScheduledTaskRegistrar.class, "cronTasks", true);
            if (scheduleTasksField != null) {
                try {
                    Set<ScheduledTask> scheduledTasks = (Set<ScheduledTask>) FieldUtils.readField(scheduleTasksField, scheduledTaskRegistrar, true);
                    List<IntervalTask> fixedTasks = (List<IntervalTask>) FieldUtils.readField(fixedRateTasksField, scheduledTaskRegistrar, true);
                    List<CronTask> cronTasks = (List<CronTask>) FieldUtils.readField(cronTasksField, scheduledTaskRegistrar, true);
                    if (scheduledTasks != null) {
                        scheduledTasks.remove(scheduledTask);
                        if (fixedTasks != null) {
                            fixedTasks.remove(scheduledTask.getTask());
                        }
                        if (cronTasks != null) {
                            cronTasks.remove(scheduledTask.getTask());
                        }
                    }
                } catch (IllegalAccessException e) {
                    log.error("无法移除ScheduledTask", e);
                }
            }
        }
    }

    protected void addScheduledTask(ScheduledTask scheduledTask) {
        try {
            MethodUtils.invokeMethod(scheduledTaskRegistrar, true, "addScheduledTask", scheduledTask);
            if (scheduledTask.getTask() instanceof IntervalTask) {
                scheduledTaskRegistrar.addFixedRateTask((IntervalTask) scheduledTask.getTask());
            } else if (scheduledTask.getTask() instanceof CronTask) {
                scheduledTaskRegistrar.addCronTask((CronTask) scheduledTask.getTask());
            }
        } catch (Exception e) {
            log.error("无法添加ScheduledTask到ScheduledTaskRegistrar中", e);
        }
    }

    protected ScheduledTask getScheduledTask(Set<ScheduledTask> scheduledTasks, String taskId) {
        return scheduledTasks.stream().filter(task -> task.getTask() instanceof SimpleAutoTask
                        && StringUtils.equals(((SimpleAutoTask) task.getTask()).getTaskId(), taskId))
                .findFirst().orElse(null);
    }

    protected SimpleAutoTask<?> getSimpleTask(List<? extends Task> tasks, String taskId) {
        return (SimpleAutoTask<?>) tasks.stream().filter(task -> task instanceof SimpleAutoTask
                        && StringUtils.equals(((SimpleAutoTask) task).getTaskId(), taskId))
                .findFirst().orElse(null);
    }

    protected List<SimpleAutoTask<?>> getSimpleTasks(List<? extends Task> tasks) {
        return tasks.stream().filter(task -> task instanceof SimpleAutoTask).map(task -> (SimpleAutoTask<?>) task)
                .collect(Collectors.toList());
    }

    @Override
    public void removeAutoTask(String taskId) {
        Set<ScheduledTask> scheduledTasks = scheduledTaskRegistrar.getScheduledTasks();
        ScheduledTask scheduledTask = getScheduledTask(scheduledTasks, taskId);
        log.info("移除定时任务:{}, {}", taskId, scheduledTask);
        removeScheduledTask(scheduledTask);
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
