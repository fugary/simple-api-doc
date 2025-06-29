package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.mapper.api.ApiProjectTaskMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import com.fugary.simple.api.tasks.SimpleTaskManager;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.imports.ApiProjectImportVo;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Service
public class ApiProjectTaskServiceImpl extends ServiceImpl<ApiProjectTaskMapper, ApiProjectTask> implements ApiProjectTaskService {

    @Lazy
    @Autowired
    private SimpleTaskManager simpleTaskManager;

    @Override
    public boolean deleteByProject(Integer projectId) {
        List<ApiProjectTask> tasks = list(Wrappers.<ApiProjectTask>query()
                .eq("project_id", projectId));
        tasks.forEach(apiTask -> simpleTaskManager.removeAutoTask(SimpleTaskUtils.getTaskId(apiTask.getId())));
        return this.removeByIds(tasks.stream().map(ApiProjectTask::getId).collect(Collectors.toList()));
    }

    @Override
    public Map<Integer, ApiProjectTask> copyProjectTasks(Integer fromProjectId, Integer toProjectId, Integer id,
                                Map<Integer, Pair<ApiFolder, ApiFolder>> foldersMap) {
        List<ApiProjectTask> tasks = list(Wrappers.<ApiProjectTask>query()
                .eq("project_id", fromProjectId)
                .eq(id != null, "id", id));
        Map<Integer, ApiProjectTask> taskMap = new HashMap<>();
        tasks.forEach(task -> {
            Integer oldId = task.getId();
            task.setId(null);
            task.setProjectId(toProjectId);
            if (fromProjectId.equals(toProjectId)) {
                task.setTaskName(task.getTaskName() + ApiDocConstants.COPY_SUFFIX);
            }
            Pair<ApiFolder, ApiFolder> folderPair = foldersMap.get(task.getToFolder());
            ApiFolder newFolder = folderPair.getRight();
            if (newFolder != null) {
                task.setToFolder(newFolder.getId());
            }
            SimpleModelUtils.cleanCreateModel(task);
            task.setExecDate(null);
            save(task);
            taskMap.put(oldId, task);
        });
        return taskMap;
    }

    @Override
    public SimpleResult<ApiProjectTask> copyProjectTask(ApiProjectTask projectTask) {
        SimpleModelUtils.cleanCreateModel(projectTask);
        projectTask.setExecDate(null);
        projectTask.setTaskName(projectTask.getTaskName() + ApiDocConstants.COPY_SUFFIX);
        save(projectTask);
        return SimpleResultUtils.createSimpleResult(projectTask);
    }

    @Override
    public SimpleResult<ApiProject> saveUrlImportAsTask(ApiProjectImportVo importVo, SimpleResult<ApiProject> importResult) {
        if (importResult.isSuccess() && ApiDocConstants.IMPORT_TYPE_URL.equals(importVo.getImportType())) {
            // url模式下自动保存一份数据为task
            ApiProject project = importResult.getResultData();
            ApiProjectTask projectTask = new ApiProjectTask();
            projectTask.setProjectId(project.getId());
            projectTask.setTaskType(ApiDocConstants.PROJECT_TASK_TYPE_MANUAL);
            projectTask.setTaskName(project.getProjectName() + "-import");
            projectTask.setStatus(ApiDocConstants.STATUS_ENABLED);
            projectTask.setAuthType(importVo.getAuthType());
            projectTask.setAuthContent(importVo.getAuthContent());
            projectTask.setSourceType(importVo.getSourceType());
            projectTask.setSourceUrl(importVo.getUrl());
            if (importResult.getAddons() != null) {
                ApiProjectInfo projectInfo = (ApiProjectInfo) importResult.getAddons().get("projectInfo");
                if (projectInfo != null) {
                    projectTask.setToFolder(projectInfo.getFolderId());
                }
            }
            save(projectTask);
        }
        return importResult;
    }
}
