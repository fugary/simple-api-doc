package com.fugary.simple.api.web.controllers.admin;

import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.tasks.SimpleAutoTask;
import com.fugary.simple.api.tasks.SimpleTaskManager;
import com.fugary.simple.api.tasks.SimpleTaskWrapper;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.task.SimpleTaskVo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
        ApiProjectTask apiProjectTask = null;
        ApiProject project = null;
        if (taskWrapper.getData() instanceof ApiProjectTask) {
            apiProjectTask = (ApiProjectTask) taskWrapper.getData();
            project = apiProjectService.getById(apiProjectTask.getProjectId());
        }
        ScheduledTask scheduledTask = simpleTaskManager.getScheduledTask(taskWrapper.getTaskId());
        return SimpleTaskUtils.getSimpleTaskVo(scheduledTask, autoTask, project);
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
