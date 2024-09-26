package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.imports.ApiDocImporter;
import com.fugary.simple.api.mapper.api.ApiProjectMapper;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectImportVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectTaskImportVo;
import lombok.SneakyThrows;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.fugary.simple.api.utils.security.SecurityUtils.getLoginUser;

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

    @Autowired
    private ApiProjectSchemaDetailService apiProjectSchemaDetailService;

    @Autowired
    private ApiProjectShareService apiProjectShareService;

    @Autowired
    private ApiProjectTaskService apiProjectTaskService;

    @SneakyThrows
    @Override
    public ApiProjectDetailVo loadProjectVo(String projectCode) {
        ApiProject apiProject = getOne(Wrappers.<ApiProject>query().eq("project_code", projectCode));
        ApiProjectDetailVo apiProjectVo = new ApiProjectDetailVo();
        BeanUtils.copyProperties(apiProject, apiProjectVo);
        return apiProjectVo;
    }

    @Override
    public boolean deleteApiProject(Integer id) {
        ApiProject apiProject = getById(id);
        if (apiProject != null) {
            apiFolderService.deleteByProject(id);
            apiProjectSchemaService.deleteByProject(id);
            apiProjectSchemaDetailService.deleteByProject(id);
            apiProjectShareService.deleteByProject(id);
            apiProjectTaskService.deleteByProject(id);
        }
        return removeById(id);
    }

    @Override
    public boolean existsApiProject(ApiProject project) {
        List<ApiProject> existProjects = list(Wrappers.<ApiProject>query().eq("user_name", project.getUserName())
                .eq("project_code", project.getProjectCode()));
        return existProjects.stream().anyMatch(existProject -> !existProject.getId().equals(project.getId()));
    }

    @Override
    public SimpleResult<ExportApiProjectVo> processImportProject(String content, ApiProjectImportVo importVo) {
        ApiDocImporter importer = ApiDocImporter.findImporter(apiDocImporters, importVo.getSourceType());
        ExportApiProjectVo exportVo;
        if (importer == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2004);
        }
        if ((exportVo = importer.doImport(content)) == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2006);
        }
        exportVo.setProjectName(StringUtils.defaultIfBlank(importVo.getProjectName(), exportVo.getProjectName()));
        return SimpleResultUtils.createSimpleResult(exportVo);
    }

    @Override
    public SimpleResult<ApiProject> importNewProject(ExportApiProjectVo exportVo, ApiProjectImportVo importVo) {
        try {
            ApiProject apiProject = new ApiProject();
            BeanUtils.copyProperties(apiProject, exportVo);
            ApiUser loginUser = getLoginUser();
            if (loginUser != null) {
                apiProject.setUserName(loginUser.getUserName());
            }
            this.saveOrUpdate(SimpleModelUtils.addAuditInfo(apiProject)); // 保存apiProject信息
            return this.importUpdateProject(apiProject, exportVo, importVo);
        } catch (IllegalAccessException | InvocationTargetException e) {
            log.error("import project error", e);
        }
        return null;
    }

    @Override
    public SimpleResult<ApiProject> importUpdateProject(ApiProject apiProject, ExportApiProjectVo exportVo, ApiProjectImportVo importVo) {
        ApiFolder parentFolder = null;
        boolean importExists = importVo instanceof ApiProjectTaskImportVo;
        if (importExists) {
            ApiProjectTaskImportVo taskImportVo = (ApiProjectTaskImportVo) importVo;
            if (taskImportVo.getToFolder() != null && (parentFolder = apiFolderService.getById(taskImportVo.getToFolder())) != null
                    && !Objects.equals(parentFolder.getProjectId(), apiProject.getId())) { // folder不属于project属于配置错误忽略该配置
                parentFolder = null;
            }
        }
        apiProjectSchemaService.saveApiProjectSchema(exportVo.getProjectSchema(), apiProject, importExists);
        apiProjectSchemaDetailService.saveApiProjectSchema(exportVo.getProjectSchemaDetails(), apiProject);
        apiFolderService.saveApiFolders(Objects.requireNonNullElseGet(exportVo.getFolders(), ArrayList::new), apiProject, parentFolder, exportVo.getDocs());
        return SimpleResultUtils.createSimpleResult(apiProject);
    }
}
