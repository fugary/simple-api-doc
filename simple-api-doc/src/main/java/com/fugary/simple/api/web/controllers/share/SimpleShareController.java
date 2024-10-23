package com.fugary.simple.api.web.controllers.share;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.exports.ApiDocExporter;
import com.fugary.simple.api.push.ApiInvokeProcessor;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.service.token.TokenService;
import com.fugary.simple.api.utils.SchemaJsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.SchemaYamlUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportDownloadVo;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectShareVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Slf4j
@RestController
@RequestMapping("/shares")
public class SimpleShareController {

    @Value("${spring.application.name}")
    private String applicationName;

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiProjectShareService apiProjectShareService;

    @Autowired
    private ApiProjectInfoService apiProjectInfoService;

    @Autowired
    private ApiProjectInfoDetailService apiProjectInfoDetailService;

    @Autowired
    private ApiDocService apiDocService;

    @Autowired
    private ApiDocSchemaService apiDocSchemaService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ApiInvokeProcessor apiInvokeProcessor;

    @Autowired
    private ApiDocExporter<OpenAPI> apiApiDocExporter;

    @GetMapping("/loadShare/{shareId}")
    public SimpleResult<ApiProjectShareVo> loadShare(@PathVariable("shareId") String shareId, @RequestParam(name = "pwd") String password) {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        if (apiShare == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProjectShareVo shareVo = SimpleModelUtils.toShareVo(apiShare);
        if (shareVo.isExpired()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2008);
        }
        if (shareVo.isNeedPassword()) {
            SimpleResult<ApiUser> validateResult = apiProjectShareService.validateSharePwd(password, apiShare);
            if (!validateResult.isSuccess()) {
                SimpleResult<ApiProjectShareVo> result = SimpleResultUtils.createSimpleResult(validateResult);
                result.setResultData(shareVo);
                return result;
            }
        }
        ApiUser apiUser = new ApiUser();
        apiUser.setUserName(SecurityUtils.getShareUserName(apiShare));
        shareVo.setShareToken(tokenService.createToken(apiUser));
        return SimpleResultUtils.createSimpleResult(shareVo);
    }

    @GetMapping("/loadProject/{shareId}")
    public SimpleResult<ApiProjectDetailVo> loadProject(@PathVariable("shareId") String shareId) {
        if (!StringUtils.equals(shareId, SecurityUtils.getLoginShareId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_401);
        }
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        ApiProject project;
        if (apiShare == null || (project = apiProjectService.getById(apiShare.getProjectId())) == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectService.loadProjectVo(ProjectDetailQueryVo.builder()
                .projectCode(project.getProjectCode())
                .forceEnabled(true)
                .includeDocs(true).build()));
    }

    @GetMapping("/checkDownloadDocs/{shareId}")
    public SimpleResult<Boolean> checkDownloadDocs(@PathVariable("shareId") String shareId) {
        if (!StringUtils.equals(shareId, SecurityUtils.getLoginShareId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_401);
        }
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        List<ApiProjectInfo> infoList;
        ApiProjectInfo currentInfo;
        List<ApiProjectInfoDetail> details;
        ApiProjectInfoDetail detail;
        if (apiShare == null
                || (infoList = apiProjectInfoService.loadByProjectId(apiShare.getProjectId())).isEmpty()
                || (currentInfo = infoList.get(0)) == null
                || (details = apiProjectInfoDetailService.loadByProjectAndInfo(apiShare.getProjectId(), currentInfo.getId(),
                Set.of(ApiDocConstants.PROJECT_SCHEMA_TYPE_CONTENT))).isEmpty()
                || (detail = details.get(0)) == null
                || StringUtils.isBlank(detail.getSchemaContent())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        return SimpleResultUtils.createSimpleResult(true);
    }

    @GetMapping("/download/{type}/{shareId}")
    public ResponseEntity<byte[]> downloadDocs(@PathVariable("type") String type,
                                               @PathVariable("shareId") String shareId,
                                               HttpServletResponse response) throws IOException {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        ApiProjectInfo currentInfo = apiProjectInfoService.loadByProjectId(apiShare.getProjectId()).get(0);
        ApiProjectInfoDetail detail = apiProjectInfoDetailService.loadByProjectAndInfo(apiShare.getProjectId(), currentInfo.getId(),
                Set.of(ApiDocConstants.PROJECT_SCHEMA_TYPE_CONTENT)).get(0);
        String fileName = apiShare.getShareName() + "." + type;
        byte[] contentBytes = getContent(type, detail.getSchemaContent()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(contentBytes.length)
                .body(contentBytes);
    }

    @PostMapping("/checkExportDownloadDocs")
    public SimpleResult<String> checkExportDownloadDocs(@RequestBody ExportDownloadVo downloadVo) {
        String shareId = downloadVo.getShareId();
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        if (apiShare == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!StringUtils.equals(shareId, SecurityUtils.getLoginShareId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_401);
        }
        String uuid = SimpleModelUtils.uuid();
        String type = StringUtils.defaultIfBlank(downloadVo.getType(), "json");
        OpenAPI openAPI = apiApiDocExporter.export(apiShare.getProjectId(), downloadVo.getDocIds());
        String content;
        if (StringUtils.equals(type, "json")) {
            content = SchemaJsonUtils.toJson(openAPI);
        } else {
            content = SchemaYamlUtils.toYaml(openAPI);
        }
        try {
            String filePathName = getFileFullPath(uuid, type);
            Path tempFile = Files.createFile(Path.of(filePathName));
            Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8));
            tempFile.toFile().deleteOnExit();
        } catch (IOException e) {
            log.error("创建临时文件失败", e);
        }
        return SimpleResultUtils.createSimpleResult(uuid);
    }

    /**
     * 获取文件路径
     *
     * @param uuid
     * @param type
     * @return
     */
    private String getFileFullPath(String uuid, String type) {
        String fileName = uuid + "." + type;
        String filePath = StringUtils.join(List.of(FileUtils.getTempDirectoryPath(), applicationName), File.separator);
        File fileDir = new File(filePath);
        if (!fileDir.exists()) {
            try {
                FileUtils.forceMkdir(fileDir);
            } catch (IOException e) {
                log.error("创建临时文件夹失败", e);
            }
        }
        return StringUtils.join(List.of(filePath, fileName), File.separator);
    }

    @GetMapping("/exportDownload/{type}/{shareId}/{uuid}")
    public ResponseEntity<InputStreamResource> exportDownloadDocs(@PathVariable("type") String type,
                                                                  @PathVariable("shareId") String shareId,
                                                                  @PathVariable("uuid") String uuid) throws IOException {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        String fileName = uuid + "." + type;
        // 构造临时文件的完整路径
        File tempFile = new File(getFileFullPath(uuid, type));
        fileName = apiShare.getShareName() + "-" + fileName;
        // 检查文件是否存在
        if (!tempFile.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        // 创建 InputStreamResource 从文件中读取数据
        InputStreamResource resource = new InputStreamResource(new FileInputStream(tempFile));
        // 设置响应头，准备文件下载
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(tempFile.length())
                .body(resource);
    }

    protected String getContent(String type, String content) {
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readContents(content, null, parseOptions);
        OpenAPI openAPI = result.getOpenAPI();
        if (StringUtils.equals(type, "json")) {
            return SchemaJsonUtils.toJson(openAPI);
        } else {
            return SchemaYamlUtils.toYaml(openAPI);
        }
    }

    @GetMapping("/loadShareDoc/{shareId}/{docId}")
    public SimpleResult<ApiDocDetailVo> loadShareDoc(@PathVariable("shareId") String shareId, @PathVariable("docId") Integer docId) {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        ApiDoc apiDoc = apiDocService.getById(docId);
        if (apiShare == null || apiDoc == null || !apiShare.getProjectId().equals(apiDoc.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProjectInfo apiInfo = apiProjectInfoService.getById(apiDoc.getInfoId());
        ApiDocDetailVo apiDocVo = apiDocSchemaService.loadDetailVo(apiDoc);
        if (apiInfo == null || !apiDocVo.getProjectId().equals(apiInfo.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProject apiProject = apiProjectService.getById(apiDocVo.getProjectId());
        ApiProjectInfoDetailVo apiInfoDetailVo = apiProjectInfoDetailService.parseInfoDetailVo(apiInfo, apiDocVo);
        apiInfoDetailVo.setProjectCode(apiProject.getProjectCode());
        apiDocVo.setProjectInfoDetail(apiInfoDetailVo);
        apiDocVo.setApiShare(SimpleModelUtils.toShareVo(apiShare));
        return SimpleResultUtils.createSimpleResult(apiDocVo);
    }

    /**
     * 调试API
     *
     * @return
     */
    @RequestMapping("/proxy/**")
    public ResponseEntity<?> proxyApi(HttpServletRequest request, HttpServletResponse response) {
        return apiInvokeProcessor.invoke(request, response);
    }
}
