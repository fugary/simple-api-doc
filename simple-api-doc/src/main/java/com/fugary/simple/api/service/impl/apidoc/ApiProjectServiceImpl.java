package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.imports.ApiDocImporter;
import com.fugary.simple.api.mapper.api.ApiProjectMapper;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.service.apidoc.ApiProjectSchemaService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportApiFolderVo;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectImportVo;
import lombok.SneakyThrows;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Create date 2024/7/15<br>
 *
 * @author gary.fu
 */
@Service
public class ApiProjectServiceImpl extends ServiceImpl<ApiProjectMapper, ApiProject> implements ApiProjectService {

    @Autowired
    private List<ApiDocImporter> apiDocImporters;

    @Autowired
    private ApiFolderService apiFolderService;

    @Autowired
    private ApiDocService apiDocService;

    @Autowired
    private ApiProjectSchemaService apiProjectSchemaService;

    @SneakyThrows
    @Override
    public ApiProjectDetailVo loadProjectVo(String projectCode) {
        ApiProject apiProject = getOne(Wrappers.<ApiProject>query().eq("project_code", projectCode));
        ApiProjectDetailVo apiProjectVo = new ApiProjectDetailVo();
        BeanUtils.copyProperties(apiProject, apiProjectVo);
        return apiProjectVo;
    }

    @Override
    public boolean deleteMockProject(Integer id) {
        ApiProject mockProject = getById(id);
        if (mockProject != null) {
//            List<ModelGroup> mockGroups = mockGroupService.list(Wrappers.<ModelGroup>query()
//                    .eq("project_code", mockProject.getProjectCode())
//                    .eq("user_name", mockProject.getUserName()));
//            mockGroupService.deleteMockGroups(mockGroups.stream().map(ModelGroup::getId).collect(Collectors.toList()));
        }
        return removeById(id);
    }

    @Override
    public boolean deleteMockProjects(List<Integer> ids) {
        for (Integer id : ids) {
            deleteMockProject(id);
        }
        return false;
    }

    @Override
    public boolean existsMockProject(ApiProject project) {
        List<ApiProject> existProjects = list(Wrappers.<ApiProject>query().eq("user_name", project.getUserName())
                .eq("project_code", project.getProjectCode()));
        return existProjects.stream().anyMatch(existProject -> !existProject.getId().equals(project.getId()));
    }

    @Override
    public SimpleResult<ExportApiProjectVo> processImportProject(String content, ApiProjectImportVo importVo) {
        ApiDocImporter importer = ApiDocImporter.findImporter(apiDocImporters, importVo.getSourceType());
        ExportApiProjectVo exportVo;
        if (importer == null || (exportVo = importer.doImport(content)) == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2003);
        }
        return SimpleResultUtils.createSimpleResult(exportVo);
    }

    @Override
    public SimpleResult<ApiProject> importProject(ExportApiProjectVo exportVo, ApiProjectImportVo importVo) {
        try {
            ApiProject apiProject = new ApiProject();
            BeanUtils.copyProperties(apiProject, exportVo);
            this.saveOrUpdate(apiProject);
            List<ExportApiFolderVo> folders = Objects.requireNonNullElseGet(exportVo.getFolders(), ArrayList::new);
            apiFolderService.saveApiFolders(folders, apiProject, null);
            return SimpleResultUtils.createSimpleResult(apiProject);
        } catch (IllegalAccessException | InvocationTargetException e) {
            log.error("import project error", e);
        }
        return null;
    }
}
