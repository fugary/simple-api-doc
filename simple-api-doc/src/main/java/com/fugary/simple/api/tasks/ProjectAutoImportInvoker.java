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
import com.fugary.simple.api.web.vo.imports.DocSourceData;
import com.fugary.simple.api.web.vo.imports.ApiProjectTaskImportVo;
import com.fugary.simple.api.web.vo.imports.ImportStatisticsVo;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
        HttpServletResponse response = HttpRequestUtils.getCurrentResponse();
        boolean manual = request != null && response != null;
        Date createDate = new Date();
        ApiLog.ApiLogBuilder logBuilder = ApiLog.builder()
                .ipAddress(manual ? HttpRequestUtils.getIp(request) : HttpRequestUtils.calcFirstLocalIp())
                .logName(ProjectAutoImportInvoker.class.getSimpleName() + "#importProject")
                .logType(manual ? request.getMethod() : null)
                .taskType(projectTask.getTaskType())
                .projectId(String.valueOf(projectTask.getProjectId()))
                .dataId(String.valueOf(projectTask.getId()))
                .headers(manual ? JsonUtils.toJson(HttpRequestUtils.getRequestHeadersMap(request, true)) : null)
                .responseHeaders(manual ? JsonUtils.toJson(HttpRequestUtils.getResponseHeadersMap(response)) : null)
                .requestUrl(manual ? HttpRequestUtils.getRequestUrl(request) : null)
                .createDate(createDate);
        ApiProject apiProject = null;
        try {
            if (StringUtils.isNotBlank(projectTask.getSourceUrl())) {
                apiProject = apiProjectService.getById(projectTask.getProjectId());
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
                    if (StringUtils.isBlank(importVo.getFileName()) && StringUtils.isNotBlank(importVo.getUrl())) {
                        String urlPath = StringUtils.substringBefore(importVo.getUrl(), "?");
                        int lastSlash = urlPath.lastIndexOf('/');
                        if (lastSlash >= 0 && lastSlash < urlPath.length() - 1) {
                            importVo.setFileName(urlPath.substring(lastSlash + 1));
                        }
                    }
                    logBuilder.logData(SimpleModelUtils.logDataString(List.of(importVo)));
                    SimpleResult<DocSourceData> contentResult = urlDocContentProvider.getContent(importVo);
                    if (!contentResult.isSuccess()) {
                        String errorMessage = MessageFormat.format("[{0}]项目任务[{1}]获取远程文档失败：{2}", apiProject.getProjectName(), projectTask.getTaskName(), contentResult.getMessage());
                        publishEvent(logBuilder, apiProject, createDate, errorMessage, false, null, null);
                        updateTaskExecDate(projectTask);
                        return SimpleResultUtils.createError(errorMessage);
                    }
                    SimpleResult<ExportApiProjectVo> parseResult = apiProjectService.processImportProject(contentResult.getResultData(), importVo);
                    if (!parseResult.isSuccess()) {
                        String errorMessage = MessageFormat.format("[{0}]项目任务[{1}]解析文档错误：{2}", apiProject.getProjectName(), projectTask.getTaskName(), parseResult.getMessage());
                        log.error(errorMessage);
                        publishEvent(logBuilder, apiProject, createDate, errorMessage, false, null, null);
                        updateTaskExecDate(projectTask);
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
                        String errorMessage = MessageFormat.format("[{0}]项目任务[{1}]执行导入错误：{2}", apiProject.getProjectName(), projectTask.getTaskName(), importResult.getMessage());
                        log.error(errorMessage);
                        publishEvent(logBuilder, apiProject, createDate, errorMessage, false, null, null);
                        updateTaskExecDate(projectTask);
                        return SimpleResultUtils.createError(errorMessage);
                    }
                    updateTaskExecDate(projectTask);
                    long costTime = System.currentTimeMillis() - start;
                    log.info("import project task {}/{} cost {}ms", apiProject.getProjectName(), projectTask.getTaskName(), costTime);
                    ImportStatisticsVo stats = null;
                    if (importResult.getAddons() != null && importResult.getAddons().get("statistics") instanceof ImportStatisticsVo) {
                        stats = (ImportStatisticsVo) importResult.getAddons().get("statistics");
                        stats.setCostTime(costTime);
                    }
                    String successMessage = stats != null
                            ? MessageFormat.format("[{0}]项目任务[{1}]导入成功 (新增:{2}, 更新:{3}, 未变:{4}, 耗时:{5}ms)", apiProject.getProjectName(), projectTask.getTaskName(), stats.getDocAdded(), stats.getDocUpdated(), stats.getDocUnchanged(), costTime)
                            : getFormatMessage(projectTask, "[{0}]/[{1}]/[{2}]执行导入成功");
                    publishEvent(logBuilder, apiProject, createDate, successMessage, true, stats != null ? JsonUtils.toJson(stats) : null, null);
                    importResult.setMessage(successMessage);
                    return importResult;
                } else {
                    String msg = getFormatMessage(projectTask, "[{0}]/[{1}]/[{2}]Project信息为空");
                    publishEvent(logBuilder, null, createDate, msg, false, null, null);
                    updateTaskExecDate(projectTask);
                }
            } else {
                String msg = getFormatMessage(projectTask, "[{0}]/[{1}]/[{2}]sourceUrl不能为空");
                publishEvent(logBuilder, null, createDate, msg, false, null, null);
                updateTaskExecDate(projectTask);
            }
        } catch (Exception e) {
            String errorMessage = MessageFormat.format("[{0}]项目任务[{1}]执行异常：{2}", projectTask.getProjectId(), projectTask.getTaskName(), e.getMessage());
            log.error(errorMessage, e);
            publishEvent(logBuilder, apiProject, createDate, errorMessage, false, null, e);
            updateTaskExecDate(projectTask);
            return SimpleResultUtils.createError(errorMessage);
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
    }

    private void updateTaskExecDate(ApiProjectTask projectTask) {
        projectTask.setExecDate(new Date());
        apiProjectTaskService.updateById(projectTask);
    }

    private String getFormatMessage(ApiProjectTask projectTask, String message) {
        return MessageFormat.format(message, projectTask.getProjectId(), projectTask.getId(), projectTask.getTaskName());
    }

    protected void publishEvent(ApiLog.ApiLogBuilder logBuilder, ApiProject apiProject,
                                Date createDate, String message, boolean success, String logData, Throwable throwable) {
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
        if (StringUtils.isNotBlank(logData)) {
            logBuilder.logData(logData);
        }
        if (throwable != null) {
            logBuilder.exceptions(ExceptionUtils.getStackTrace(throwable));
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
