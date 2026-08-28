package com.fugary.simple.api.web.controllers.admin;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.service.apidoc.ApiProjectAccessService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import com.fugary.simple.api.service.apidoc.content.DocContentProvider;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoVo;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectImportVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectTaskImportVo;
import com.fugary.simple.api.web.vo.imports.DocSourceData;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.List;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@RestController
@RequestMapping("/admin/projects")
public class ApiProjectImportController {

    private static final Logger log = LoggerFactory.getLogger(ApiProjectImportController.class);
    @Qualifier("streamDocContentProviderImpl")
    @Autowired
    private DocContentProvider<InputStream> streamDocContentProvider;

    @Qualifier("urlDocContentProviderImpl")
    @Autowired
    private DocContentProvider<UrlWithAuthVo> urlDocContentProvider;

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiProjectAccessService apiProjectAccessService;

    @Autowired
    private ApiProjectTaskService apiProjectTaskService;

    @SneakyThrows
    @PostMapping("/parseProject")
    public SimpleResult<ExportApiProjectVo> parseProject(@ModelAttribute ApiProjectImportVo importVo, HttpServletRequest request){
        DocSourceData sourceData = null;
        String fileName = null;
        boolean isUrlMode = ApiDocConstants.IMPORT_TYPE_URL.equals(importVo.getImportType());
        if (ApiDocConstants.IMPORT_TYPE_FILE.equals(importVo.getImportType())) { // 文件模式
            List<MultipartFile> files = SimpleModelUtils.getUploadFiles(request);
            if (files.isEmpty()) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2002);
            }
            MultipartFile file = files.get(0);
            fileName = file.getOriginalFilename();
            importVo.setFileName(fileName);
            if (!SimpleModelUtils.isSupportedImportFile(fileName)) {
                String supportedExts = String.join(", ", ApiDocConstants.SUPPORTED_IMPORT_EXTENSIONS);
                String msg = SimpleResultUtils.getErrorMsg("simple.error.code.2003.unsupported",
                        new Object[]{fileName, supportedExts});
                return SimpleResultUtils.createError(SystemErrorConstants.CODE_2003, msg);
            }
            if (fileName != null && fileName.toLowerCase().endsWith(".zip")) {
                sourceData = DocSourceData.ofBinary(file.getBytes(), fileName, "application/zip");
            } else {
                SimpleResult<DocSourceData> contentResult = streamDocContentProvider.getContent(file.getInputStream());
                if (!contentResult.isSuccess()) {
                    return SimpleResultUtils.createError(contentResult.getCode(), contentResult.getMessage());
                }
                sourceData = contentResult.getResultData();
                if (sourceData != null) {
                    sourceData.setFileName(fileName);
                }
            }
        } else if (isUrlMode) {
            if (StringUtils.isBlank(importVo.getFileName()) && StringUtils.isNotBlank(importVo.getUrl())) {
                String urlPath = StringUtils.substringBefore(importVo.getUrl(), "?");
                int lastSlash = urlPath.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < urlPath.length() - 1) {
                    fileName = urlPath.substring(lastSlash + 1);
                    importVo.setFileName(fileName);
                }
            }
            SimpleResult<DocSourceData> contentResult = urlDocContentProvider.getContent(importVo);
            if (!contentResult.isSuccess()) {
                return SimpleResultUtils.createError(contentResult.getCode(), contentResult.getMessage());
            }
            sourceData = contentResult.getResultData();
        }
        SimpleResult<ExportApiProjectVo> parseResult = apiProjectService.processImportProject(sourceData, importVo);
        ExportApiProjectVo exportProjectVo = parseResult.getResultData();
        if (parseResult.isSuccess()) {
            ExportApiProjectInfoVo projectInfo = exportProjectVo.getProjectInfo();
            projectInfo.setFileName(fileName);
            projectInfo.setImportType(importVo.getImportType());
            projectInfo.setSourceType(importVo.getSourceType());
            projectInfo.setAuthType(importVo.getAuthType());
            projectInfo.setAuthContent(importVo.getAuthContent());
            if (isUrlMode) {
                projectInfo.setUrl(importVo.getUrl());
            }
        }
        return parseResult;
    }

    @SneakyThrows
    @PostMapping("/importProject")
    public SimpleResult<ApiProject> importProject(@ModelAttribute ApiProjectImportVo importVo, HttpServletRequest request){
        if (!apiProjectAccessService.canAccessImportGroup(importVo.getGroupCode(), ApiGroupAuthority.WRITABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        SimpleResult<ExportApiProjectVo> parseResult = parseProject(importVo, request);
        if (parseResult.isSuccess()) {
            return apiProjectTaskService.saveUrlImportAsTask(importVo, apiProjectService.importNewProject(parseResult.getResultData(), importVo));
        }
        return SimpleResult.<ApiProject>builder()
                .code(parseResult.getCode())
                .message(parseResult.getMessage()).build();
    }

    @SneakyThrows
    @PostMapping("/importExistsProject")
    public SimpleResult<ApiProject> importExistsProject(@ModelAttribute ApiProjectTaskImportVo importVo, HttpServletRequest request){
        ApiProject apiProject = apiProjectService.getById(importVo.getProjectId());
        if (apiProject == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!apiProjectAccessService.canAccessProject(apiProject, ApiGroupAuthority.WRITABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        SimpleResult<ExportApiProjectVo> parseResult = parseProject(importVo, request);
        if (parseResult.isSuccess()) {
            return apiProjectService.importUpdateProject(apiProject, parseResult.getResultData(), importVo);
        }
        return SimpleResult.<ApiProject>builder()
                .code(parseResult.getCode())
                .message(parseResult.getMessage()).build();
    }
}
