package com.fugary.simple.api.web.controllers.admin;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.tasks.SimpleAutoTask;
import com.fugary.simple.api.tasks.SimpleTaskManager;
import com.fugary.simple.api.tasks.SimpleTaskWrapper;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.task.SimpleTaskVo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Slf4j
@RestController
@RequestMapping("/admin/simple-tasks")
public class SimpleTaskController {

    @Autowired
    private SimpleTaskManager simpleTaskManager;

    @Autowired
    private ApiProjectService apiProjectService;

    @GetMapping
    public SimpleResult<List<SimpleTaskVo>> list() {
        List<SimpleAutoTask<?>> autoTasks = simpleTaskManager.loadAutoTasks();
        List<SimpleTaskVo> taskList = autoTasks.stream().map(this::getSimpleTaskVo).collect(Collectors.toList());
        return SimpleResultUtils.createSimpleResult(taskList);
    }

    @SneakyThrows
    protected SimpleTaskVo getSimpleTaskVo(SimpleAutoTask<?> autoTask) {
        SimpleTaskWrapper<?> taskWrapper = autoTask.getTaskWrapper();
        SimpleTaskVo taskVo = new SimpleTaskVo();
        taskVo.setTaskId(taskWrapper.getTaskId());
        taskVo.setTaskName(taskWrapper.getTaskName());
        if (taskWrapper.getData() instanceof ApiProjectTask) {
            ApiProjectTask apiProjectTask = (ApiProjectTask) taskWrapper.getData();
            taskVo.setProjectId(apiProjectTask.getProjectId());
            ApiProject project = apiProjectService.getById(apiProjectTask.getProjectId());
            if (project != null) {
                taskVo.setProjectName(project.getProjectName());
                taskVo.setUserName(project.getUserName());
            }
        }
        ScheduledTask scheduledTask = simpleTaskManager.getScheduledTask(taskVo.getTaskId());
        String status = calcTaskStatus(scheduledTask, taskWrapper);
        taskVo.setTaskStatus(status);
        return taskVo;
    }

    protected String calcTaskStatus(ScheduledTask scheduledTask, SimpleTaskWrapper<?> taskWrapper) throws IllegalAccessException {
        String status = ApiDocConstants.TASK_STATUS_STOPPED;
        if (scheduledTask != null) {
            status = ApiDocConstants.TASK_STATUS_STARTED;
            Field futureField = FieldUtils.getField(scheduledTask.getClass(), "future", true);
            ScheduledFuture<?> scheduledFuture = (ScheduledFuture<?>) futureField.get(scheduledTask);
            if (scheduledFuture.isCancelled()) {
                status = ApiDocConstants.TASK_STATUS_STOPPED;
            } else {
                if (StringUtils.isNotBlank(taskWrapper.getRunningStatus())) {
                    status = taskWrapper.getRunningStatus();
                }
            }
        }
        return status;
    }

    @GetMapping("/{taskId}")
    public SimpleResult<SimpleTaskVo> getById(@PathVariable(name = "taskId") String taskId) {
        SimpleAutoTask<?> autoTask = simpleTaskManager.getAutoTask(taskId);
        if (autoTask != null) {
            return SimpleResultUtils.createSimpleResult(getSimpleTaskVo(autoTask));
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
    }
}
