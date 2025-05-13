package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.project.ApiDocConfigSortsVo;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

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
    private ApiDocService apiDocService;

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiProjectInfoService apiProjectInfoService;

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

    private SimpleResult<Boolean> removeFolder(Integer id, boolean clear) {
        ApiFolder apiFolder = apiFolderService.getById(id);
        if (apiFolder == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!clear && BooleanUtils.isTrue(apiFolder.getRootFlag())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2007);
        }
        if (!validateUserProject(apiFolder)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        List<ApiFolder> folders = apiFolderService.loadSubFolders(id);
        if (!folders.isEmpty()) {
            folders.forEach(folder -> {
                if (clear && Objects.equals(id, folder.getId())) { // clear时仅删除子文档
                    apiDocService.deleteByFolder(folder.getId());
                    apiProjectInfoService.deleteByFolder(folder.getId());
                } else {
                    apiFolderService.deleteFolder(folder.getId());
                }
            });
        }
        return SimpleResultUtils.createSimpleResult(!folders.isEmpty());
    }

    @DeleteMapping("/{id}")
    public SimpleResult<Boolean> removeFolder(@PathVariable("id") Integer id) {
        return removeFolder(id, false);
    }

    @DeleteMapping("/clearFolder/{id}")
    public SimpleResult<Boolean> clearFolder(@PathVariable("id") Integer id) {
        return removeFolder(id, true);
    }

    @PostMapping
    public SimpleResult<Boolean> saveFolder(@RequestBody ApiFolder apiFolder) {
        if (apiFolder.getParentId() != null) {
            ApiFolder parent = apiFolderService.getById(apiFolder.getParentId());
            if (parent == null || !parent.getProjectId().equals(apiFolder.getProjectId())) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
            }
        }
        if (!validateUserProject(apiFolder)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        if (apiFolderService.existsApiFolder(apiFolder)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_1001);
        }
        return SimpleResultUtils.createSimpleResult(apiFolderService.saveOrUpdate(SimpleModelUtils.addAuditInfo(apiFolder)));
    }

    @PostMapping("/updateSorts")
    public SimpleResult<Boolean> updateSorts(@RequestBody ApiDocConfigSortsVo sortsVo) {
        if (CollectionUtils.isEmpty(sortsVo.getSorts()) || sortsVo.getProjectId() == null || sortsVo.getFolderId() == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_400);
        }
        ApiFolder apiFolder = apiFolderService.getById(sortsVo.getFolderId());
        if (apiFolder == null || !apiFolder.getProjectId().equals(sortsVo.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        return SimpleResultUtils.createSimpleResult(apiFolderService.updateSorts(sortsVo, apiFolder));
    }

    protected boolean validateUserProject(ApiFolder apiFolder) {
        if (apiFolder != null) {
            return apiProjectService.validateUserProject(apiFolder.getProjectId());
        }
        return true;
    }

}
