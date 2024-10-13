package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.config.SimpleApiConfigProperties;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import com.fugary.simple.api.tasks.ProjectAutoImportInvoker;
import com.fugary.simple.api.tasks.SimpleAutoTask;
import com.fugary.simple.api.tasks.SimpleTaskManager;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.project.ApiProjectTaskVo;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Create date 2024/9/27<br>
 *
 * @author gary.fu
 */
@RestController
@RequestMapping("/admin/tasks")
public class ApiProjectTaskController {

    @Autowired
    private ApiProjectTaskService apiProjectTaskService;

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ProjectAutoImportInvoker projectAutoImportInvoker;

    @Autowired
    private SimpleApiConfigProperties simpleApiConfigProperties;

    @Autowired
    private SimpleTaskManager simpleTaskManager;

    @GetMapping
    public SimpleResult<List<ApiProjectTask>> search(@ModelAttribute ProjectQueryVo queryVo) {
        Page<ApiProjectTask> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        String userName = SecurityUtils.getUserName(queryVo.getUserName());
        QueryWrapper<ApiProjectTask> queryWrapper = Wrappers.<ApiProjectTask>query()
                .eq(queryVo.getProjectId() != null, "project_id", queryVo.getProjectId())
                .like(StringUtils.isNotBlank(keyword), "task_name", keyword)
                .exists("select 1 from t_api_project p where p.id = t_api_project_task.project_id and p.user_name={0}", userName);
        Page<ApiProjectTask> pageResult = apiProjectTaskService.page(page, queryWrapper);
        Map<Integer, ApiProject> projectMap = apiProjectService.list(Wrappers.<ApiProject>query().in("id",
                        pageResult.getRecords().stream().map(ApiProjectTask::getProjectId).collect(Collectors.toList())))
                .stream().collect(Collectors.toMap(ApiProject::getId, Function.identity()));
        List<ApiProjectTask> apiTasks = pageResult.getRecords().stream().map(task -> {
            String taskId = SimpleTaskUtils.getTaskId(task.getId());
            ApiProjectTaskVo taskVo = SimpleModelUtils.copy(task, ApiProjectTaskVo.class);
            taskVo.setTaskId(taskId);
            if (ApiDocConstants.PROJECT_TASK_TYPE_AUTO.equals(task.getTaskType())) {
                String scheduleStatus = ApiDocConstants.TASK_STATUS_STOPPED;
                SimpleAutoTask<?> autoTask = simpleTaskManager.getAutoTask(taskId);
                if (autoTask != null) {
                    ScheduledTask scheduledTask = simpleTaskManager.getScheduledTask(taskId);
                    if (scheduledTask != null) {
                        scheduleStatus = ApiDocConstants.TASK_STATUS_STARTED;
                    }
                    taskVo.setTaskStatus(SimpleTaskUtils.calcTaskStatus(scheduledTask, autoTask.getTaskWrapper()));
                }
                taskVo.setScheduleStatus(scheduleStatus);
            }
            taskVo.setProject(projectMap.get(taskVo.getProjectId()));
            return taskVo;
        }).collect(Collectors.toList());
        pageResult.setRecords(apiTasks);
        return SimpleResultUtils.createSimpleResult(pageResult);
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiProjectTask> get(@PathVariable("id") Integer id) {
        return SimpleResultUtils.createSimpleResult(apiProjectTaskService.getById(id));
    }

    @DeleteMapping("/{id}")
    public SimpleResult<Boolean> remove(@PathVariable("id") Integer id) {
        ApiProjectTask apiTask = apiProjectTaskService.getById(id);
        if (!validateOperateUser(apiTask)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        if (apiTask != null) {
            simpleTaskManager.removeAutoTask(SimpleTaskUtils.getTaskId(apiTask.getId()));
        }
        return SimpleResultUtils.createSimpleResult(apiProjectTaskService.removeById(id));
    }

    @PostMapping
    public SimpleResult<Boolean> save(@RequestBody ApiProjectTask apiTask) {
        if (!validateOperateUser(apiTask)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        boolean saved = apiProjectTaskService.saveOrUpdate(SimpleModelUtils.addAuditInfo(apiTask));
        if (apiTask.getId() != null) {
            if (!apiTask.isEnabled()) {
                simpleTaskManager.removeAutoTask(SimpleTaskUtils.getTaskId(apiTask.getId()));
            } else {
                simpleTaskManager.addOrUpdateAutoTask(SimpleTaskUtils.projectTask2SimpleTask(apiTask,
                        projectAutoImportInvoker, simpleApiConfigProperties));
            }
        }
        return SimpleResultUtils.createSimpleResult(saved);
    }

    @PostMapping("/trigger/{id}")
    public SimpleResult<Boolean> triggerNow(@PathVariable("id") Integer id) {
        ApiProjectTask apiTask = apiProjectTaskService.getById(id);
        if (!validateOperateUser(apiTask)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        if (apiTask != null) {
            SimpleTaskUtils.projectTask2SimpleTask(apiTask,
                    projectAutoImportInvoker, simpleApiConfigProperties).triggerNow();
        }
        return SimpleResultUtils.createSimpleResult(true);
    }

    protected boolean validateOperateUser(ApiProjectTask projectTask) {
        if (projectTask != null) {
            return apiProjectService.validateUserProject(projectTask.getProjectId());
        }
        return true;
    }

}
