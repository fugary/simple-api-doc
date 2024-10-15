package com.fugary.simple.api.utils.task;

import com.fugary.simple.api.config.SimpleApiConfigProperties;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.tasks.ProjectAutoImportInvoker;
import com.fugary.simple.api.tasks.ProjectAutoImportTask;
import com.fugary.simple.api.tasks.SimpleAutoTask;
import com.fugary.simple.api.tasks.SimpleTaskWrapper;
import com.fugary.simple.api.web.vo.task.SimpleTaskVo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTask;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SimpleTaskUtils {

    /**
     * 包装任务执行
     *
     * @param projectTaskWrapper
     * @param runnable
     */
    public static void executeTask(SimpleTaskWrapper<?> projectTaskWrapper, Runnable runnable) {
        long start = System.currentTimeMillis();
        try {
            log.info("开始任务:{}", projectTaskWrapper.getTaskName());
            projectTaskWrapper.setRunningStatus(ApiDocConstants.TASK_STATUS_RUNNING);
            projectTaskWrapper.setLastExecDate(new Date());
            runnable.run();
            log.info("结束任务:{}，耗时：{}", projectTaskWrapper.getTaskName(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("任务执行异常", e);
            projectTaskWrapper.setRunningStatus(ApiDocConstants.TASK_STATUS_ERROR);
        } finally {
            projectTaskWrapper.setRunningStatus(ApiDocConstants.TASK_STATUS_DONE);
        }
    }

    /**
     * 计算TaskId
     *
     * @param id
     * @return
     */
    public static String getTaskId(Integer id) {
        return ApiDocConstants.AUTO_IMPORT_TASK_PREFIX + id;
    }

    /**
     * 计算ProjectTask为SimpleTask
     *
     * @param projectTask
     */
    public static SimpleAutoTask<ApiProjectTask> projectTask2SimpleTask(ApiProjectTask projectTask, ProjectAutoImportInvoker projectAutoImportInvoker, SimpleApiConfigProperties simpleApiConfigProperties) {
        SimpleTaskWrapper<ApiProjectTask> projectTaskWrapper = new SimpleTaskWrapper<>(
                SimpleTaskUtils.getTaskId(projectTask.getId()), projectTask.getTaskName(), projectTask);
        return new ProjectAutoImportTask(projectTaskWrapper,
                () -> projectAutoImportInvoker.importProject(projectTask),
                simpleApiConfigProperties.getDefaultTaskDelay());
    }

    /**
     * 计算任务状态
     *
     * @param scheduledTask
     * @param taskWrapper
     * @return
     * @throws IllegalAccessException
     */
    public static String calcTaskStatus(ScheduledTask scheduledTask, SimpleTaskWrapper<?> taskWrapper) {
        String status = ApiDocConstants.TASK_STATUS_STOPPED;
        if (scheduledTask != null && taskWrapper != null) {
            status = ApiDocConstants.TASK_STATUS_STARTED;
            Field futureField = FieldUtils.getField(scheduledTask.getClass(), "future", true);
            ScheduledFuture<?> scheduledFuture = null;
            try {
                scheduledFuture = (ScheduledFuture<?>) futureField.get(scheduledTask);
            } catch (IllegalAccessException e) {
                log.error("无法获取ScheduledFuture", e);
            }
            if (scheduledFuture != null && scheduledFuture.isCancelled()) {
                status = ApiDocConstants.TASK_STATUS_STOPPED;
            } else {
                if (StringUtils.isNotBlank(taskWrapper.getRunningStatus())) {
                    status = taskWrapper.getRunningStatus();
                }
            }
        }
        return status;
    }

    /**
     * 计算TaskVo
     *
     * @param scheduledTask
     * @param autoTask
     * @param project
     * @return
     */
    public static SimpleTaskVo getSimpleTaskVo(ScheduledTask scheduledTask, SimpleAutoTask<?> autoTask, ApiProject project) {
        SimpleTaskWrapper<?> taskWrapper = autoTask.getTaskWrapper();
        SimpleTaskVo taskVo = new SimpleTaskVo();
        taskVo.setTaskId(taskWrapper.getTaskId());
        taskVo.setTaskName(taskWrapper.getTaskName());
        taskVo.setLastExecDate(taskWrapper.getLastExecDate());
        if (taskWrapper.getData() instanceof ApiProjectTask) {
            ApiProjectTask apiProjectTask = (ApiProjectTask) taskWrapper.getData();
            taskVo.setProjectId(apiProjectTask.getProjectId());
            taskVo.setTid(apiProjectTask.getId());
            if (project != null) {
                taskVo.setProjectName(project.getProjectName());
                taskVo.setProjectCode(project.getProjectCode());
                taskVo.setUserName(project.getUserName());
            }
        }
        if (autoTask instanceof CronTask) {
            CronTask cronTask = (CronTask) autoTask;
            taskVo.setType(ApiDocConstants.SIMPLE_TASK_TYPE_CRON);
            taskVo.setCron(cronTask.getExpression());
        } else if (autoTask instanceof FixedRateTask) {
            FixedRateTask fixedRateTask = (FixedRateTask) autoTask;
            taskVo.setType(ApiDocConstants.SIMPLE_TASK_TYPE_FIXED);
            taskVo.setTriggerRate(fixedRateTask.getInterval());
            taskVo.setTriggerDelay(fixedRateTask.getInitialDelay());
        }
        String status = SimpleTaskUtils.calcTaskStatus(scheduledTask, taskWrapper);
        taskVo.setTaskStatus(status);
        return taskVo;
    }
}
