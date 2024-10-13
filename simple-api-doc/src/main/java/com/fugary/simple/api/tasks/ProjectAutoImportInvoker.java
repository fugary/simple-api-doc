package com.fugary.simple.api.tasks;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import com.fugary.simple.api.service.apidoc.content.DocContentProvider;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoVo;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectTaskImportVo;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.Date;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Slf4j
@Component
public class ProjectAutoImportInvoker {

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiProjectTaskService apiProjectTaskService;

    @Qualifier("urlDocContentProviderImpl")
    @Autowired
    private DocContentProvider<UrlWithAuthVo> urlDocContentProvider;

    /**
     * 导入数据
     *
     * @param projectTask
     */
    @SneakyThrows
    public SimpleResult<ApiProject> importProject(ApiProjectTask projectTask) {
        long start = System.currentTimeMillis();
        if (StringUtils.isNotBlank(projectTask.getSourceUrl())) {
            ApiProject apiProject = apiProjectService.getById(projectTask.getProjectId());
            if (apiProject != null) {
                ApiProjectTaskImportVo importVo = new ApiProjectTaskImportVo();
                importVo.setProjectId(projectTask.getProjectId());
                importVo.setImportType(ApiDocConstants.IMPORT_TYPE_URL);
                importVo.setUrl(projectTask.getSourceUrl());
                importVo.setAuthType(projectTask.getAuthType());
                importVo.setAuthContent(projectTask.getAuthContent());
                importVo.setSourceType(projectTask.getSourceType());
                importVo.setProjectName(apiProject.getProjectName());
                importVo.setTaskName(projectTask.getTaskName());
                importVo.setTaskType(projectTask.getTaskType());
                importVo.setToFolder(projectTask.getToFolder());
                String content = urlDocContentProvider.getContent(importVo);
                SimpleResult<ExportApiProjectVo> parseResult = apiProjectService.processImportProject(content, importVo);
                if (!parseResult.isSuccess()) {
                    String errorMessage = MessageFormat.format("[{0}]项目任务[{1}]解析文档错误：{2}", apiProject.getProjectName(), projectTask.getTaskName(), parseResult.getMessage());
                    log.error(errorMessage);
                    return SimpleResultUtils.createError(errorMessage);
                }
                ExportApiProjectVo exportProjectVo = parseResult.getResultData();
                ExportApiProjectInfoVo projectInfo = exportProjectVo.getProjectInfo();
                projectInfo.setImportType(importVo.getImportType());
                projectInfo.setSourceType(importVo.getSourceType());
                projectInfo.setAuthType(importVo.getAuthType());
                projectInfo.setAuthContent(importVo.getAuthContent());
                projectInfo.setUrl(importVo.getUrl());
                projectInfo.setFolderId(projectTask.getToFolder());
                SimpleResult<ApiProject> importResult = apiProjectService.importUpdateProject(apiProject, parseResult.getResultData(), importVo);
                if (!importResult.isSuccess()) {
                    String errorMessage = MessageFormat.format("[{0}]项目任务[{1}]执行导入错误：{2}", apiProject.getProjectName(), projectTask.getTaskName(), parseResult.getMessage());
                    log.error(errorMessage);
                    return SimpleResultUtils.createError(errorMessage);
                }
                projectTask.setExecDate(new Date());
                apiProjectTaskService.updateById(projectTask);
                log.info("import project task {}/{} cost {}ms", apiProject.getProjectName(), projectTask.getTaskName(), System.currentTimeMillis() - start);
                return SimpleResultUtils.createSimpleResult(apiProject);
            }
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
    }
}
