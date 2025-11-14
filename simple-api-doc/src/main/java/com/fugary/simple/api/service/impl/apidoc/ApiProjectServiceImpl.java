package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.config.SimpleApiConfigProperties;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.imports.ApiDocImporter;
import com.fugary.simple.api.mapper.api.ApiProjectMapper;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.tasks.ProjectAutoImportInvoker;
import com.fugary.simple.api.tasks.SimpleTaskManager;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.exports.ApiDocParseUtils;
import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.exports.ExportEnvConfigVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectImportVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectTaskImportVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;
import com.fugary.simple.api.web.vo.user.ApiGroupVo;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Autowired
    private ApiGroupService apiGroupService;

    @Lazy
    @Autowired
    private SimpleTaskManager simpleTaskManager;

    @Lazy
    @Autowired
    private ProjectAutoImportInvoker projectAutoImportInvoker;

    @Autowired
    private SimpleApiConfigProperties simpleApiConfigProperties;

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
            if (queryVo.isRemoveAuditFields()) {
                SimpleModelUtils.removeAuditInfo(apiProject);
            }
            ApiProjectDetailVo apiProjectVo = SimpleModelUtils.copy(apiProject, ApiProjectDetailVo.class);
            List<ApiProjectInfo> infoList = apiProjectInfoService.loadByProjectId(apiProject.getId());
            infoList.forEach(info -> {
                if (StringUtils.isNotBlank(info.getAuthContent())) {
                    info.setAuthContent(ApiDocConstants.SECURITY_CONFUSION_VALUE);
                }
                if (queryVo.isRemoveAuditFields()) {
                    SimpleModelUtils.removeAuditInfo(info);
                }
            });
            apiProjectVo.setInfoList(infoList);
            if (includeDocs) {
                List<ApiFolder> folders = forceEnabled ? apiFolderService.loadEnabledApiFolders(apiProject.getId())
                        : apiFolderService.loadApiFolders(apiProject.getId());
                apiProjectVo.setFolders(folders);
                List<ApiDoc> docs = forceEnabled ? apiDocService.loadEnabledByProject(apiProject.getId(), queryVo.isIncludeDocContent())
                        : apiDocService.loadByProject(apiProject.getId(), queryVo.isIncludeDocContent());
                apiProjectVo.setDocs(docs);
                if (CollectionUtils.isNotEmpty(queryVo.getDocIds())) {
                    List<ApiDoc> docList = docs.stream().filter(doc -> queryVo.getDocIds().contains(doc.getId()))
                            .collect(Collectors.toList());
                    apiProjectVo.setDocs(docList);
                    Set<Integer> folderIds = docList.stream().map(ApiDoc::getFolderId).distinct()
                            .flatMap(folderId -> SimpleModelUtils.calcFolderIds(folderId, folders, new HashSet<>()).stream())
                            .collect(Collectors.toSet());
                    List<ApiFolder> folderList = folders.stream().filter(folder -> folderIds.contains(folder.getId()))
                            .collect(Collectors.toList());
                    apiProjectVo.setFolders(folderList);
                }
                if (queryVo.isRemoveAuditFields()) {
                    apiProjectVo.getFolders().forEach(SimpleModelUtils::removeAuditInfo);
                    apiProjectVo.getDocs().forEach(SimpleModelUtils::removeAuditInfo);
                }
            } else if (CollectionUtils.isNotEmpty(infoList)) {
                List<Integer> folderIds = infoList.stream().map(ApiProjectInfo::getFolderId).filter(Objects::nonNull)
                        .distinct().collect(Collectors.toList());
                List<ApiFolder> folders = apiFolderService.listByIds(folderIds);
                apiProjectVo.setFolders(folders);
            }
            if (queryVo.isIncludesShares()) {
                List<ApiProjectShare> shares = apiProjectShareService.list(Wrappers.<ApiProjectShare>query()
                        .eq("project_id", apiProject.getId()));
                apiProjectVo.setShares(shares.stream().map(share -> {
                    if (queryVo.isRemoveAuditFields()) {
                        SimpleModelUtils.removeAuditInfo(share);
                    }
                    return SimpleModelUtils.toShareVo(share);
                }).collect(Collectors.toList()));
            }
            if (queryVo.isIncludeTasks()) {
                List<ApiProjectTask> tasks = apiProjectTaskService.list(Wrappers.<ApiProjectTask>query()
                        .eq("project_id", apiProject.getId()));
                if (queryVo.isRemoveAuditFields()) {
                    tasks.forEach(SimpleModelUtils::removeAuditInfo);
                }
                apiProjectVo.setTasks(tasks);
            }
            ApiUserVo loginUser = getLoginUser();
            if (queryVo.isIncludeAuthorities() && StringUtils.isNotBlank(apiProject.getGroupCode()) && loginUser != null) {
                ApiGroup apiGroup = apiGroupService.loadGroup(apiProject.getGroupCode());
                if (apiGroup != null) {
                    ApiGroupVo groupVo = SimpleModelUtils.copy(apiGroup, ApiGroupVo.class);
                    List<ApiUserGroup> userGroups = apiGroupService.loadGroupUsers(loginUser.getId(), apiProject.getGroupCode());
                    if (!userGroups.isEmpty()) {
                        groupVo.setUserGroups(userGroups);
                        groupVo.setAuthorities(userGroups.get(0).getAuthorities());
                    }
                    apiProjectVo.setApiGroup(groupVo);
                }
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
                if (!apiGroupService.checkProjectAccess(getLoginUser(), project, ApiGroupAuthority.WRITABLE)) {
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
        exportVo.setIconUrl(StringUtils.defaultIfBlank(importVo.getIconUrl(), exportVo.getIconUrl()));
        exportVo.setGroupCode(importVo.getGroupCode());
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
            ApiProjectTaskImportVo taskImportVo = (ApiProjectTaskImportVo) importVo;
            if (taskImportVo.getToFolder() != null && (mountFolder = apiFolderService.getById(taskImportVo.getToFolder())) != null
                    && !Objects.equals(mountFolder.getProjectId(), apiProject.getId())) { // folder不属于project属于配置错误忽略该配置
                mountFolder = null;
            }
        }
        mountFolder = apiFolderService.getOrCreateMountFolder(apiProject, mountFolder);
        ApiProjectInfo projectInfo = apiProjectInfoService.saveApiProjectInfo(exportVo.getProjectInfo(), apiProject, mountFolder, importExists);
        if (Boolean.TRUE.equals(projectInfo.getDefaultFlag())) {
            List<ExportEnvConfigVo> mergedEnvConfigs = ApiDocParseUtils.mergeEnvConfigs(apiProject.getEnvContent(), exportVo.getEnvContent());
            SimpleModelUtils.copyNoneNullValue(exportVo, apiProject);
            apiProject.setEnvContent(JsonUtils.toJson(mergedEnvConfigs));
        }
        updateById(SimpleModelUtils.addAuditInfo(apiProject));
        apiProjectInfoDetailService.saveApiProjectInfoDetails(apiProject, projectInfo, exportVo.getProjectInfoDetails());
        apiFolderService.saveApiFolders(apiProject, projectInfo, mountFolder, Objects.requireNonNullElseGet(exportVo.getFolders(), ArrayList::new), exportVo.getDocs());
        return SimpleResultUtils.createSimpleResult(apiProject)
                .add("projectInfo", projectInfo);
    }

    @Override
    public SimpleResult<ApiProject> copyProject(ApiProject project) {
        Integer lastProjectId = project.getId();
        project.setId(null);
        project.setProjectCode(SimpleModelUtils.uuid());
        project.setProjectName(project.getProjectName() + ApiDocConstants.COPY_SUFFIX);
        save(project); // 复制新的project
        // folder和doc
        Map<Integer, Pair<ApiFolder, ApiFolder>> foldersMap = apiFolderService.copyProjectFolders(lastProjectId, project.getId(), null);
        Map<Integer, Pair<ApiProjectInfo, ApiProjectInfo>> infosMap = apiProjectInfoService.copyProjectInfos(lastProjectId, project.getId(), foldersMap);
        Map<Integer, Integer> docMappings = apiDocService.copyProjectDocs(lastProjectId, project.getId(), foldersMap, infosMap);
        // share和task比较好处理
        apiProjectShareService.copyProjectShares(lastProjectId, project.getId(), null, docMappings);
        Map<Integer, ApiProjectTask> copyTasks = apiProjectTaskService.copyProjectTasks(lastProjectId, project.getId(), null, foldersMap);
        copyTasks.forEach((key, apiTask) -> {
            if (apiTask.isEnabled() && ApiDocConstants.PROJECT_TASK_TYPE_AUTO.equals(apiTask.getTaskType())) {
                simpleTaskManager.addOrUpdateAutoTask(SimpleTaskUtils.projectTask2SimpleTask(apiTask,
                        projectAutoImportInvoker, simpleApiConfigProperties));
            }
        });
        return SimpleResultUtils.createSimpleResult(project);
    }

    @Override
    public boolean saveEnvConfigs(ApiProject apiProject, List<ExportEnvConfigVo> envConfigs) {
        String jsonContent = JsonUtils.toJson(envConfigs);
        return update(Wrappers.<ApiProject>lambdaUpdate()
                .set(ApiProject::getEnvContent, jsonContent)
                .eq(ApiProject::getId, apiProject.getId()));
    }

    @Override
    public ApiProjectInfo findOrCreateProjectInfo(ApiDoc apiDoc) {
        List<ApiProjectInfo> projectInfos = apiProjectInfoService.loadByProjectId(apiDoc.getProjectId());
        if (CollectionUtils.isNotEmpty(projectInfos)) {
            if (projectInfos.size() == 1) {
                return projectInfos.get(0);
            } else {
                List<ApiFolder> apiFolders = apiFolderService.loadApiFolders(apiDoc.getProjectId());
                Map<Integer, ApiFolder> folderMap = apiFolders.stream().collect(Collectors.toMap(ApiFolder::getId, Function.identity()));
                Map<Integer, ApiProjectInfo> folderProjectInfoMap = projectInfos.stream().collect(Collectors.toMap(ApiProjectInfo::getFolderId, Function.identity()));
                ApiFolder apiFolder = folderMap.get(apiDoc.getFolderId());
                Set<Integer> scannedSet = new HashSet<>();
                while (apiFolder != null && !scannedSet.contains(apiFolder.getId())) {
                    scannedSet.add(apiFolder.getId());
                    ApiProjectInfo projectInfo = folderProjectInfoMap.get(apiFolder.getId());
                    if (projectInfo != null) {
                        return projectInfo;
                    } else {
                        apiFolder = folderMap.get(apiFolder.getParentId());
                    }
                }
                return projectInfos.get(0);
            }
        } else if (ApiDocConstants.DOC_TYPE_API.equals(apiDoc.getDocType())) { // 没有Info数据建个新的
            ApiProject project = this.getById(apiDoc.getProjectId());
            ApiFolder rootFolder = apiFolderService.getRootFolder(apiDoc.getProjectId());
            ApiProjectInfo projectInfo = SimpleModelUtils.getDefaultProjectInfo(project);
            projectInfo.setFolderId(rootFolder.getId());
            apiProjectInfoService.save(projectInfo);
            return projectInfo;
        }
        return null;
    }
}
