package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.imports.ApiDocImporter;
import com.fugary.simple.api.mapper.api.ApiProjectMapper;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectImportVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectTaskImportVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

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
    private ApiProjectInfoService apiProjectInfoService;

    @Autowired
    private ApiProjectInfoDetailService apiProjectInfoDetailService;

    @Autowired
    private ApiProjectShareService apiProjectShareService;

    @Autowired
    private ApiProjectTaskService apiProjectTaskService;

    @SneakyThrows
    @Override
    public ApiProjectDetailVo loadProjectVo(ProjectDetailQueryVo queryVo) {
        String projectCode = queryVo.getProjectCode();
        boolean forceEnabled = queryVo.isForceEnabled();
        boolean includeDocs = queryVo.isIncludeDocs();
        ApiProject apiProject = getOne(Wrappers.<ApiProject>query().eq(StringUtils.isNotBlank(projectCode), "project_code", projectCode)
                .eq(queryVo.getProjectId() != null, "id", queryVo.getProjectId())
                .eq(forceEnabled, ApiDocConstants.STATUS_KEY, ApiDocConstants.STATUS_ENABLED));
        if (apiProject != null) {
            ApiProjectDetailVo apiProjectVo = SimpleModelUtils.copy(apiProject, ApiProjectDetailVo.class);
            List<ApiProjectInfo> infoList = apiProjectInfoService.loadByProjectId(apiProject.getId());
            apiProjectVo.setInfoList(infoList);
            if (includeDocs) {
                List<ApiFolder> folders = forceEnabled ? apiFolderService.loadEnabledApiFolders(apiProject.getId())
                        : apiFolderService.loadApiFolders(apiProject.getId());
                apiProjectVo.setFolders(folders);
                List<ApiDoc> docs = forceEnabled ? apiDocService.loadEnabledByProject(apiProject.getId())
                        : apiDocService.loadByProject(apiProject.getId());
                apiProjectVo.setDocs(docs);
            }
            if (queryVo.isIncludesShares()) {
                List<ApiProjectShare> shares = apiProjectShareService.list(Wrappers.<ApiProjectShare>query()
                        .eq("project_id", apiProject.getId()));
                apiProjectVo.setShares(shares);
            }
            if (queryVo.isIncludeTasks()) {
                List<ApiProjectTask> tasks = apiProjectTaskService.list(Wrappers.<ApiProjectTask>query()
                        .eq("project_id", apiProject.getId()));
                apiProjectVo.setTasks(tasks);
            }
            return apiProjectVo;
        }
        return null;
    }

    @Override
    public boolean deleteApiProject(Integer id) {
        ApiProject apiProject = getById(id);
        if (apiProject != null) {
            apiFolderService.deleteByProject(id);
            apiProjectInfoService.deleteByProject(id);
            apiProjectInfoDetailService.deleteByProject(id);
            apiProjectShareService.deleteByProject(id);
            apiProjectTaskService.deleteByProject(id);
        }
        return removeById(id);
    }

    @Override
    public boolean deleteApiProjects(List<Integer> ids) {
        for (Integer id : ids) {
            deleteApiProject(id);
        }
        return true;
    }

    @Override
    public boolean existsApiProject(ApiProject project) {
        List<ApiProject> existProjects = list(Wrappers.<ApiProject>query().eq("user_name", project.getUserName())
                .eq("project_code", project.getProjectCode()));
        return existProjects.stream().anyMatch(existProject -> !existProject.getId().equals(project.getId()));
    }

    @Override
    public boolean validateUserProject(Integer projectId) {
        if (projectId != null) {
            ApiProject project = getById(projectId);
            if (project != null) {
                if (!SecurityUtils.validateUserUpdate(project.getUserName())) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean saveProject(ApiProject project) {
        boolean saved = saveOrUpdate(project);
        if (project.getId() != null) {
            ApiFolder rootFolder = apiFolderService.getRootFolder(project.getId());
            if (rootFolder == null) {
                apiFolderService.createRootFolder(project);
            }
        }
        return saved;
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
        ApiProject apiProject = SimpleModelUtils.copy(exportVo, ApiProject.class);
        ApiUser loginUser = getLoginUser();
        if (loginUser != null) {
            apiProject.setUserName(loginUser.getUserName());
        }
        this.saveOrUpdate(SimpleModelUtils.addAuditInfo(apiProject)); // 保存apiProject信息
        return this.importUpdateProject(apiProject, exportVo, importVo);
    }

    @Override
    public SimpleResult<ApiProject> importUpdateProject(ApiProject apiProject, ExportApiProjectVo exportVo, ApiProjectImportVo importVo) {
        ApiFolder mountFolder = null;
        boolean importExists = importVo instanceof ApiProjectTaskImportVo;
        if (importExists) {
            SimpleModelUtils.copyNoneNullValue(exportVo, apiProject);
            updateById(SimpleModelUtils.addAuditInfo(apiProject));
            ApiProjectTaskImportVo taskImportVo = (ApiProjectTaskImportVo) importVo;
            if (taskImportVo.getToFolder() != null && (mountFolder = apiFolderService.getById(taskImportVo.getToFolder())) != null
                    && !Objects.equals(mountFolder.getProjectId(), apiProject.getId())) { // folder不属于project属于配置错误忽略该配置
                mountFolder = null;
            }
        }
        mountFolder = apiFolderService.getOrCreateMountFolder(apiProject, mountFolder);
        ApiProjectInfo projectInfo = apiProjectInfoService.saveApiProjectInfo(exportVo.getProjectInfo(), apiProject, mountFolder, importExists);
        apiProjectInfoDetailService.saveApiProjectInfoDetails(apiProject, projectInfo, exportVo.getProjectInfoDetails());
        apiFolderService.saveApiFolders(apiProject, projectInfo, mountFolder, Objects.requireNonNullElseGet(exportVo.getFolders(), ArrayList::new), exportVo.getDocs());
        return SimpleResultUtils.createSimpleResult(apiProject);
    }

    @Override
    public SimpleResult<ApiProject> copyProject(ApiProject project) {
        Integer lastProjectId = project.getId();
        project.setId(null);
        project.setProjectCode(SimpleModelUtils.uuid());
        project.setProjectName(project.getProjectName() + "-copy");
        save(project); // 复制新的project
        // share和task比较好处理
        apiProjectShareService.copyProjectShares(lastProjectId, project.getId(), null);
        // folder和doc
        Map<Integer, Pair<ApiFolder, ApiFolder>> foldersMap = apiFolderService.copyProjectFolders(lastProjectId, project.getId(), null);
        Map<Integer, Pair<ApiProjectInfo, ApiProjectInfo>> infosMap = apiProjectInfoService.copyProjectInfos(lastProjectId, project.getId(), foldersMap);
        apiDocService.copyProjectDocs(lastProjectId, project.getId(), foldersMap, infosMap);
        apiProjectTaskService.copyProjectTasks(lastProjectId, project.getId(), null, foldersMap);
        return SimpleResultUtils.createSimpleResult(project);
    }
}
