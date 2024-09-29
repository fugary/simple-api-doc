package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
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
    public SimpleResult remove(@PathVariable("id") Integer id) {
        ApiProjectTask apiShare = apiProjectTaskService.getById(id);
        if (!validateOperateUser(apiShare)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectTaskService.removeById(id));
    }

    @PostMapping
    public SimpleResult save(@RequestBody ApiProjectTask apiTask) {
        if (!validateOperateUser(apiTask)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectTaskService.saveOrUpdate(SimpleModelUtils.addAuditInfo(apiTask)));
    }

    protected boolean validateOperateUser(ApiProjectTask projectTask) {
        if (projectTask != null) {
            return apiProjectService.validateUserProject(projectTask.getProjectId());
        }
        return true;
    }

}
