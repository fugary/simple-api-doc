package com.fugary.simple.api.web.controllers.admin;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import com.fugary.simple.api.tasks.SimpleAutoTask;
import com.fugary.simple.api.tasks.SimpleTaskManager;
import com.fugary.simple.api.tasks.SimpleTaskWrapper;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import com.fugary.simple.api.web.vo.task.SimpleTaskVo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.web.bind.annotation.*;

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

    @Autowired
    private ApiProjectTaskService apiProjectTaskService;

    @GetMapping
    public SimpleResult<List<SimpleTaskVo>> list(@ModelAttribute ProjectQueryVo queryVo) {
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        List<SimpleAutoTask<?>> autoTasks = simpleTaskManager.loadAutoTasks();
        List<SimpleTaskVo> taskList = autoTasks.stream()
                .map(this::getSimpleTaskVo)
                .filter(taskVo -> {
                    boolean result = true;
                    if (StringUtils.isNotBlank(keyword)) {
                        result = StringUtils.containsIgnoreCase(taskVo.getTaskName(), keyword)
                                || StringUtils.containsIgnoreCase(taskVo.getProjectName(), keyword)
                                || StringUtils.containsIgnoreCase(taskVo.getDescription(), keyword);
                    }
                    if (result && StringUtils.isNotBlank(queryVo.getUserName())) {
                        result = StringUtils.equals(taskVo.getUserName(), queryVo.getUserName());
                    }
                    return result;
                })
                .collect(Collectors.toList());
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

    @DeleteMapping("/{taskId}")
    public SimpleResult<Boolean> removeTask(@PathVariable(name = "taskId") String taskId) {
        simpleTaskManager.removeAutoTask(taskId);
        return SimpleResultUtils.createSimpleResult(true);
    }

    @DeleteMapping("/{taskId}/{tid}")
    public SimpleResult<Boolean> removeAndDisable(@PathVariable(name = "taskId") String taskId,
                                                  @PathVariable(name = "tid") Integer tid) {
        ApiProjectTask apiTask = apiProjectTaskService.getById(tid);
        ApiProject apiProject = null;
        if (apiTask == null || (apiProject = apiProjectService.getById(apiTask.getProjectId())) == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!SecurityUtils.validateUserUpdate(apiProject.getUserName())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        simpleTaskManager.removeAutoTask(taskId);
        apiTask.setStatus(ApiDocConstants.STATUS_DISABLED);
        return SimpleResultUtils.createSimpleResult(apiProjectTaskService.updateById(apiTask));
    }
}
