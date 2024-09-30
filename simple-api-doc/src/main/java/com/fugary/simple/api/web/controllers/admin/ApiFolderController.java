package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import org.apache.commons.lang3.BooleanUtils;
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
@RequestMapping("/admin/folders")
public class ApiFolderController {

    @Autowired
    private ApiFolderService apiFolderService;

    @Autowired
    private ApiProjectService apiProjectService;

    @GetMapping
    public SimpleResult<List<ApiFolder>> searchFolder(@ModelAttribute ProjectQueryVo queryVo) {
        Page<ApiFolder> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        QueryWrapper<ApiFolder> queryWrapper = Wrappers.<ApiFolder>query()
                .eq("project_id", queryVo.getProjectId())
                .like(StringUtils.isNotBlank(keyword), "folder_name", keyword);
        return SimpleResultUtils.createSimpleResult(apiFolderService.page(page, queryWrapper));
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiFolder> getFolder(@PathVariable("id") Integer id) {
        return SimpleResultUtils.createSimpleResult(apiFolderService.getById(id));
    }

    @DeleteMapping("/{id}")
    public SimpleResult removeFolder(@PathVariable("id") Integer id) {
        ApiFolder apiFolder = apiFolderService.getById(id);
        if (BooleanUtils.isTrue(apiFolder.getRootFlag())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2007);
        }
        if (!validateUserProject(apiFolder)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiFolderService.removeById(id));
    }

    @PostMapping
    public SimpleResult saveFolder(@RequestBody ApiFolder apiFolder) {
        if (apiFolder.getParentId() != null) {
            ApiFolder parent = apiFolderService.getById(apiFolder.getParentId());
            if (parent == null || !parent.getProjectId().equals(apiFolder.getProjectId())) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
            }
        }
        if (!validateUserProject(apiFolder)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiFolderService.saveOrUpdate(SimpleModelUtils.addAuditInfo(apiFolder)));
    }

    protected boolean validateUserProject(ApiFolder apiFolder) {
        if (apiFolder != null) {
            return apiProjectService.validateUserProject(apiFolder.getProjectId());
        }
        return true;
    }

}
