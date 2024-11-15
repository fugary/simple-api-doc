package com.fugary.simple.api.tasks;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiLog;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.event.log.OperationLogEvent;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import com.fugary.simple.api.service.apidoc.content.DocContentProvider;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.utils.servlet.HttpRequestUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoVo;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectTaskImportVo;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Slf4j
@Component
public class ProjectAutoImportInvoker implements ApplicationContextAware {

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiProjectTaskService apiProjectTaskService;

    @Qualifier("urlDocContentProviderImpl")
    @Autowired
    private DocContentProvider<UrlWithAuthVo> urlDocContentProvider;

    private ApplicationContext applicationContext;

    /**
     * 导入数据
     *
     * @param projectTask
     */
    @SneakyThrows
    public SimpleResult<ApiProject> importProject(ApiProjectTask projectTask) {
        long start = System.currentTimeMillis();
        HttpServletRequest request = HttpRequestUtils.getCurrentRequest();
        boolean manual = request != null;
        Date createDate = new Date();
        ApiLog.ApiLogBuilder logBuilder = ApiLog.builder()
                .ipAddress(manual ? HttpRequestUtils.getIp(request) : HttpRequestUtils.calcFirstLocalIp())
                .logName(ProjectAutoImportInvoker.class.getSimpleName() + "#importProject")
                .logType(manual ? request.getMethod() : null)
                .taskType(projectTask.getTaskType())
                .projectId(String.valueOf(projectTask.getProjectId()))
                .dataId(String.valueOf(projectTask.getId()))
                .extend1(manual ? JsonUtils.toJson(HttpRequestUtils.getRequestHeadersMap(request)) : null)
                .createDate(createDate);
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
                logBuilder.logData(SimpleModelUtils.logDataString(List.of(importVo)));
                SimpleResult<String> contentResult = urlDocContentProvider.getContent(importVo);
                if (!contentResult.isSuccess()) {
                    publishEvent(logBuilder, apiProject, createDate, contentResult.getMessage());
                    return SimpleResultUtils.createError(contentResult.getMessage());
                }
                SimpleResult<ExportApiProjectVo> parseResult = apiProjectService.processImportProject(contentResult.getResultData(), importVo);
                if (!parseResult.isSuccess()) {
                    String errorMessage = MessageFormat.format("[{0}]项目任务[{1}]解析文档错误：{2}", apiProject.getProjectName(), projectTask.getTaskName(), parseResult.getMessage());
                    log.error(errorMessage);
                    publishEvent(logBuilder, apiProject, createDate, errorMessage);
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
                    publishEvent(logBuilder, apiProject, createDate, errorMessage);
                    return SimpleResultUtils.createError(errorMessage);
                }
                projectTask.setExecDate(new Date());
                apiProjectTaskService.updateById(projectTask);
                log.info("import project task {}/{} cost {}ms", apiProject.getProjectName(), projectTask.getTaskName(), System.currentTimeMillis() - start);
                publishEvent(logBuilder, apiProject, createDate, getFormatMessage(projectTask, "[{0}]/[{1}]/[{2}]执行导入成功"), true);
                return SimpleResultUtils.createSimpleResult(apiProject);
            } else {
                publishEvent(logBuilder, null, createDate, getFormatMessage(projectTask, "[{0}]/[{1}]/[{2}]Project信息为空"));
            }
        } else {
            publishEvent(logBuilder, null, createDate, getFormatMessage(projectTask, "[{0}]/[{1}]/[{2}]sourceUrl不能为空"));
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
    }

    private String getFormatMessage(ApiProjectTask projectTask, String message) {
        return MessageFormat.format(message, projectTask.getProjectId(), projectTask.getId(), projectTask.getTaskName());
    }

    protected void publishEvent(ApiLog.ApiLogBuilder logBuilder, ApiProject apiProject,
                                Date createDate, String message) {
        publishEvent(logBuilder, apiProject, createDate, message, false);
    }

    protected void publishEvent(ApiLog.ApiLogBuilder logBuilder, ApiProject apiProject,
                                Date createDate, String message, boolean success) {
        logBuilder.logResult(success ? ApiDocConstants.SUCCESS : ApiDocConstants.FAIL);
        if (apiProject != null) {
            logBuilder.userName(apiProject.getUserName())
                    .creator(apiProject.getUserName());
        }
        ApiUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser != null) {
            logBuilder.userName(loginUser.getUserName())
                    .creator(loginUser.getUserName());
        }
        ApiLog apiLog = logBuilder
                .logMessage(message)
                .logTime(System.currentTimeMillis() - createDate.getTime()).build();
        applicationContext.publishEvent(new OperationLogEvent(apiLog));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
