package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.config.SimpleApiConfigProperties;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import com.fugary.simple.api.tasks.ProjectAutoImportInvoker;
import com.fugary.simple.api.tasks.SimpleTaskManager;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        QueryWrapper<ApiProjectTask> queryWrapper = Wrappers.<ApiProjectTask>query()
                .eq("project_id", queryVo.getProjectId())
                .like(StringUtils.isNotBlank(keyword), "task_name", keyword);
        return SimpleResultUtils.createSimpleResult(apiProjectTaskService.page(page, queryWrapper));
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
