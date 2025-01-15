package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.config.SimpleApiConfigProperties;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiGroup;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import com.fugary.simple.api.service.apidoc.ApiUserService;
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
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.fugary.simple.api.utils.security.SecurityUtils.getLoginUser;

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
    private ApiGroupService apiGroupService;

    @Autowired
    private ApiUserService apiUserService;

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
        String groupCode = StringUtils.trimToEmpty(queryVo.getGroupCode());
        if (StringUtils.isNotBlank(groupCode)
                && !apiGroupService.checkGroupAccess(getLoginUser(), groupCode, ApiGroupAuthority.READABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        QueryWrapper<ApiProjectTask> queryWrapper = Wrappers.<ApiProjectTask>query()
                .eq(queryVo.getProjectId() != null, "project_id", queryVo.getProjectId())
                .like(StringUtils.isNotBlank(keyword), "task_name", keyword);
        addGroupCodeQuery(queryVo, queryWrapper, userName);
        Page<ApiProjectTask> pageResult = apiProjectTaskService.page(page, queryWrapper);
        Map<Integer, ApiProject> projectMap = apiProjectService.list(Wrappers.<ApiProject>query()
                        .in(!pageResult.getRecords().isEmpty(), "id",
                                pageResult.getRecords().stream().map(ApiProjectTask::getProjectId)
                                .collect(Collectors.toList())))
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

    /**
     * 添加项目查询sql
     *
     * @param queryVo
     * @param queryWrapper
     * @param userName
     */
    protected void addGroupCodeQuery(ProjectQueryVo queryVo, QueryWrapper<ApiProjectTask> queryWrapper, String userName) {
        if (StringUtils.isNotBlank(queryVo.getGroupCode())) {
            queryWrapper.exists("select 1 from t_api_project p where p.id = t_api_project_task.project_id and p.group_code={0}", queryVo.getGroupCode());
        } else {
            ApiUserVo apiUser = apiUserService.loadUser(userName);
            String groupCodesStr = apiUser.getGroups().stream().map(ApiGroup::getGroupCode)
                    .filter(StringUtils::isNotBlank).collect(Collectors.joining("','"));
            queryWrapper.and(wrapper -> wrapper.exists(StringUtils.isNotBlank(groupCodesStr), "select 1 from t_api_project p where p.id = t_api_project_task.project_id and p.group_code in ('" + groupCodesStr + "')")
                    .or().exists("select 1 from t_api_project p where p.id = t_api_project_task.project_id and p.user_name={0} and (p.group_code is null or p.group_code = '')", userName));
        }
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
        if (!validateOperateUser(apiTask)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        boolean saved = apiProjectTaskService.saveOrUpdate(SimpleModelUtils.addAuditInfo(apiTask));
        if (apiTask.getId() != null) {
            if (ApiDocConstants.PROJECT_TASK_TYPE_MANUAL.equals(apiTask.getTaskType())
                    || !apiTask.isEnabled()) {
                simpleTaskManager.removeAutoTask(SimpleTaskUtils.getTaskId(apiTask.getId()));
            }
            if (apiTask.isEnabled() && ApiDocConstants.PROJECT_TASK_TYPE_AUTO.equals(apiTask.getTaskType())) {
                simpleTaskManager.addOrUpdateAutoTask(SimpleTaskUtils.projectTask2SimpleTask(apiTask,
                        projectAutoImportInvoker, simpleApiConfigProperties));
            }
        }
        return SimpleResultUtils.createSimpleResult(saved);
    }

    @PostMapping("/trigger/{id}")
    public SimpleResult<ApiProject> triggerNow(@PathVariable("id") Integer id) {
        ApiProjectTask apiTask = apiProjectTaskService.getById(id);
        if (apiTask == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!validateOperateUser(apiTask)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return projectAutoImportInvoker.importProject(apiTask);
    }

    protected boolean validateOperateUser(ApiProjectTask projectTask) {
        if (projectTask != null) {
            return apiProjectService.validateUserProject(projectTask.getProjectId());
        }
        return true;
    }

}
