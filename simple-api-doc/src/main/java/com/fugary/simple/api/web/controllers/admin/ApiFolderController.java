package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.service.apidoc.ApiProjectAccessService;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoService;
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
    private ApiProjectAccessService apiProjectAccessService;

    @Autowired
    private ApiProjectInfoService apiProjectInfoService;

    @GetMapping
    public SimpleResult<List<ApiFolder>> searchFolder(@ModelAttribute ProjectQueryVo queryVo) {
        if (!apiProjectAccessService.canAccessProject(queryVo.getProjectId(), ApiGroupAuthority.READABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        Page<ApiFolder> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        QueryWrapper<ApiFolder> queryWrapper = Wrappers.<ApiFolder>query()
                .eq("project_id", queryVo.getProjectId())
                .like(StringUtils.isNotBlank(keyword), "folder_name", keyword);
        return SimpleResultUtils.createSimpleResult(apiFolderService.page(page, queryWrapper));
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiFolder> getFolder(@PathVariable("id") Integer id) {
        ApiFolder apiFolder = apiFolderService.getById(id);
        if (apiFolder == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!apiProjectAccessService.canAccessFolder(apiFolder, ApiGroupAuthority.READABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiFolder);
    }

    private SimpleResult<Boolean> removeFolder(Integer id, boolean clear) {
        ApiFolder apiFolder = apiFolderService.getById(id);
        if (apiFolder == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!clear && BooleanUtils.isTrue(apiFolder.getRootFlag())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2007);
        }
        if (!apiProjectAccessService.canAccessFolder(apiFolder, ApiGroupAuthority.WRITABLE)) {
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
    public SimpleResult<ApiFolder> saveFolder(@RequestBody ApiFolder apiFolder) {
        ApiFolder existsFolder = null;
        if (apiFolder.getId() != null) {
            existsFolder = apiFolderService.getById(apiFolder.getId());
            if (existsFolder == null) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
            }
            if (!apiProjectAccessService.canAccessFolder(existsFolder, ApiGroupAuthority.WRITABLE)) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
            }
        }
        if (apiFolder.getParentId() != null) {
            ApiFolder parent = apiFolderService.getById(apiFolder.getParentId());
            if (parent == null || !parent.getProjectId().equals(apiFolder.getProjectId())) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
            }
        }
        if (!apiProjectAccessService.canAccessFolder(apiFolder, ApiGroupAuthority.WRITABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        if (existsFolder != null && SimpleModelUtils.isSameData(apiFolder, existsFolder)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2000, existsFolder);
        }
        if (apiFolderService.existsApiFolder(apiFolder)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_1001);
        }
        apiFolderService.saveOrUpdate(SimpleModelUtils.addAuditInfo(apiFolder));
        return SimpleResultUtils.createSimpleResult(apiFolder);
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
        if (!apiProjectAccessService.canAccessFolder(apiFolder, ApiGroupAuthority.WRITABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        boolean updated = apiFolderService.updateSorts(sortsVo, apiFolder);
        return updated ? SimpleResultUtils.createSimpleResult(true) : SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
    }

}
