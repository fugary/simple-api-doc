package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.fugary.simple.api.utils.security.SecurityUtils.getLoginUser;

/**
 * Create date 2024/7/15<br>
 *
 * @author gary.fu
 */
@RestController
@RequestMapping("/admin/projects")
public class MockProjectController {

    @Autowired
    private ApiProjectService apiProjectService;

    @GetMapping
    public SimpleResult<List<ApiProject>> search(@ModelAttribute ProjectQueryVo queryVo) {
        Page<ApiProject> page = SimpleResultUtils.toPage(queryVo);
        QueryWrapper<ApiProject> queryWrapper = Wrappers.<ApiProject>query();
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        queryWrapper.and(StringUtils.isNotBlank(keyword), wrapper -> wrapper.like("project_name", keyword)
                .or().like("project_code", keyword));
        String userName = SecurityUtils.getUserName(queryVo.getUserName());
        queryWrapper.and(wrapper -> wrapper.and(wrapper1 -> wrapper1.eq("user_name", userName)));
        return SimpleResultUtils.createSimpleResult(apiProjectService.page(page, queryWrapper));
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiProject> get(@PathVariable("id") Integer id) {
        return SimpleResultUtils.createSimpleResult(apiProjectService.getById(id));
    }

    @DeleteMapping("/{id}")
    public SimpleResult remove(@PathVariable("id") Integer id) {
        return SimpleResultUtils.createSimpleResult(apiProjectService.deleteMockProject(id));
    }

    @DeleteMapping("/removeByIds/{ids}")
    public SimpleResult removeByIds(@PathVariable("ids") List<Integer> ids) {
        return SimpleResultUtils.createSimpleResult(apiProjectService.deleteMockProjects(ids));
    }

    @PostMapping
    public SimpleResult save(@RequestBody ApiProject project) {
        ApiUser loginUser = getLoginUser();
        if (StringUtils.isBlank(project.getUserName()) && loginUser != null) {
            project.setUserName(loginUser.getUserName());
        }
        if (!SecurityUtils.validateUserUpdate(project.getUserName())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        if (apiProjectService.existsMockProject(project)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_1001);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectService.saveOrUpdate(SimpleModelUtils.addAuditInfo(project)));
    }

    @PostMapping("/selectProjects")
    public SimpleResult<List<ApiProject>> selectProjects(@RequestBody ProjectQueryVo queryVo) {
        QueryWrapper<ApiProject> queryWrapper = Wrappers.<ApiProject>query();
        String userName = SecurityUtils.getUserName(queryVo.getUserName());
        queryWrapper.eq("status", 1)
                .and(wrapper -> wrapper.and(wrapper1 -> wrapper1.eq("user_name", userName)));
        return SimpleResultUtils.createSimpleResult(apiProjectService.list(queryWrapper));
    }
}
